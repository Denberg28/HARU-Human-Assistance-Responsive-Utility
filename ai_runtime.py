from __future__ import annotations

from dataclasses import dataclass
import json
import re
import time
from ai_usage import cooldown_seconds, gemini_request, needs_agent

MAX_JSON_RESPONSE_BYTES = 4_000_000
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import Request, urlopen, build_opener, HTTPRedirectHandler


@dataclass
class AiConfig:
    mode: str = "Off"
    provider: str = "Disabled"
    model: str = ""
    endpoint: str = ""
    api_key: str = ""
    timeout_s: int = 25


class AiRuntimeError(RuntimeError):
    def __init__(
        self,
        message: str,
        *,
        status_code: int | None = None,
        kind: str = "provider_error",
        retryable: bool = False,
        cooldown_s: float = 60.0,
    ):
        super().__init__(message)
        self.status_code = status_code
        self.kind = kind
        self.retryable = retryable
        self.cooldown_s = cooldown_s


class _NoGeminiRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise AiRuntimeError("AI provider redirected the request; credentials were not forwarded.")


def _json_request(url: str, *, payload=None, headers=None, timeout=25, method=None):
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    request_headers = {"Content-Type": "application/json"}
    request_headers.update(headers or {})
    request = Request(url, data=body, headers=request_headers, method=method or ("POST" if body is not None else "GET"))

    try:
        opener = build_opener(_NoGeminiRedirect()).open if url.startswith("https://generativelanguage.googleapis.com/") else urlopen
        with opener(request, timeout=timeout) as response:
            raw_bytes = response.read(MAX_JSON_RESPONSE_BYTES + 1)
            if len(raw_bytes) > MAX_JSON_RESPONSE_BYTES:
                raise AiRuntimeError(
                    "Provider response was unexpectedly large.",
                    kind="response_too_large",
                )
            raw = raw_bytes.decode("utf-8")
            return json.loads(raw) if raw else {}
    except HTTPError as exc:
        try:
            raw_detail = exc.read(MAX_JSON_RESPONSE_BYTES + 1).decode("utf-8", errors="replace")
            parsed = json.loads(raw_detail) if raw_detail else {}
        except Exception:
            parsed = {}

        if exc.code == 429:
            raise AiRuntimeError(
                "Provider quota or rate limit reached. HARU will pause requests; check project limits in Google AI Studio.",
                status_code=429,
                kind="quota",
                retryable=False,
                cooldown_s=cooldown_seconds(parsed.get("error", {}) if isinstance(parsed, dict) and isinstance(parsed.get("error"), dict) else {}, exc.headers.get("Retry-After") if exc.headers else None),
            ) from None
        if exc.code in {401, 403}:
            raise AiRuntimeError(
                "Authentication was rejected. Check the API key and provider access.",
                status_code=exc.code,
                kind="auth",
            ) from None
        if exc.code == 404:
            raise AiRuntimeError(
                "The selected model or API endpoint was not found.",
                status_code=404,
                kind="model_not_found",
            ) from None
        if 500 <= exc.code <= 599:
            raise AiRuntimeError(
                "The AI provider is temporarily unavailable. Try again shortly.",
                status_code=exc.code,
                kind="provider_unavailable",
                retryable=True,
            ) from None

        message = f"Provider request failed with HTTP {exc.code}. Check the model and request configuration."
        if len(message) > 220:
            message = message[:217] + "..."
        raise AiRuntimeError(
            message,
            status_code=exc.code,
            kind="provider_error",
        ) from None
    except URLError as exc:
        raise AiRuntimeError(
            "Could not reach provider. Check your internet connection.",
            kind="network",
            retryable=True,
        ) from None
    except TimeoutError:
        raise AiRuntimeError(
            "Provider connection timed out.",
            kind="timeout",
            retryable=True,
        ) from None
    except json.JSONDecodeError:
        raise AiRuntimeError("Provider returned an unreadable response.") from None


def _normalize_endpoint(endpoint: str, default: str) -> str:
    value = (endpoint or default).strip()
    return value[:-1] if value.endswith("/") else value


def list_ollama_models(endpoint: str, timeout_s: int = 5) -> list[str]:
    base = _normalize_endpoint(endpoint, "http://localhost:11434")
    data = _json_request(f"{base}/api/tags", timeout=timeout_s)
    names = []
    for item in data.get("models", []):
        name = item.get("name") or item.get("model")
        if name:
            names.append(name)
    return names


