from __future__ import annotations

from dataclasses import dataclass
import json
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import Request, urlopen


@dataclass
class AiConfig:
    mode: str = "Off"
    provider: str = "Disabled"
    model: str = ""
    endpoint: str = ""
    api_key: str = ""
    timeout_s: int = 25


class AiRuntimeError(RuntimeError):
    pass


def _json_request(url: str, *, payload=None, headers=None, timeout=25, method=None):
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    request_headers = {"Content-Type": "application/json"}
    request_headers.update(headers or {})
    request = Request(url, data=body, headers=request_headers, method=method or ("POST" if body is not None else "GET"))

    try:
        with urlopen(request, timeout=timeout) as response:
            raw = response.read().decode("utf-8")
            return json.loads(raw) if raw else {}
    except HTTPError as exc:
        try:
            detail = exc.read().decode("utf-8")[:500]
        except Exception:
            detail = ""
        raise AiRuntimeError(f"Provider returned HTTP {exc.code}. {detail}".strip()) from None
    except URLError as exc:
        raise AiRuntimeError(f"Could not reach provider: {exc.reason}") from None
    except TimeoutError:
        raise AiRuntimeError("Provider connection timed out.") from None
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


def ask_ai(
    config: AiConfig,
    prompt: str,
    system_prompt: str = "",
    enable_live_search: bool = False,
) -> str:
    provider = config.provider
    model = config.model.strip()

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

    if provider in {"Local OpenAI-compatible", "Cloud OpenAI-compatible"}:
        base = _normalize_endpoint(config.endpoint, "http://localhost:1234")
        headers = {}
        if config.api_key:
            headers["Authorization"] = f"Bearer {config.api_key}"
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
        data = _json_request(
            "https://api.openai.com/v1/responses",
            payload={
                "model": model,
                "input": prompt if not system_prompt else f"{system_prompt}\n\nUser: {prompt}",
            },
            headers={"Authorization": f"Bearer {config.api_key}"},
            timeout=config.timeout_s,
        )
        parts = []
        for output in data.get("output", []):
            if output.get("type") != "message":
                continue
            for item in output.get("content", []):
                if item.get("type") == "output_text" and item.get("text"):
                    parts.append(item["text"])
        text = "\n".join(parts).strip()
        if not text:
            raise AiRuntimeError("OpenAI returned no text.")
        return text

    if provider == "Google Gemini API":
        if not config.api_key:
            raise AiRuntimeError("Gemini API key is required.")
        model_path = model if model.startswith("models/") else f"models/{model}"
        payload = {"contents": [{"parts": [{"text": prompt}]}]}
        if system_prompt:
            payload["systemInstruction"] = {"parts": [{"text": system_prompt}]}
        if enable_live_search:
            payload["tools"] = [{"google_search": {}}]

        data = _json_request(
            f"https://generativelanguage.googleapis.com/v1beta/{quote(model_path, safe='/')}:generateContent",
            payload=payload,
            headers={"x-goog-api-key": config.api_key},
            timeout=config.timeout_s,
        )
        try:
            parts = data["candidates"][0]["content"]["parts"]
            text = "\n".join(part.get("text", "") for part in parts).strip()
        except (KeyError, IndexError, TypeError):
            text = ""

        if not text:
            raise AiRuntimeError("Gemini returned no text.")

        if enable_live_search:
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
            "max_tokens": 700,
            "messages": [{"role": "user", "content": prompt}],
        }
        if system_prompt:
            payload["system"] = system_prompt
        data = _json_request(
            "https://api.anthropic.com/v1/messages",
            payload=payload,
            headers={
                "x-api-key": config.api_key,
                "anthropic-version": "2023-06-01",
            },
            timeout=config.timeout_s,
        )
        text = "\n".join(
            item.get("text", "")
            for item in data.get("content", [])
            if item.get("type") == "text"
        ).strip()
        if not text:
            raise AiRuntimeError("Claude returned no text.")
        return text

    if provider == "Android on-device (APK only)":
        raise AiRuntimeError(
            "This runtime is reserved for the native Android build. "
            "The Streamlit simulator cannot execute a model installed inside your phone."
        )

    raise AiRuntimeError(f"Unsupported provider: {provider}")


def test_ai(config: AiConfig) -> str:
    return ask_ai(
        config,
        "Reply with exactly: HARU OK",
        "You are HARU's AI runtime connection test. Keep the response extremely short.",
    )
