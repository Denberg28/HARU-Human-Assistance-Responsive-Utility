from datetime import datetime
from pathlib import Path
import ast
import base64
import hashlib
import importlib.util
import html
import operator
import os
import re

import streamlit as st

from credential_store import (
    delete_key as delete_stored_key,
    get_key as get_stored_key,
    save_key as save_stored_key,
)
from ai_runtime import (
    AiConfig,
    AiRuntimeError,
    ask_ai,
    list_ollama_models,
    list_openai_compatible_models,
    test_ai,
)
from news_service import (
    deduplicate,
    friendly_time,
    infer_interests,
    local_region_headlines,
    philippines_headlines,
    topic_headlines,
    top_interests,
    world_headlines,
)

st.set_page_config(
    page_title="HARU",
    page_icon="🙂",
    layout="centered",
    initial_sidebar_state="collapsed",
)

ROOT_DIR = Path(__file__).resolve().parent
ASSET_DIR = ROOT_DIR / "assets"
HARU_LINEART = ASSET_DIR / "haru_lineart.svg"
HARU_THEME = ASSET_DIR / "haru_cute_theme.wav"


def ensure_haru_media() -> None:
    """Create HARU media on first run when deployed assets are missing."""
    ASSET_DIR.mkdir(parents=True, exist_ok=True)

    need_theme = not HARU_THEME.exists()
    if not need_theme:
        return

    generator_path = ROOT_DIR / "scripts" / "generate_haru_media.py"
    if not generator_path.exists():
        return

    try:
        spec = importlib.util.spec_from_file_location("haru_media_generator", generator_path)
        if spec is None or spec.loader is None:
            return

        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)

        if need_theme and hasattr(module, "make_theme"):
            module.make_theme()
    except Exception:
        # Keep HARU usable even if optional media generation fails.
        return


ensure_haru_media()


SECRET_NAME_BY_PROVIDER = {
    "Google Gemini API": "GEMINI_API_KEY",
    "OpenRouter API": "OPENROUTER_API_KEY",
}


def server_secret_for_provider(provider: str) -> str:
    name = SECRET_NAME_BY_PROVIDER.get(provider, "")
    if not name:
        return ""

    env_value = os.environ.get(name, "").strip()
    if env_value:
        return env_value

    try:
        value = str(st.secrets.get(name, "")).strip()
    except Exception:
        value = ""
    return value


def local_stored_key_for_provider(provider: str) -> str:
    if is_cloud_haru():
        return ""
    return get_stored_key(provider)


def resolved_api_key(provider: str, session_key: str = "") -> str:
    return (
        server_secret_for_provider(provider)
        or local_stored_key_for_provider(provider)
        or (session_key or "").strip()
    )


def api_key_source(provider: str) -> str:
    if server_secret_for_provider(provider):
        return "server secret"
    if local_stored_key_for_provider(provider):
        return "OS credential store"
    return "session only"


DEFAULTS = {
    "mood": "IDLE",
    "message": "Hello. I'm HARU.",
    "history": [],
    "news_region": "Philippines",
    "news_last_seen": 0.0,
    "news_refresh_nonce": 0,
    "explicit_interests": {},
    "local_notes": [],
    "local_tasks": [],
    "music_enabled": False,
    "ai_mode": "Off",
    "ai_provider": "Disabled",
    "ai_model": "",
    "ai_endpoint": "",
    "ai_api_key": "",
    "ai_status": "Not tested",
    "ai_connection_state": "OFF",
    "ai_connection_message": "AI runtime is disabled.",
    "ai_runtime_degraded": False,
    "ai_runtime_degraded_reason": "",
    "ai_applied_signature": "",
    "ai_applied_mode": "Off",
    "ai_applied_provider": "Disabled",
    "ai_applied_model": "",
    "ai_applied_endpoint": "",
    "ai_applied_api_key": "",
}
for key, value in DEFAULTS.items():
    if key not in st.session_state:
        st.session_state[key] = value.copy() if isinstance(value, (list, dict)) else value


def _safe_calc(expression: str) -> float:
    allowed_binary = {
        ast.Add: operator.add,
        ast.Sub: operator.sub,
        ast.Mult: operator.mul,
        ast.Div: operator.truediv,
        ast.Mod: operator.mod,
        ast.Pow: operator.pow,
    }
    allowed_unary = {
        ast.UAdd: operator.pos,
        ast.USub: operator.neg,
    }

    def evaluate(node):
        if isinstance(node, ast.Expression):
            return evaluate(node.body)
        if isinstance(node, ast.Constant) and isinstance(node.value, (int, float)):
            return float(node.value)
        if isinstance(node, ast.BinOp) and type(node.op) in allowed_binary:
            left = evaluate(node.left)
            right = evaluate(node.right)
            if isinstance(node.op, ast.Pow) and abs(right) > 12:
                raise ValueError("Exponent too large")
            return allowed_binary[type(node.op)](left, right)
        if isinstance(node, ast.UnaryOp) and type(node.op) in allowed_unary:
            return allowed_unary[type(node.op)](evaluate(node.operand))
        raise ValueError("Unsupported expression")

    tree = ast.parse(expression, mode="eval")
    return float(evaluate(tree))


def _format_number(value: float) -> str:
    if abs(value - round(value)) < 1e-10:
        return str(int(round(value)))
    return f"{value:.6f}".rstrip("0").rstrip(".")


def _convert_units(value: float, source: str, target: str):
    aliases = {
        "m": "m", "meter": "m", "meters": "m",
        "km": "km", "kilometer": "km", "kilometers": "km",
        "cm": "cm", "centimeter": "cm", "centimeters": "cm",
        "mm": "mm", "millimeter": "mm", "millimeters": "mm",
        "in": "in", "inch": "in", "inches": "in",
        "ft": "ft", "foot": "ft", "feet": "ft",
        "yd": "yd", "yard": "yd", "yards": "yd",
        "mi": "mi", "mile": "mi", "miles": "mi",
        "g": "g", "gram": "g", "grams": "g",
        "kg": "kg", "kilogram": "kg", "kilograms": "kg",
        "lb": "lb", "lbs": "lb", "pound": "lb", "pounds": "lb",
        "oz": "oz", "ounce": "oz", "ounces": "oz",
    }
    length_to_m = {
        "m": 1.0, "km": 1000.0, "cm": 0.01, "mm": 0.001,
        "in": 0.0254, "ft": 0.3048, "yd": 0.9144, "mi": 1609.344,
    }
    mass_to_kg = {
        "kg": 1.0, "g": 0.001, "lb": 0.45359237, "oz": 0.028349523125,
    }

    s = aliases.get(source.lower())
    t = aliases.get(target.lower())
    if not s or not t:
        return None
    if s in length_to_m and t in length_to_m:
        return value * length_to_m[s] / length_to_m[t]
    if s in mass_to_kg and t in mass_to_kg:
        return value * mass_to_kg[s] / mass_to_kg[t]
    return None