def pull_ollama_model(
    endpoint: str,
    model: str,
    timeout_s: int = 1800,
) -> str:
    """Download an Ollama model through the local API without invoking a shell."""
    name = (model or "").strip()
    if not name:
        raise AiRuntimeError("Choose a model to download first.")
    if len(name) > 120:
        raise AiRuntimeError("Model name is too long.")
    if not all(ch.isalnum() or ch in "._:/-" for ch in name):
        raise AiRuntimeError("Model name contains unsupported characters.")

    base = _normalize_endpoint(endpoint, "http://localhost:11434")
    data = _json_request(
        f"{base}/api/pull",
        payload={"name": name, "stream": False},
        timeout=timeout_s,
    )
    status = str(data.get("status") or "").strip()
    if status and status.lower() not in {"success", "done"}:
        raise AiRuntimeError(f"Ollama download did not complete: {status}")
    return status or "success"


def list_openai_compatible_models(
    endpoint: str,
    api_key: str = "",
    timeout_s: int = 5,
) -> list[str]:
    base = _normalize_endpoint(endpoint, "http://localhost:1234")
    headers = {}
    if api_key:
        headers["Authorization"] = f"Bearer {api_key}"
    data = _json_request(f"{base}/v1/models", headers=headers, timeout=timeout_s)
    names = []
    for item in data.get("data", []):
        name = item.get("id")
        if name:
            names.append(name)
    return names


def _gemini_grounding_sources(data: dict, limit: int = 5) -> list[tuple[str, str]]:
    try:
        metadata = data["candidates"][0].get("groundingMetadata", {})
    except (KeyError, IndexError, TypeError):
        return []

    sources = []
    seen = set()
    for chunk in metadata.get("groundingChunks", []):
        web = chunk.get("web") or {}
        uri = (web.get("uri") or "").strip()
        title = (web.get("title") or "Web source").strip()
        if not uri or uri in seen:
            continue
        seen.add(uri)
        sources.append((title, uri))
        if len(sources) >= limit:
            break
    return sources


def _interaction_output_text(data: dict) -> str:
    """Extract final text from a raw Gemini Interactions API response."""
    direct = str(data.get("output_text") or "").strip()
    if direct:
        return direct

    parts = []
    for step in reversed(data.get("steps", [])):
        if not isinstance(step, dict) or step.get("type") != "model_output":
            continue
        for item in step.get("content", []):
            if (
                isinstance(item, dict)
                and item.get("type") == "text"
                and item.get("text")
            ):
                parts.append(str(item["text"]))
        if parts:
            break
    return "\n".join(parts).strip()


def _ask_ai(
    config: AiConfig,
    prompt: str,
    system_prompt: str = "",
    enable_native_tools: bool = False,
) -> str:
    provider = config.provider
    model = config.model.strip()
    if len(prompt) > 16_000 or len(system_prompt) > 4_000:
        raise AiRuntimeError("Prompt or instructions are too large.")
    if provider == "Google Gemini API" and model == "antigravity-preview-09-2026" and prompt.strip() != "Reply with exactly: HARU OK" and not needs_agent(prompt):
        model = "gemini-3.5-flash-lite"

    if config.mode == "Off" or provider == "Disabled":
        raise AiRuntimeError("AI runtime is disabled.")
    if not model and provider != "Android on-device (APK only)":
        raise AiRuntimeError("Select or enter a model first.")

    if provider == "Ollama":
        base = _normalize_endpoint(config.endpoint, "http://localhost:11434")
        messages = []
        if system_prompt:
            messages.append({"role": "system", "content": system_prompt})
        messages.append({"role": "user", "content": prompt})
        data = _json_request(
            f"{base}/api/chat",
            payload={"model": model, "messages": messages, "stream": False},
            timeout=config.timeout_s,
        )
        text = ((data.get("message") or {}).get("content") or "").strip()
        if not text:
            raise AiRuntimeError("Ollama returned no text.")
        return text

    if provider in {"Local OpenAI-compatible", "Cloud OpenAI-compatible", "OpenRouter API"}:
        default_base = "https://openrouter.ai/api" if provider == "OpenRouter API" else "http://localhost:1234"
        base = _normalize_endpoint(config.endpoint, default_base)
        headers = {}
        if config.api_key:
            headers["Authorization"] = f"Bearer {config.api_key}"
        if provider == "OpenRouter API":
            headers["HTTP-Referer"] = "https://github.com/Denberg28/HARU-Human-Assistance-Responsive-Utility"
            headers["X-Title"] = "HARU"
        messages = []
        if system_prompt:
            messages.append({"role": "system", "content": system_prompt})
        messages.append({"role": "user", "content": prompt})
        data = _json_request(
            f"{base}/v1/chat/completions",
            payload={"model": model, "messages": messages, "temperature": 0.3},
            headers=headers,
            timeout=config.timeout_s,
        )
        try:
            return data["choices"][0]["message"]["content"].strip()
        except (KeyError, IndexError, TypeError, AttributeError):
            raise AiRuntimeError("OpenAI-compatible endpoint returned no chat text.") from None

    if provider == "OpenAI API":
        if not config.api_key:
            raise AiRuntimeError("OpenAI API key is required.")
        payload = {
            "model": model,
            "input": prompt if not system_prompt else f"{system_prompt}\n\nUser: {prompt}",
        }
        if enable_native_tools:
            payload["tools"] = [{"type": "web_search"}]

        data = _json_request(
            "https://api.openai.com/v1/responses",
            payload=payload,
            headers={"Authorization": f"Bearer {config.api_key}"},
            timeout=config.timeout_s,
        )
        parts = []
        sources = []
        seen = set()
        for output in data.get("output", []):
            if output.get("type") != "message":
                continue
            for item in output.get("content", []):
                if item.get("type") == "output_text" and item.get("text"):
                    parts.append(item["text"])
                    for annotation in item.get("annotations", []):
                        if annotation.get("type") != "url_citation":
                            continue
                        url = (annotation.get("url") or "").strip()
                        title = (annotation.get("title") or "Web source").strip()
                        if url and url not in seen:
                            seen.add(url)
                            sources.append((title, url))
        text = "\n".join(parts).strip()
        if not text:
            raise AiRuntimeError("OpenAI returned no text.")
        if sources:
            source_lines = "\n".join(f"- {title}: {url}" for title, url in sources[:5])
            text = f"{text}\n\nLive sources:\n{source_lines}"
        return text

    if provider == "Google Gemini API":
        if not config.api_key:
            raise AiRuntimeError("Gemini API key is required.")

        # Antigravity is a managed agent and must use the Interactions API,
        # not the standard generateContent model endpoint.
        if model == "antigravity-preview-09-2026":
            if len(prompt) > 6_000 or len(prompt) + len(system_prompt) > 6_500:
                raise AiRuntimeError("Agent task is too large. Shorten it or select Gemini.")
            interaction_input = (
                prompt
                if not system_prompt
                else f"{system_prompt}\n\nUser: {prompt}"
            )
            token_budget = (
                512
                if prompt.strip() == "Reply with exactly: HARU OK"
                else 4096
            )
            payload = {
                "agent": model,
                "input": interaction_input,
                "environment": "remote",
                "background": True,
                "agent_config": {
                    "type": "antigravity",
                    "model": "gemini-3.5-flash-lite",
                    "max_total_tokens": token_budget,
                },
            }
            payload["tools"] = (
                [
                    {"type": "google_search"},
                    {"type": "url_context"},
                ]
                if enable_native_tools
                else []
            )

            base = "https://generativelanguage.googleapis.com/v1beta/interactions"
            headers = {"x-goog-api-key": config.api_key}
            interaction_id = None
            terminal = False
            deadline = time.monotonic() + 120
            try:
                data = _json_request(base, payload=payload, headers=headers, timeout=20)
                candidate_id = str(data.get("id", ""))
                if re.fullmatch(r"[A-Za-z0-9_-]{1,512}", candidate_id):
                    interaction_id = candidate_id
                poll_delay = 2
                polls = 0
                while data.get("status") == "in_progress":
                    if not interaction_id or polls >= 15 or time.monotonic() + poll_delay >= deadline:
                        raise AiRuntimeError("Agent time or polling limit reached. HARU requested cancellation.")
                    time.sleep(poll_delay)
                    remaining = deadline - time.monotonic()
                    if remaining <= 0:
                        raise AiRuntimeError("Agent time limit reached. HARU requested cancellation.")
                    data = _json_request(f"{base}/{interaction_id}?include_input=false", headers=headers, timeout=min(20, remaining))
                    polls += 1
                    poll_delay = min(10, poll_delay * 2)
                status = str(data.get("status", ""))
                terminal = status in {"completed", "failed", "cancelled", "incomplete"}
                if status == "incomplete":
                    raise AiRuntimeError("Agent reached HARU's token budget. Narrow the task; no automatic continuation was sent.")
                if status != "completed":
                    error = data.get("error", {})
                    if isinstance(error, dict) and str(error.get("code", error.get("status", ""))).upper() in {"8", "429", "RESOURCE_EXHAUSTED", "RATE_LIMIT_EXCEEDED", "QUOTA_EXCEEDED"}:
                        raise AiRuntimeError("Agent quota reached.", status_code=429, kind="quota", cooldown_s=cooldown_seconds(error))
                    raise AiRuntimeError("Agent did not complete. Check quota and agent access in Google AI Studio.")
                text = _interaction_output_text(data)
                if not text:
                    raise AiRuntimeError("Antigravity returned no final text.")
                return text
            finally:
                if interaction_id and not terminal:
                    try:
                        _json_request(f"{base}/{interaction_id}/cancel", payload={}, headers=headers, timeout=5)
                    except Exception:
                        pass  # Best effort; never recreate or automatically continue the task.


        model_path = model if model.startswith("models/") else f"models/{model}"
        payload = {"contents": [{"parts": [{"text": prompt}]}],
                   "generationConfig": {"maxOutputTokens": 256 if prompt.strip() == "Reply with exactly: HARU OK" else 1200}}
        thinking_level = {
            "gemini-3.7-flash": "low", "gemini-3.8-flash": "low",
            "gemini-3.1-flash-lite": "minimal", "gemini-3.5-flash-lite": "minimal",
            "gemini-3.5-flash": "minimal", "gemini-3.6-flash": "minimal",
        }.get(model)
        if thinking_level:
            payload["generationConfig"]["thinkingConfig"] = {"thinkingLevel": thinking_level}
        if system_prompt:
            payload["systemInstruction"] = {"parts": [{"text": system_prompt}]}

        # Gemini models can use Google Search grounding. Gemma is kept as a
        # high-volume text model without unsupported native-tool requests.
        use_grounding = enable_native_tools and model.startswith("gemini-")
        if use_grounding:
            payload["tools"] = [{"google_search": {}}]

        data = _json_request(
            f"https://generativelanguage.googleapis.com/v1beta/{quote(model_path, safe='/')}:generateContent",
            payload=payload,
            headers={"x-goog-api-key": config.api_key},
            timeout=config.timeout_s,
        )
        try:
            parts = data["candidates"][0]["content"]["parts"]
            text = "\n".join(part.get("text", "") for part in parts if not part.get("thought", False)).strip()
        except (KeyError, IndexError, TypeError):
            text = ""

        finish = (data.get("candidates") or [{}])[0].get("finishReason", "")
        if not text:
            if finish == "MAX_TOKENS":
                raise AiRuntimeError("Gemini reached HARU's response budget before answering. Shorten the question; no automatic retry was sent.")
            raise AiRuntimeError("Gemini returned no final text. Check model access and provider restrictions.")
        if finish == "MAX_TOKENS":
            text += "\n\nAnswer stopped at HARU's response budget."

        if use_grounding:
            sources = _gemini_grounding_sources(data)
            if sources:
                source_lines = "\n".join(
                    f"- {title}: {uri}" for title, uri in sources
                )
                text = f"{text}\n\nLive sources:\n{source_lines}"

        return text

    if provider == "Anthropic Claude API":
        if not config.api_key:
            raise AiRuntimeError("Anthropic API key is required.")
        payload = {
            "model": model,
            "max_tokens": 1200,
            "messages": [{"role": "user", "content": prompt}],
        }
        if system_prompt:
            payload["system"] = system_prompt
        if enable_native_tools:
            payload["tools"] = [
                {
                    "type": "web_search_20260318",
                    "name": "web_search",
                    "max_uses": 5,
                }
            ]

        data = _json_request(
            "https://api.anthropic.com/v1/messages",
            payload=payload,
            headers={
                "x-api-key": config.api_key,
                "anthropic-version": "2023-06-01",
            },
            timeout=config.timeout_s,
        )

        text_parts = []
        sources = []
        seen = set()
        for item in data.get("content", []):
            if item.get("type") != "text":
                continue
            if item.get("text"):
                text_parts.append(item["text"])
            for citation in item.get("citations", []):
                url = (citation.get("url") or "").strip()
                title = (citation.get("title") or "Web source").strip()
                if url and url not in seen:
                    seen.add(url)
                    sources.append((title, url))

        text = "\n".join(text_parts).strip()
        if not text:
            raise AiRuntimeError("Claude returned no text.")
        if sources:
            source_lines = "\n".join(f"- {title}: {url}" for title, url in sources[:5])
            text = f"{text}\n\nLive sources:\n{source_lines}"
        return text

    if provider == "Android on-device (APK only)":
        raise AiRuntimeError(
            "This runtime is reserved for the native Android build. "
            "The Streamlit simulator cannot execute a model installed inside your phone."
        )

    raise AiRuntimeError(f"Unsupported provider: {provider}")


def ask_ai(
    config: AiConfig,
    prompt: str,
    system_prompt: str = "",
    enable_native_tools: bool = False,
) -> str:
    if config.provider == "Google Gemini API" and config.mode != "Off" and config.api_key:
        try:
            with gemini_request(config.api_key):
                return _ask_ai(config, prompt, system_prompt, enable_native_tools)
        except AiRuntimeError:
            raise
        except RuntimeError as exc:
            raise AiRuntimeError(str(exc), kind="request_gate") from None
    return _ask_ai(config, prompt, system_prompt, enable_native_tools)


def test_ai(config: AiConfig) -> str:
    return ask_ai(
        config,
        "Reply with exactly: HARU OK",
        "You are HARU's AI runtime connection test. Keep the response extremely short.",
    )