def local_tool_result(command: str):
    clean = command.strip()
    low = clean.lower()

    if not clean:
        return "CONFUSED", "Type or say a command first."

    if low in {
        "hi", "hello", "hey", "haru", "hello haru",
        "good morning", "good afternoon", "good evening",
    }:
        return "HAPPY", "Ready. HARU Local is active. What can I help you with?"

    if low in {"who are you", "what are you"}:
        return (
            "HAPPY",
            "I'm HARU. In Local mode I can handle offline utility tasks; connect a local or online AI model for open-ended reasoning.",
        )

    if "what time" in low or "current time" in low or low == "time":
        return "HAPPY", datetime.now().strftime("%I:%M %p").lstrip("0")

    if "what date" in low or low in {"today", "date"}:
        return "HAPPY", datetime.now().strftime("%A, %B %d, %Y").replace(" 0", " ")

    if low in {"news", "latest news", "brief me", "news briefing"}:
        return (
            "HAPPY",
            "Open HARU's News tab for live local, international, and interest-aware headlines.",
        )

    # Notes
    if low in {"show notes", "list notes", "my notes", "what did you remember"}:
        notes = st.session_state.local_notes
        if not notes:
            return "HAPPY", "You don't have any HARU Local notes yet."
        return "HAPPY", "Notes:\n" + "\n".join(f"{i + 1}. {note}" for i, note in enumerate(notes))

    if low in {"clear notes", "delete all notes"}:
        st.session_state.local_notes = []
        return "HAPPY", "All HARU Local notes cleared."

    note_match = re.match(
        r"^(?:remember that|remember|note|save note)\s+(.+)$",
        clean,
        re.IGNORECASE,
    )
    if note_match:
        note = note_match.group(1).strip()
        st.session_state.local_notes.append(note)
        return "HAPPY", f"Noted: {note}"

    # Tasks / to-do list
    if low in {"show tasks", "list tasks", "my tasks", "show todo", "show to-do"}:
        tasks = st.session_state.local_tasks
        if not tasks:
            return "HAPPY", "Your HARU Local task list is empty."
        lines = []
        for i, task in enumerate(tasks):
            mark = "✓" if task.get("done") else "○"
            lines.append(f"{i + 1}. {mark} {task.get('text', '')}")
        return "HAPPY", "Tasks:\n" + "\n".join(lines)

    if low in {"clear tasks", "delete all tasks", "clear todo", "clear to-do"}:
        st.session_state.local_tasks = []
        return "HAPPY", "All HARU Local tasks cleared."

    done_match = re.match(r"^(?:done|complete|finish)\s+(?:task\s+)?(\d+)$", low)
    if done_match:
        index = int(done_match.group(1)) - 1
        tasks = st.session_state.local_tasks
        if 0 <= index < len(tasks):
            tasks[index]["done"] = True
            return "HAPPY", f"Completed task {index + 1}: {tasks[index]['text']}"
        return "CONFUSED", "That task number doesn't exist."

    task_match = re.match(
        r"^(?:add task|todo|to-do|add to tasks)\s+(.+)$",
        clean,
        re.IGNORECASE,
    )
    if task_match:
        task = task_match.group(1).strip()
        st.session_state.local_tasks.append({"text": task, "done": False})
        return "HAPPY", f"Added task: {task}"

    # Percentages
    percent_match = re.fullmatch(
        r"(?:what is|calculate)?\s*(-?\d+(?:\.\d+)?)\s*%\s*(?:of|x|\*)\s*(-?\d+(?:\.\d+)?)\s*\??",
        low,
    )
    if percent_match:
        pct = float(percent_match.group(1))
        base = float(percent_match.group(2))
        result = pct / 100.0 * base
        return "HAPPY", f"{_format_number(pct)}% of {_format_number(base)} = {_format_number(result)}"

    # Temperature
    temp_match = re.fullmatch(
        r"(?:convert\s+)?(-?\d+(?:\.\d+)?)\s*°?\s*([cf])\s+(?:to|in)\s+°?\s*([cf])",
        low,
    )
    if temp_match:
        value = float(temp_match.group(1))
        source = temp_match.group(2)
        target = temp_match.group(3)
        if source == target:
            result = value
        elif source == "c":
            result = value * 9 / 5 + 32
        else:
            result = (value - 32) * 5 / 9
        return "HAPPY", f"{_format_number(value)}°{source.upper()} = {_format_number(result)}°{target.upper()}"

    # Length / mass conversion
    convert_match = re.fullmatch(
        r"(?:convert\s+)?(-?\d+(?:\.\d+)?)\s*([a-z]+)\s+(?:to|in)\s+([a-z]+)",
        low,
    )
    if convert_match:
        value = float(convert_match.group(1))
        source = convert_match.group(2)
        target = convert_match.group(3)
        result = _convert_units(value, source, target)
        if result is not None:
            return "HAPPY", f"{_format_number(value)} {source} = {_format_number(result)} {target}"

    # Safe arithmetic expression
    calc_text = clean
    calc_text = re.sub(r"^(?:calculate|compute|what is)\s+", "", calc_text, flags=re.IGNORECASE)
    calc_text = calc_text.rstrip(" ?")
    if re.fullmatch(r"[\d\s\.\+\-\*\/\(\)%]+", calc_text) and any(op in calc_text for op in "+-*/%"):
        try:
            result = _safe_calc(calc_text)
            return "HAPPY", f"{calc_text} = {_format_number(result)}"
        except (ValueError, ZeroDivisionError, SyntaxError, OverflowError):
            return "CONFUSED", "I couldn't safely evaluate that expression."

    if low in {"last command", "what was my last command", "repeat last command"}:
        history = st.session_state.history
        if not history:
            return "HAPPY", "No previous command yet."
        return "HAPPY", f"Your last command was: {history[-1][0]}"

    if low in {"clear history", "clear conversation"}:
        st.session_state.history = []
        return "HAPPY", "Local conversation history cleared."

    if low in {"status", "local status", "haru status"}:
        return (
            "HAPPY",
            f"HARU Local is ready. Notes: {len(st.session_state.local_notes)}. "
            f"Tasks: {len(st.session_state.local_tasks)}. "
            "No external AI is required for these tools.",
        )

    if low in {"help", "commands", "what can you do", "local help"}:
        return (
            "HAPPY",
            "HARU Local can work offline with: time/date, safe calculations, percentages, "
            "length/mass/temperature conversions, notes, task lists, command history, status, and news-tab routing. "
            "Examples: “remember buy propellers”, “show notes”, “add task charge batteries”, "
            "“done 1”, “15% of 240”, “convert 10 km to miles”, or “25 C to F”. "
            "For open-ended knowledge and reasoning, connect Gemma/Ollama or an online AI in AI selector.",
        )

    return None


def ai_is_active() -> bool:
    return bool(
        st.session_state.get("ai_applied_signature")
        and st.session_state.get("ai_connection_state") == "CONNECTED"
        and st.session_state.get("ai_applied_mode") != "Off"
    )


def route_command(command: str):
    clean = command.strip()
    if not clean:
        return "CONFUSED", "Type or say a command first."

    # HARU shell mode:
    # - Connected provider is the primary agent.
    # - HARU only adds identity, recent conversation context, and local deterministic tool context.
    # - If no provider is connected, HARU falls back to its lightweight local tools.
    tool_result = local_tool_result(clean)

    if ai_is_active():
        config = current_ai_config()

        recent_history = st.session_state.get("history", [])[-6:]
        history_text = ""
        if recent_history:
            lines = []
            for question, answer in recent_history:
                lines.append(f"User: {question}")
                lines.append(f"HARU: {answer}")
            history_text = "\n".join(lines)

        tool_context = ""
        if tool_result is not None:
            _mood, tool_text = tool_result
            tool_context = (
                "\n\nHARU LOCAL TOOL CONTEXT:\n"
                f"{tool_text}\n"
                "Use this when relevant, but otherwise answer with your own native capabilities."
            )

        prompt = clean
        if history_text:
            prompt = (
                f"Recent HARU conversation:\n{history_text}"
                f"\n\nCurrent user request:\n{clean}"
            )
        prompt += tool_context

        system_prompt = (
            "You are operating as HARU's active intelligence. HARU is the shell, UI, voice, local tools, "
            "and device integration around you. Answer the user's query or address the task using your native model "
            "capabilities and provider tools when available. Do not claim you are a separate assistant unless the user asks. "
            "Do not say you lack live internet if your provider-native search tool is available and enabled. "
            "Use HARU local tool context when supplied. Never claim that a phone action was completed unless HARU actually "
            "provides a tool result confirming it."
        )

        enable_native_tools = config.provider in {
            "OpenAI API",
            "Google Gemini API",
            "Anthropic Claude API",
        }

        try:
            reply = ask_ai(
                config,
                prompt,
                system_prompt,
                enable_native_tools=enable_native_tools,
            )
            return "HAPPY", reply
        except AiRuntimeError as exc:
            if getattr(exc, "kind", "") == "quota":
                st.session_state.ai_runtime_degraded = True
                st.session_state.ai_runtime_degraded_reason = str(exc)
                st.session_state.ai_connection_state = "PAUSED"
                st.session_state.ai_connection_message = (
                    "Selected AI is configured but temporarily paused because its quota/rate limit was reached."
                )
                st.session_state.ai_status = str(exc)

                if tool_result is not None:
                    mood, tool_text = tool_result
                    return mood, (
                        f"{tool_text}\n\n"
                        "Your selected AI is temporarily paused because its provider quota/rate limit was reached. "
                        "HARU used its local tool for this request."
                    )
                return (
                    "CONFUSED",
                    "Your selected AI is temporarily paused because its provider quota/rate limit was reached. "
                    "The model configuration is preserved. Retry later or switch provider/model in AI selector.",
                )

            st.session_state.ai_runtime_degraded = True
            st.session_state.ai_runtime_degraded_reason = str(exc)
            st.session_state.ai_connection_state = "FAILED"
            st.session_state.ai_connection_message = str(exc)
            st.session_state.ai_status = str(exc)

            if tool_result is not None:
                mood, tool_text = tool_result
                return mood, f"{tool_text}\n\nThe connected AI is unavailable, so HARU used its local tool."
            return "CONFUSED", f"The connected AI is unavailable: {exc}"

    if tool_result is not None:
        return tool_result

    return (
        "CONFUSED",
        "HARU Local didn't match that request. Type “help” to see offline commands, or connect a local/online AI model for open-ended questions.",
    )


def is_cloud_haru() -> bool:
    cwd = os.getcwd().replace("\\", "/").lower()
    return cwd.startswith("/mount/src/") or "streamlit" in os.environ.get("HOSTNAME", "").lower()


def choose_ollama_model(models: list[str]) -> str:
    if not models:
        return ""

    def score(name: str) -> tuple[int, float]:
        low = name.lower()
        family_score = 0
        priorities = [
            ("qwen3", 90),
            ("gemma4", 88),
            ("gemma3", 85),
            ("llama3", 82),
            ("phi4", 78),
            ("mistral", 72),
        ]
        for token, value in priorities:
            if token in low:
                family_score = value
                break

        size = 0.0
        match = re.search(r"(\d+(?:\.\d+)?)b", low)
        if match:
            size = float(match.group(1))

        # Favor capable but still desktop-friendly models.
        if size > 14:
            size_bonus = 0
        elif size >= 7:
            size_bonus = 12
        elif size >= 3:
            size_bonus = 10
        else:
            size_bonus = 5

        return family_score + size_bonus, size

    return max(models, key=score)


def draft_ai_config() -> AiConfig:
    return AiConfig(
        mode=st.session_state.get("ai_mode", "Off"),
        provider=st.session_state.get("ai_provider", "Disabled"),
        model=st.session_state.get("ai_model", ""),
        endpoint=st.session_state.get("ai_endpoint", ""),
        api_key=resolved_api_key(
            st.session_state.get("ai_provider", "Disabled"),
            st.session_state.get("ai_api_key", ""),
        ),
        timeout_s=25,
    )


def ai_config_signature(config: AiConfig) -> str:
    # Never keep the raw API key inside the configuration signature.
    # Only a one-way fingerprint is used to detect key/config changes.
    key_fingerprint = (
        hashlib.sha256(config.api_key.encode("utf-8")).hexdigest()
        if config.api_key
        else ""
    )
    return "|".join(
        [
            config.mode,
            config.provider,
            config.model.strip(),
            config.endpoint.strip(),
            key_fingerprint,
        ]
    )


def current_ai_config() -> AiConfig:
    if st.session_state.get("ai_applied_signature"):
        return AiConfig(
            mode=st.session_state.get("ai_applied_mode", "Off"),
            provider=st.session_state.get("ai_applied_provider", "Disabled"),
            model=st.session_state.get("ai_applied_model", ""),
            endpoint=st.session_state.get("ai_applied_endpoint", ""),
            api_key=resolved_api_key(
                st.session_state.get("ai_applied_provider", "Disabled"),
                st.session_state.get("ai_applied_api_key", ""),
            ),
            timeout_s=25,
        )
    return draft_ai_config()


def effective_ai_connection_state() -> tuple[str, str]:
    if st.session_state.get("ai_mode") == "Off":
        return "OFF", "AI runtime is disabled."

    draft_signature = ai_config_signature(draft_ai_config())
    applied_signature = st.session_state.get("ai_applied_signature", "")

    if applied_signature and draft_signature != applied_signature:
        return "UNTESTED", "Settings changed. Apply again to use this configuration."

    return (
        st.session_state.get("ai_connection_state", "UNTESTED"),
        st.session_state.get("ai_connection_message", "Not tested."),
    )


def ai_status_html(state: str, message: str) -> str:
    palette = {
        "OFF": ("#7a7f87", "OFF"),
        "UNTESTED": ("#e0a100", "UNTESTED"),
        "CONNECTING": ("#1976d2", "CONNECTING"),
        "CONNECTED": ("#2e7d32", "CONNECTED"),
        "PAUSED": ("#e0a100", "PAUSED"),
        "FAILED": ("#c62828", "FAILED"),
    }
    color, label = palette.get(state, ("#7a7f87", state))
    safe_label = html.escape(str(label))
    safe_message = html.escape(str(message))
    return f"""
    <div style="
        display:flex; align-items:center; gap:.65rem;
        padding:.7rem .85rem; border:1px solid rgba(127,127,127,.22);
        border-radius:12px; margin:.35rem 0 .85rem 0;">
        <span style="
            width:12px; height:12px; border-radius:50%;
            background:{color}; display:inline-block;
            box-shadow:0 0 0 4px {color}22;"></span>
        <div>
            <div style="font-weight:700; font-size:.9rem;">{safe_label}</div>
            <div style="font-size:.82rem; opacity:.72;">{safe_message}</div>
        </div>
    </div>
    """


def active_model_display_name() -> str:
    model = st.session_state.get("ai_applied_model", "").strip()
    provider = st.session_state.get("ai_applied_provider", "").strip()

    if not st.session_state.get("ai_applied_signature"):
        return "HARU Local"

    friendly = {
        "gpt-5.6-sol": "GPT-5.6 Sol",
        "gpt-5.6-terra": "GPT-5.6 Terra",
        "gpt-5.6-luna": "GPT-5.6 Luna",
        "gemini-3.8-flash": "Gemini 3.8 Flash",
        "gemini-3.7-flash": "Gemini 3.7 Flash",
        "gemini-3.6-flash": "Gemini 3.6 Flash",
        "gemini-3.5-flash": "Gemini 3.5 Flash",
        "gemini-3.5-flash-lite": "Gemini 3.5 Flash-Lite",
        "gemini-3.1-flash-lite": "Gemini 3.1 Flash-Lite",
        "gemini-3.1-pro-preview": "Gemini 3.1 Pro Preview",
        "claude-fable-5": "Claude Fable 5",
        "claude-opus-5": "Claude Opus 5",
        "claude-sonnet-5": "Claude Sonnet 5",
        "claude-haiku-4-5-20251001": "Claude Haiku 4.5",
        "google/gemma-4-E4B-it": "Gemma 4 E4B",
        "google/gemma-4-E2B-it": "Gemma 4 E2B",
        "Qwen/Qwen3-4B": "Qwen3 4B",
        "microsoft/Phi-4-mini-instruct": "Phi-4 Mini",
        "phone-local": "Phone Local",
    }

    if model in friendly:
        label = friendly[model]
    elif model:
        label = model.split("/")[-1].replace("_", " ")
    else:
        label = provider or "Connected AI"

    if st.session_state.get("ai_runtime_degraded"):
        return f"{label} · paused"
    return label


def render_haru_mascot(mood: str) -> None:
    """Render HARU as lightweight original line art."""
    if HARU_LINEART.exists():
        left, center, right = st.columns([1.25, 1.5, 1.25])
        with center:
            st.image(str(HARU_LINEART), use_container_width=True)
        return

    st.markdown(face_html(mood), unsafe_allow_html=True)


def render_haru_theme_control() -> None:
    """Optional HARU theme with a hidden 10%-volume player."""
    ensure_haru_media()

    if not HARU_THEME.exists():
        return

    enabled = st.toggle(
        "♪ HARU theme",
        value=st.session_state.music_enabled,
        key="haru_music_toggle",
        help="Play HARU's theme quietly in the background.",
    )
    st.session_state.music_enabled = enabled

    if enabled:
        audio_b64 = base64.b64encode(HARU_THEME.read_bytes()).decode("ascii")
        st.markdown(
            f"""
            <audio id="haru-theme-audio" autoplay loop style="display:none">
              <source src="data:audio/wav;base64,{audio_b64}" type="audio/wav">
            </audio>
            <script>
            (() => {{
              const audio = document.getElementById("haru-theme-audio");
              if (audio) {{
                audio.volume = 0.10;
                const playPromise = audio.play();
                if (playPromise) playPromise.catch(() => {{}});
              }}
            }})();
            </script>
            """,
            unsafe_allow_html=True,
        )


def render_latest_history_tracker() -> None:
    """Show the latest user-to-HARU exchange for quick conversation tracking."""
    history = st.session_state.get("history", [])
    if history:
        latest = history[-1]
        if isinstance(latest, (tuple, list)) and len(latest) >= 2:
            user_text, haru_text = str(latest[0]), str(latest[1])
        else:
            user_text, haru_text = str(latest), ""
    else:
        user_text, haru_text = "No previous message yet.", "Hello. I'm HARU."

    safe_user = html.escape(user_text).replace("\n", "<br>")
    safe_haru = html.escape(haru_text).replace("\n", "<br>")

    st.markdown(
        f"""
        <div class="history-track">
          <div class="history-label">Latest communication</div>
          <div class="history-line"><strong>You:</strong> {safe_user}</div>
          <div class="history-line"><strong>HARU:</strong> {safe_haru}</div>
        </div>
        """,
        unsafe_allow_html=True,
    )


def face_html(mood: str):
    palette = {
        "IDLE": "#263238",
        "LISTENING": "#00897b",
        "THINKING": "#1565c0",
        "WORKING": "#1565c0",
        "HAPPY": "#2e7d32",
        "CONFUSED": "#f57c00",
        "ALERT": "#c62828",
        "SLEEPY": "#6a1b9a",
    }
    color = palette.get(mood, "#263238")

    if mood == "HAPPY":
        eyes, mouth = "^   ^", "◡"
    elif mood == "CONFUSED":
        eyes, mouth = "•   •", "︵"
    elif mood == "ALERT":
        eyes, mouth = "○   ○", "o"
    elif mood == "SLEEPY":
        eyes, mouth = "—   —", "ᴗ"
    elif mood == "THINKING":
        eyes, mouth = "◔   ◔", "—"
    elif mood == "LISTENING":
        eyes, mouth = "◉   ◉", "—"
    else:
        eyes, mouth = "●   ●", "ᴗ"

    return f"""
    <div class="haru-wrap">
        <div class="haru-face" style="border-color:{color}; color:{color};">
            <div class="eyes">{eyes}</div>
            <div class="mouth">{mouth}</div>
        </div>
    </div>
    """


@st.cache_data(ttl=900, show_spinner=False)
def fetch_news_bundle(region: str, interests: tuple[str, ...], nonce: int):
    del nonce
    errors = []
    local = []
    world = []
    personalized = []

    try:
        local = local_region_headlines(region, 12)
    except Exception as exc:
        errors.append(f"Local news: {exc}")

    try:
        world = world_headlines(12)
    except Exception as exc:
        errors.append(f"International news: {exc}")

    for topic in interests[:2]:
        try:
            personalized.extend(topic_headlines(topic, 8))
        except Exception as exc:
            errors.append(f"{topic}: {exc}")

    return deduplicate(local), deduplicate(world), deduplicate(personalized), errors


def render_news_items(items, limit: int = 6):
    if not items:
        st.info("No stories are available right now.")
        return

    for index, item in enumerate(items[:limit]):
        st.markdown(f"**{item.title}**")
        st.caption(f"{item.source} · {friendly_time(item)}")
        st.link_button("Open story", item.link, use_container_width=False)
        if index < min(limit, len(items)) - 1:
            st.divider()


st.markdown(
    """
    <style>
      .block-container {
          max-width: 760px;
          padding-top: 2.15rem;
          padding-bottom: 1.25rem;
      }
      .haru-title {
          text-align:center;
          font-size:2rem;
          font-weight:800;
          line-height:1.25;
          padding-top:.15rem;
          margin:0 0 .05rem 0;
          display:flex;
          align-items:center;
          justify-content:center;
          gap:.45rem;
          flex-wrap:wrap;
          overflow:visible;
      }
      .model-badge {
          display:inline-flex;
          align-items:center;
          font-size:.72rem;
          font-weight:700;
          line-height:1.15;
          padding:.24rem .5rem;
          border-radius:999px;
          background:rgba(127,127,127,.12);
          border:1px solid rgba(127,127,127,.22);
          letter-spacing:.01rem;
      }
      .haru-sub {
          text-align:center;
          opacity:.62;
          font-size:.9rem;
          margin:.15rem 0 .45rem 0;
      }
      .haru-wrap {
          display:flex;
          justify-content:center;
          margin:.45rem 0 .55rem;
      }
      .haru-face {
          width:182px;
          height:182px;
          border:7px solid;
          border-radius:50%;
          display:flex;
          flex-direction:column;
          align-items:center;
          justify-content:center;
          box-shadow:0 6px 22px rgba(0,0,0,.07);
          animation:breathe 2.2s ease-in-out infinite alternate;
      }
      .eyes {
          font-size:1.9rem;
          font-weight:800;
          letter-spacing:.35rem;
          line-height:1;
      }
      .mouth {
          font-size:2.25rem;
          margin-top:.7rem;
          line-height:1;
      }
      @keyframes breathe { from { transform:scale(.99); } to { transform:scale(1.01); } }
      .status {
          text-align:center;
          font-size:.72rem;
          font-weight:700;
          letter-spacing:.11rem;
          opacity:.62;
          margin:.05rem 0 .25rem 0;
      }
      .reply {
          padding:.75rem .95rem;
          border-radius:14px;
          background:rgba(127,127,127,.08);
          margin-bottom:.55rem;
          font-size:.98rem;
          line-height:1.45;
      }
      div[data-testid="stChatInput"] {
          margin:.15rem 0 .35rem 0;
      }
      div[data-testid="stChatInput"] textarea {
          min-height:44px !important;
          max-height:110px !important;
          line-height:1.35 !important;
      }
      .stTabs [data-baseweb="tab-list"] {
          gap:.35rem;
      }
      .stTabs [data-baseweb="tab"] {
          padding-top:.45rem;
          padding-bottom:.45rem;
      }
      div[data-testid="stExpander"] {
          margin-top:.28rem;
      }
      div[data-testid="stExpander"] details {
          border-radius:10px;
      }
      button, input, textarea, select {
          box-shadow:none !important;
      }
      .stImage img {
          max-width:260px;
          margin:0 auto;
          display:block;
          border-radius:0;
          filter:none;
      }
      .history-track {
          padding:.72rem .9rem;
          border:1px solid rgba(127,127,127,.22);
          border-radius:12px;
          background:rgba(127,127,127,.045);
          margin-top:.35rem;
      }
      .history-label {
          font-size:.7rem;
          font-weight:700;
          letter-spacing:.08em;
          text-transform:uppercase;
          opacity:.58;
          margin-bottom:.38rem;
      }
      .history-line {
          font-size:.9rem;
          line-height:1.35;
          margin:.12rem 0;
          overflow-wrap:anywhere;
      }
      .footer {
          text-align:center;
          opacity:.45;
          font-size:.72rem;
          margin-top:.6rem;
      }
      @media (max-width: 640px) {
          .block-container { padding-top: 2.4rem; }
          .haru-title { font-size:1.8rem; }
          .haru-face { width:165px; height:165px; border-width:6px; }
          .eyes { font-size:1.75rem; }
          .mouth { font-size:2rem; }
      }
    </style>
    """,
    unsafe_allow_html=True,
)

model_badge = html.escape(active_model_display_name())
st.markdown(
    f'<div class="haru-title">HARU <span class="model-badge">{model_badge}</span></div>',
    unsafe_allow_html=True,
)
st.markdown('<div class="haru-sub">Human Assistance & Responsive Utility</div>', unsafe_allow_html=True)

assistant_tab, news_tab = st.tabs(["Assistant", "News"])

with assistant_tab:
    render_haru_mascot(st.session_state.mood)
    st.markdown(f'<div class="status">{st.session_state.mood}</div>', unsafe_allow_html=True)
    render_haru_theme_control()
    safe_reply = html.escape(str(st.session_state.message)).replace("\n", "<br>")
    st.markdown(f'<div class="reply">{safe_reply}</div>', unsafe_allow_html=True)

    with st.container(border=True):
        st.caption("Ask HARU · Enter to send · Shift+Enter for a new line")
        command = st.chat_input(
            "Type a question or task…",
            key="haru_chat_input",
        )
        render_latest_history_tracker()

    if command:
        st.session_state.mood = "THINKING"
        mood, reply = route_command(command)
        st.session_state.mood = mood
        st.session_state.message = reply
        st.session_state.history.append((command, reply))
        st.rerun()

with news_tab:
    scores = infer_interests(st.session_state.history, st.session_state.explicit_interests)
    interests = top_interests(scores, 2)

    top_left, top_right = st.columns([3, 1])
    with top_left:
        st.subheader("HARU News")
        st.caption("General coverage remains visible. Interests only add stories; they do not replace the briefing.")
    with top_right:
        if st.button("Refresh", use_container_width=True):
            st.session_state.news_refresh_nonce += 1
            st.cache_data.clear()
            st.rerun()

    with st.expander("News settings"):
        region = st.text_input(
            "Local news area",
            value=st.session_state.news_region,
            help="Use Philippines, a province, city, or region. HARU does not require precise device location.",
        )
        if region != st.session_state.news_region:
            st.session_state.news_region = region.strip() or "Philippines"

        st.caption("Optional interests")
        interest_labels = {
            "technology": "Technology",
            "aerospace": "Aerospace / UAV",
            "science": "Science / Engineering",
            "business": "Business / Economy",
            "weather_disaster": "Weather / Disaster",
            "sports": "Sports",
            "entertainment": "Entertainment / Gaming",
            "health": "Health",
        }
        selected = st.multiselect(
            "Add topics you want HARU to watch",
            options=list(interest_labels),
            default=[k for k, v in st.session_state.explicit_interests.items() if v > 0],
            format_func=lambda x: interest_labels[x],
        )
        st.session_state.explicit_interests = {topic: 3 for topic in selected}

    scores = infer_interests(st.session_state.history, st.session_state.explicit_interests)
    interests = top_interests(scores, 2)

    with st.spinner("Checking headlines…"):
        local_items, world_items, personalized_items, news_errors = fetch_news_bundle(
            st.session_state.news_region,
            tuple(interests),
            st.session_state.news_refresh_nonce,
        )

    all_items = deduplicate(local_items + world_items + personalized_items)
    newest_ts = max((item.published_ts for item in all_items), default=0.0)
    unread = sum(1 for item in all_items if item.published_ts > st.session_state.news_last_seen)

    if unread > 0:
        st.info(f"🔔 {unread} new stories since your last visit.")
    else:
        st.caption("No new stories since your last visit.")

    st.session_state.news_last_seen = max(st.session_state.news_last_seen, newest_ts)

    if news_errors:
        with st.expander("Feed status"):
            for err in news_errors:
                st.caption(err)

    st.markdown("### 🇵🇭 Local first")
    st.caption(f"Area: {st.session_state.news_region}")
    render_news_items(local_items, 6)

    st.markdown("### 🌍 International")
    render_news_items(world_items, 6)

    st.markdown("### 🧭 General + your interests")
    if interests:
        st.caption("Based only on your HARU activity and topics you selected: " + ", ".join(i.replace("_", " ") for i in interests))
        render_news_items(personalized_items, 6)
    else:
        st.caption("Use HARU normally or choose topics in News settings. General/local coverage will still remain visible.")
        general = deduplicate(philippines_headlines(6) + world_items[:4]) if not local_items else deduplicate(local_items[:3] + world_items[:3])
        render_news_items(general, 6)

with st.expander("AI selector"):
    st.markdown("#### AI runtime")
    st.caption(
        "Apply a model to make HARU a shell for that AI agent. The selected provider handles queries with its own "
        "native capabilities and tools; HARU supplies the UI, conversation context, and local device functions."
    )

    connection_state, connection_message = effective_ai_connection_state()
    st.markdown(
        ai_status_html(connection_state, connection_message),
        unsafe_allow_html=True,
    )
    if st.session_state.get("ai_applied_signature"):
        st.caption(
            "Active: "
            f"{st.session_state.ai_applied_provider} · "
            f"{st.session_state.ai_applied_model}"
        )

    ai_mode = st.radio(
        "Runtime",
        ["Off", "Local", "Online"],
        horizontal=True,
        index=["Off", "Local", "Online"].index(st.session_state.ai_mode),
    )
    st.session_state.ai_mode = ai_mode

    if ai_mode == "Off":
        st.session_state.ai_provider = "Disabled"
        st.session_state.ai_connection_state = "OFF"
        st.session_state.ai_runtime_degraded = False
        st.session_state.ai_runtime_degraded_reason = ""
        st.session_state.ai_connection_message = "AI runtime is disabled."
        st.session_state.ai_applied_signature = ""
        st.session_state.ai_applied_mode = "Off"
        st.session_state.ai_applied_provider = "Disabled"
        st.session_state.ai_applied_model = ""
        st.session_state.ai_applied_endpoint = ""
        st.session_state.ai_applied_api_key = ""
        st.info("AI is disabled. HARU uses only local deterministic skills.")
    else:
        if ai_mode == "Local":
            provider_options = [
                "Ollama",
                "Local OpenAI-compatible",
                "Android on-device (APK only)",
            ]
            if st.session_state.ai_provider not in provider_options:
                st.session_state.ai_provider = "Ollama"
            st.caption(
                "Recommended: Ollama on the same PC as HARU. No API key required."
            )
        else:
            provider_options = [
                "OpenRouter API",
                "Google Gemini API",
            ]
            if st.session_state.ai_provider not in provider_options:
                st.session_state.ai_provider = provider_options[0]

        provider_labels = {
            "Ollama": "Ollama on this PC — recommended",
            "Local OpenAI-compatible": "Other local OpenAI-compatible server",
            "Android on-device (APK only)": "Android on-device model",
            "Google Gemini API": "Google Gemini API",
            "OpenRouter API": "OpenRouter API",
        }
        provider = st.selectbox(
            "Provider",
            provider_options,
            index=provider_options.index(st.session_state.ai_provider),
            format_func=lambda item: provider_labels.get(item, item),
        )
        st.session_state.ai_provider = provider

        model_catalogs = {
            "Google Gemini API": {
                "Gemini 3.8 Flash — newest stable Flash": "gemini-3.8-flash",
                "Gemini 3.7 Flash — previous stable": "gemini-3.7-flash",
                "Gemini 3.6 Flash — balanced stable": "gemini-3.6-flash",
                "Gemini 3.5 Flash — stable general model": "gemini-3.5-flash",
                "Gemini 3.5 Flash-Lite — low-cost/high-throughput": "gemini-3.5-flash-lite",
                "Gemini 3.1 Flash-Lite — very low-cost stable": "gemini-3.1-flash-lite",
                "Gemini 3.1 Pro Preview — higher capability preview": "gemini-3.1-pro-preview",
            },
            "Android on-device (APK only)": {
                "Gemma 4 E4B Instruct — HARU recommended": "google/gemma-4-E4B-it",
                "Gemma 4 E2B Instruct — lightweight fallback": "google/gemma-4-E2B-it",
                "Qwen3 4B — text-only alternative": "Qwen/Qwen3-4B",
                "Phi-4 Mini — compact reasoning alternative": "microsoft/Phi-4-mini-instruct",
            },
        }

        default_endpoints = {
            "Ollama": "http://localhost:11434",
            "Local OpenAI-compatible": "http://localhost:1234",
            "OpenRouter API": "https://openrouter.ai/api",
        }

        if provider == "Ollama":
            st.session_state.ai_endpoint = st.session_state.ai_endpoint or "http://localhost:11434"

            if is_cloud_haru():
                st.warning(
                    "This HARU is running on Streamlit Cloud, so localhost points to the cloud server—not your Windows PC. "
                    "Your Ollama installation is working, but this cloud copy cannot reach it."
                )
                st.code(".\\run_haru_local.bat", language="powershell")
                st.caption(
                    "Run the launcher from a local copy of the HARU repository. "
                    "Then open http://localhost:8501 and choose Local → Ollama."
                )
            else:
                if st.button(
                    "Connect Ollama on this PC",
                    type="primary",
                    use_container_width=True,
                    key="ollama_quick_connect",
                ):
                    try:
                        names = list_ollama_models(st.session_state.ai_endpoint)
                        st.session_state["ollama_models"] = names

                        if not names:
                            st.session_state.ai_connection_state = "FAILED"
                            st.session_state.ai_connection_message = (
                                "Ollama is reachable, but no models are installed."
                            )
                            st.session_state.ai_status = "No Ollama models installed."
                            st.rerun()

                        chosen = choose_ollama_model(names)
                        st.session_state.ai_model = chosen
                        candidate = AiConfig(
                            mode="Local",
                            provider="Ollama",
                            model=chosen,
                            endpoint=st.session_state.ai_endpoint,
                            api_key="",
                            timeout_s=25,
                        )

                        result = test_ai(candidate)
                        st.session_state.ai_applied_mode = "Local"
                        st.session_state.ai_applied_provider = "Ollama"
                        st.session_state.ai_applied_model = chosen
                        st.session_state.ai_applied_endpoint = st.session_state.ai_endpoint
                        st.session_state.ai_applied_api_key = ""
                        st.session_state.ai_applied_signature = ai_config_signature(candidate)
                        st.session_state.ai_connection_state = "CONNECTED"
                        st.session_state.ai_runtime_degraded = False
                        st.session_state.ai_runtime_degraded_reason = ""
                        st.session_state.ai_connection_message = f"Ollama connected. {chosen} is now HARU."
                        st.session_state.ai_status = result
                        st.rerun()
                    except AiRuntimeError as exc:
                        st.session_state.ai_connection_state = "FAILED"
                        st.session_state.ai_connection_message = (
                            "HARU could not reach Ollama at http://localhost:11434. "
                            "Confirm Ollama is running, then try again."
                        )
                        st.session_state.ai_status = str(exc)
                        st.rerun()

                discovered = st.session_state.get("ollama_models", [])
                if not discovered:
                    try:
                        discovered = list_ollama_models(st.session_state.ai_endpoint, timeout_s=2)
                        st.session_state["ollama_models"] = discovered
                    except AiRuntimeError:
                        discovered = []

                if discovered:
                    current = (
                        st.session_state.ai_model
                        if st.session_state.ai_model in discovered
                        else choose_ollama_model(discovered)
                    )
                    selected_model = st.selectbox(
                        "Installed model",
                        discovered,
                        index=discovered.index(current),
                        help="HARU detected these models from Ollama. qwen3:8b is suitable if already installed.",
                    )
                    st.session_state.ai_model = selected_model
                    st.success(f"Ollama detected · {len(discovered)} model(s) available")

                with st.expander("Advanced Ollama settings"):
                    st.session_state.ai_endpoint = st.text_input(
                        "Ollama endpoint",
                        value=st.session_state.ai_endpoint,
                        help="Default: http://localhost:11434",
                    )
                    if st.button("Refresh installed models", use_container_width=True):
                        try:
                            names = list_ollama_models(st.session_state.ai_endpoint)
                            st.session_state["ollama_models"] = names
                            st.session_state.ai_status = f"Found {len(names)} model(s)."
                            st.rerun()
                        except AiRuntimeError as exc:
                            st.session_state.ai_status = f"Ollama discovery failed: {exc}"

        elif provider == "OpenRouter API":
            st.session_state.ai_endpoint = "https://openrouter.ai/api"

            openrouter_server_key = server_secret_for_provider("OpenRouter API")
            openrouter_saved_key = local_stored_key_for_provider("OpenRouter API")
            if openrouter_server_key:
                st.success("OpenRouter key loaded securely from Streamlit/server secrets.")
                st.session_state.ai_api_key = ""
            elif openrouter_saved_key:
                st.success("OpenRouter key loaded securely from this PC's credential store.")
                st.session_state.ai_api_key = ""
            else:
                st.session_state.ai_api_key = st.text_input(
                    "OpenRouter API key",
                    value=st.session_state.ai_api_key,
                    type="password",
                    placeholder="sk-or-v1-…",
                    help=(
                        "Local Windows HARU saves a successfully connected key to Windows Credential Manager. "
                        "On Streamlit Cloud, use Streamlit Secrets for persistence."
                    ),
                )

            openrouter_mode = st.radio(
                "Model access",
                [
                    "Free auto-router — recommended",
                    "Choose a specific free model",
                    "All OpenRouter models",
                ],
                horizontal=False,
                key="openrouter_model_mode",
            )

            if openrouter_mode == "Free auto-router — recommended":
                st.session_state.ai_model = "openrouter/free"
                st.success(
                    "Using OpenRouter Free Models Router. HARU will automatically use an available free model."
                )
                st.caption(
                    "Model ID: openrouter/free · No token charge. Free-plan request limits still apply."
                )
            else:
                if st.button("Refresh OpenRouter models", use_container_width=True):
                    try:
                        names = list_openai_compatible_models(
                            st.session_state.ai_endpoint,
                            resolved_api_key("OpenRouter API", st.session_state.ai_api_key),
                        )
                        st.session_state["openrouter_models"] = names
                        st.session_state.ai_status = (
                            f"Found {len(names)} OpenRouter model(s)."
                            if names else
                            "OpenRouter returned no models."
                        )
                        st.rerun()
                    except AiRuntimeError as exc:
                        st.session_state["openrouter_models"] = []
                        st.session_state.ai_status = f"OpenRouter discovery failed: {exc}"

                discovered = st.session_state.get("openrouter_models", [])

                if openrouter_mode == "Choose a specific free model":
                    free_models = [
                        name for name in discovered
                        if name == "openrouter/free" or name.endswith(":free")
                    ]
                    if "openrouter/free" not in free_models:
                        free_models.insert(0, "openrouter/free")

                    current = (
                        st.session_state.ai_model
                        if st.session_state.ai_model in free_models
                        else free_models[0]
                    )
                    st.session_state.ai_model = st.selectbox(
                        "Free model",
                        free_models,
                        index=free_models.index(current),
                        help="Free variants normally use the :free suffix. Availability can change.",
                    )
                    st.caption("Free model selected. OpenRouter free-tier rate limits still apply.")
                else:
                    if discovered:
                        current = (
                            st.session_state.ai_model
                            if st.session_state.ai_model in discovered
                            else discovered[0]
                        )
                        st.session_state.ai_model = st.selectbox(
                            "OpenRouter model",
                            discovered,
                            index=discovered.index(current),
                            help="Shows models returned by your OpenRouter account.",
                        )
                    else:
                        st.session_state.ai_model = st.text_input(
                            "OpenRouter model ID",
                            value=st.session_state.ai_model,
                            placeholder="e.g. openrouter/free",
                            help="Refresh models to avoid typing model IDs manually.",
                        )

        elif provider == "Local OpenAI-compatible":
            st.session_state.ai_endpoint = st.text_input(
                "Endpoint",
                value=st.session_state.ai_endpoint or default_endpoints[provider],
                help="Use the base URL only. HARU adds the provider API path automatically.",
            )

            if st.button("Discover endpoint models", use_container_width=True):
                try:
                    names = list_openai_compatible_models(
                        st.session_state.ai_endpoint,
                        st.session_state.ai_api_key,
                    )
                    st.session_state["compatible_models"] = names
                    st.session_state.ai_status = f"Found {len(names)} model(s)." if names else "No models returned by endpoint."
                except AiRuntimeError as exc:
                    st.session_state["compatible_models"] = []
                    st.session_state.ai_status = f"Model discovery failed: {exc}"

            discovered = st.session_state.get("compatible_models", [])
            options = discovered + ["Custom model…"] if discovered else ["Custom model…"]
            current_label = st.session_state.ai_model if st.session_state.ai_model in discovered else "Custom model…"
            selected_model = st.selectbox(
                "Model type",
                options,
                index=options.index(current_label),
            )
            if selected_model == "Custom model…":
                st.session_state.ai_model = st.text_input(
                    "Custom model ID",
                    value=st.session_state.ai_model if st.session_state.ai_model not in discovered else "",
                )
            else:
                st.session_state.ai_model = selected_model

        else:
            catalog = model_catalogs.get(provider, {})
            labels = list(catalog.keys()) + ["Custom model…"]
            current_label = next(
                (label for label, model_id in catalog.items() if model_id == st.session_state.ai_model),
                labels[0] if catalog and not st.session_state.ai_model else "Custom model…",
            )
            selected_label = st.selectbox(
                "Model type",
                labels,
                index=labels.index(current_label),
                help="Friendly model names are mapped internally to the provider's API model ID.",
            )

            if selected_label == "Custom model…":
                st.session_state.ai_model = st.text_input(
                    "Custom model ID",
                    value=st.session_state.ai_model if st.session_state.ai_model not in catalog.values() else "",
                    placeholder="Enter provider model ID",
                )
            else:
                st.session_state.ai_model = catalog[selected_label]

            if st.session_state.ai_model:
                st.caption(f"API model: {st.session_state.ai_model}")
            if provider == "Android on-device (APK only)":
                st.info(
                    "HARU local default: Gemma 4 E4B Instruct. "
                    "Use E2B on lower-memory phones. Streamlit only configures this target; "
                    "the model itself will run inside the native Android app through the mobile inference runtime."
                )

        if provider == "Google Gemini API":
            provider_server_key = server_secret_for_provider(provider)
            provider_saved_key = local_stored_key_for_provider(provider)
            if provider_server_key:
                st.success(f"{provider} key loaded securely from Streamlit/server secrets.")
                st.session_state.ai_api_key = ""
            elif provider_saved_key:
                st.success(f"{provider} key loaded securely from this PC's credential store.")
                st.session_state.ai_api_key = ""
            else:
                st.session_state.ai_api_key = st.text_input(
                    "API key",
                    value=st.session_state.ai_api_key,
                    type="password",
                    help=(
                        "Local Windows HARU saves a successfully connected key to Windows Credential Manager. "
                        "On Streamlit Cloud, use Streamlit Secrets for persistence."
                    ),
                )
        elif provider == "OpenRouter API":
            # OpenRouter renders its dedicated key field above. Preserve that
            # session value so Apply & connect can authenticate successfully.
            pass
        else:
            st.session_state.ai_api_key = ""

        if provider != "Ollama":
            st.info(
                "When applied, HARU becomes the shell for this model. Gemini keeps native web-search grounding; "
                "OpenRouter uses the selected routed model. HARU local tools remain available for device-specific tasks."
            )

        if provider != "Ollama":
            apply_col, test_col, clear_col = st.columns([1.35, 1, 1])
            with apply_col:
                if st.button("Apply & connect", type="primary", use_container_width=True):
                    candidate = draft_ai_config()
                    st.session_state.ai_connection_state = "CONNECTING"
                    st.session_state.ai_connection_message = (
                        f"Testing {candidate.provider} · {candidate.model or 'no model selected'}…"
                    )
                    try:
                        result = test_ai(candidate)
                        st.session_state.ai_applied_mode = candidate.mode
                        st.session_state.ai_applied_provider = candidate.provider
                        st.session_state.ai_applied_model = candidate.model
                        st.session_state.ai_applied_endpoint = candidate.endpoint

                        if (
                            candidate.api_key
                            and not is_cloud_haru()
                            and not server_secret_for_provider(candidate.provider)
                            and st.session_state.get("ai_api_key")
                        ):
                            save_stored_key(candidate.provider, candidate.api_key)

                        st.session_state.ai_applied_api_key = (
                            ""
                            if (
                                server_secret_for_provider(candidate.provider)
                                or local_stored_key_for_provider(candidate.provider)
                            )
                            else candidate.api_key
                        )
                        st.session_state.ai_applied_signature = ai_config_signature(candidate)
                        st.session_state.ai_connection_state = "CONNECTED"
                        st.session_state.ai_runtime_degraded = False
                        st.session_state.ai_runtime_degraded_reason = ""
                        st.session_state.ai_connection_message = (
                            f"Connected. {candidate.provider} · {candidate.model} is now HARU's active brain."
                        )
                        st.session_state.ai_status = result
                        st.rerun()
                    except AiRuntimeError as exc:
                        st.session_state.ai_connection_state = "FAILED"
                        st.session_state.ai_connection_message = str(exc)
                        st.session_state.ai_status = f"Connection failed: {exc}"
                        st.rerun()
    
            with test_col:
                if st.button("Retest", use_container_width=True):
                    config = current_ai_config()
                    try:
                        result = test_ai(config)
                        st.session_state.ai_connection_state = "CONNECTED"
                        st.session_state.ai_runtime_degraded = False
                        st.session_state.ai_runtime_degraded_reason = ""
                        st.session_state.ai_connection_message = (
                            f"Connected. {config.provider} · {config.model} is HARU's active brain."
                        )
                        st.session_state.ai_status = result
                        st.rerun()
                    except AiRuntimeError as exc:
                        st.session_state.ai_connection_state = "FAILED"
                        st.session_state.ai_connection_message = str(exc)
                        st.session_state.ai_status = f"Connection failed: {exc}"
                        st.rerun()
    
            with clear_col:
                if st.button("Forget key", use_container_width=True):
                    st.session_state.ai_api_key = ""
                    st.session_state.ai_applied_api_key = ""

                    if not is_cloud_haru():
                        delete_stored_key(provider)

                    st.session_state.ai_applied_signature = ""
                    st.session_state.ai_connection_state = "UNTESTED"
                    st.session_state.ai_connection_message = (
                        "For Streamlit Cloud, remove the key from app Secrets to forget it."
                        if is_cloud_haru() and server_secret_for_provider(provider)
                        else "Saved credential removed. Enter a key to reconnect."
                    )
                    st.session_state.ai_status = "Credential cleared."
                    st.rerun()
    
        elif not is_cloud_haru() and st.session_state.get("ai_applied_provider") == "Ollama":
            test_col, disconnect_col = st.columns(2)
            with test_col:
                if st.button("Retest Ollama", use_container_width=True):
                    try:
                        config = current_ai_config()
                        result = test_ai(config)
                        st.session_state.ai_connection_state = "CONNECTED"
                        st.session_state.ai_runtime_degraded = False
                        st.session_state.ai_runtime_degraded_reason = ""
                        st.session_state.ai_connection_message = (
                            f"Ollama connected. {config.model} is HARU."
                        )
                        st.session_state.ai_status = result
                        st.rerun()
                    except AiRuntimeError as exc:
                        st.session_state.ai_connection_state = "FAILED"
                        st.session_state.ai_connection_message = str(exc)
                        st.session_state.ai_status = str(exc)
                        st.rerun()
            with disconnect_col:
                if st.button("Disconnect Ollama", use_container_width=True):
                    st.session_state.ai_applied_signature = ""
                    st.session_state.ai_applied_mode = "Off"
                    st.session_state.ai_applied_provider = "Disabled"
                    st.session_state.ai_applied_model = ""
                    st.session_state.ai_applied_endpoint = ""
                    st.session_state.ai_connection_state = "UNTESTED"
                    st.session_state.ai_connection_message = "Ollama disconnected."
                    st.rerun()

        st.caption(
            f"Connection status: {st.session_state.ai_status} · Key storage: {api_key_source(provider)}"
        )
        if (
            provider in SECRET_NAME_BY_PROVIDER
            and is_cloud_haru()
            and not server_secret_for_provider(provider)
        ):
            st.caption(
                f"To keep this key across Streamlit Cloud sessions, add "
                f"{SECRET_NAME_BY_PROVIDER[provider]} in the app's Secrets settings."
            )


with st.expander("Developer panel"):
    st.markdown("#### HARU face")
    mood_options = ["IDLE", "LISTENING", "THINKING", "WORKING", "HAPPY", "CONFUSED", "ALERT", "SLEEPY"]
    selected_mood = st.selectbox(
        "Preview expression",
        mood_options,
        index=mood_options.index(st.session_state.mood),
    )
    if st.button("Apply mood"):
        st.session_state.mood = selected_mood
        st.rerun()

    if st.session_state.history:
        st.caption("Recent commands")
        for q, a in reversed(st.session_state.history[-5:]):
            st.write(f"**You:** {q}")
            st.write(f"**HARU:** {a}")

st.markdown("<div class=\"footer\">HARU Lab v2.4 • line-art mascot + quiet theme + communication tracker</div>", unsafe_allow_html=True)
