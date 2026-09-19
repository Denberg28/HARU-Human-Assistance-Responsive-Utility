from datetime import datetime, timezone
import ast
import hashlib
import html
import operator
import os
import re

import streamlit as st
import streamlit.components.v1 as components
from streamlit_geolocation import streamlit_geolocation

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
    pull_ollama_model,
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
from hazard_service import (
    HazardEvent,
    fetch_hazard_bundle,
)
from location_share import (
    decode_location_share,
    encode_location_share,
    purge_expired_locations,
)
from companion_store import (
    CompanionStore,
    due_reminders,
    new_reminder,
    parse_relative_reminder,
    upcoming_reminders,
    utc_now_ts,
)

st.set_page_config(
    page_title="HARU",
    page_icon="😺",
    layout="centered",
    initial_sidebar_state="collapsed",
)

# Optional persistent signing key for cross-session trusted-location codes.
if not os.environ.get("HARU_LOCATION_SHARE_SECRET", "").strip():
    try:
        location_secret = str(
            st.secrets.get("HARU_LOCATION_SHARE_SECRET", "")
        ).strip()
    except Exception:
        location_secret = ""
    if location_secret:
        os.environ["HARU_LOCATION_SHARE_SECRET"] = location_secret

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


def session_key_for_provider(provider: str) -> str:
    if provider == "Google Gemini API":
        return st.session_state.get("gemini_api_key", "")
    if provider == "OpenRouter API":
        return st.session_state.get("openrouter_api_key", "")
    return ""


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
    "pending_command": "",
    "news_region": "Philippines",
    "news_last_seen": 0.0,
    "news_refresh_nonce": 0,
    "hazard_refresh_nonce": 0,
    "trusted_locations": [],
    "location_share_code": "",
    "explicit_interests": {},
    "local_notes": [],
    "local_tasks": [],
    "local_reminders": [],
    "companion_loaded": False,
    "ai_mode": "Off",
    "ai_provider": "Disabled",
    "ai_model": "",
    "ai_endpoint": "",
    "gemini_api_key": "",
    "openrouter_api_key": "",
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
}
for key, value in DEFAULTS.items():
    if key not in st.session_state:
        st.session_state[key] = value.copy() if isinstance(value, (list, dict)) else value

COMPANION_STORE = CompanionStore()

if not st.session_state.companion_loaded:
    if not is_cloud_haru():
        persisted = COMPANION_STORE.load()
        st.session_state.local_notes = persisted["notes"]
        st.session_state.local_tasks = persisted["tasks"]
        st.session_state.local_reminders = persisted["reminders"]
    st.session_state.companion_loaded = True


def persist_companion_state() -> None:
    if is_cloud_haru():
        return
    COMPANION_STORE.save(
        {
            "notes": st.session_state.local_notes,
            "tasks": st.session_state.local_tasks,
            "reminders": st.session_state.local_reminders,
        }
    )



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
            "Open HARU's News tab for live local and international headlines.",
        )

    if low in {"hazards", "hazard", "disaster", "disaster update", "hazard update"}:
        return (
            "ALERT",
            "Open HARU's Hazard Advisories tab for current PAGASA, PHIVOLCS, and UP NOAH information.",
        )

    # Notes
    if low in {"show notes", "list notes", "my notes", "what did you remember"}:
        notes = st.session_state.local_notes
        if not notes:
            return "HAPPY", "You don't have any HARU Local notes yet."
        return "HAPPY", "Notes:\n" + "\n".join(f"{i + 1}. {note}" for i, note in enumerate(notes))

    if low in {"clear notes", "delete all notes"}:
        st.session_state.local_notes = []
        persist_companion_state()
        return "HAPPY", "All HARU Local notes cleared."

    note_match = re.match(
        r"^(?:remember that|remember|note|save note)\s+(.+)$",
        clean,
        re.IGNORECASE,
    )
    if note_match:
        note = note_match.group(1).strip()[:500]
        st.session_state.local_notes.append(note)
        st.session_state.local_notes = st.session_state.local_notes[-100:]
        persist_companion_state()
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
        persist_companion_state()
        return "HAPPY", "All HARU Local tasks cleared."

    done_match = re.match(r"^(?:done|complete|finish)\s+(?:task\s+)?(\d+)$", low)
    if done_match:
        index = int(done_match.group(1)) - 1
        tasks = st.session_state.local_tasks
        if 0 <= index < len(tasks):
            tasks[index]["done"] = True
            persist_companion_state()
            return "HAPPY", f"Completed task {index + 1}: {tasks[index]['text']}"
        return "CONFUSED", "That task number doesn't exist."

    task_match = re.match(
        r"^(?:add task|todo|to-do|add to tasks)\s+(.+)$",
        clean,
        re.IGNORECASE,
    )
    if task_match:
        task = task_match.group(1).strip()[:500]
        st.session_state.local_tasks.append({"text": task, "done": False})
        st.session_state.local_tasks = st.session_state.local_tasks[-100:]
        persist_companion_state()
        return "HAPPY", f"Added task: {task}"

    # Reminders
    if low in {"show reminders", "list reminders", "my reminders"}:
        reminders = upcoming_reminders(st.session_state.local_reminders)
        if not reminders:
            return "HAPPY", "You have no upcoming reminders."
        lines = []
        for index, item in enumerate(reminders, start=1):
            due = datetime.fromtimestamp(
                int(item["due_at"]),
                tz=timezone.utc,
            ).astimezone()
            lines.append(
                f"{index}. {item['text']} — {due.strftime('%b %d, %I:%M %p').replace(' 0', ' ')}"
            )
        return "HAPPY", "Upcoming reminders:\n" + "\n".join(lines)

    if low in {"clear reminders", "delete all reminders"}:
        st.session_state.local_reminders = []
        persist_companion_state()
        return "HAPPY", "All reminders cleared."

    reminder = parse_relative_reminder(clean)
    if reminder is not None:
        reminder_text, due_at = reminder
        st.session_state.local_reminders.append(new_reminder(reminder_text, due_at))
        st.session_state.local_reminders = st.session_state.local_reminders[-100:]
        persist_companion_state()
        due = datetime.fromtimestamp(due_at, tz=timezone.utc).astimezone()
        return (
            "HAPPY",
            f"I'll remind you to {reminder_text} at "
            f"{due.strftime('%b %d, %I:%M %p').replace(' 0', ' ')}.",
        )

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
            f"Reminders: {len(upcoming_reminders(st.session_state.local_reminders, limit=100))}. "
            "No external AI is required for these tools.",
        )

    if low in {"help", "commands", "what can you do", "local help"}:
        return (
            "HAPPY",
            "HARU Local can work offline with: time/date, safe calculations, percentages, "
            "length/mass/temperature conversions, notes, task lists, reminders, command history, status, and news routing. "
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
    if len(clean) > 12000:
        return "ALERT", "That request is too large for HARU's free-first mode. Keep it under 12,000 characters."

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
            budget = 8000
            used = 0
            for question, answer in reversed(recent_history):
                pair = (
                    f"User: {str(question)[:1500]}\n"
                    f"HARU: {str(answer)[:2500]}"
                )
                if used + len(pair) > budget:
                    break
                lines.append(pair)
                used += len(pair)
            history_text = "\n".join(reversed(lines))

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
    provider = st.session_state.get("ai_provider", "Disabled")
    session_key = session_key_for_provider(provider)

    return AiConfig(
        mode=st.session_state.get("ai_mode", "Off"),
        provider=provider,
        model=st.session_state.get("ai_model", ""),
        endpoint=st.session_state.get("ai_endpoint", ""),
        api_key=resolved_api_key(provider, session_key),
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
                session_key_for_provider(
                    st.session_state.get("ai_applied_provider", "Disabled")
                ),
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
        "antigravity-preview-09-2026": "Antigravity",
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


def normalize_haru_mood(raw: str) -> str:
    key = (raw or "").strip().upper()
    aliases = {
        "READY": "IDLE",
        "OK": "HAPPY",
        "CONNECTED": "HAPPY",
        "SUCCESS": "HAPPY",
        "LISTEN": "LISTENING",
        "PROCESSING": "WORKING",
        "BUSY": "WORKING",
        "THINK": "THINKING",
        "ERROR": "CONFUSED",
        "FAILED": "CONFUSED",
        "WARNING": "ALERT",
        "SLEEP": "SLEEPY",
    }
    allowed = {
        "IDLE", "HAPPY", "LISTENING", "THINKING",
        "CONFUSED", "ALERT", "SLEEPY", "WORKING",
    }
    mapped = aliases.get(key, key)
    return mapped if mapped in allowed else "IDLE"


def emoji_for_mood(mood: str) -> str:
    """Return HARU's cute chibi cat-style kaomoji for the current state."""
    mood = normalize_haru_mood(mood)
    return {
        "IDLE": "₍^. .^₎⟆",
        "HAPPY": "₍^ >ヮ<^₎♡",
        "LISTENING": "₍^. ̫ .^₎♫",
        "THINKING": "₍^. .^₎?",
        "WORKING": "₍^•⩊•^₎⚙",
        "CONFUSED": "₍^. .^₎՞",
        "ALERT": "₍⊙ᆺ⊙₎!",
        "SLEEPY": "₍^-.-^₎ zZ",
    }.get(mood, "₍^. .^₎⟆")


def render_haru_mascot(mood: str) -> None:
    """Render HARU as a cute, lightweight, mood-responsive chibi face."""
    face = html.escape(emoji_for_mood(mood))
    label = html.escape(normalize_haru_mood(mood).lower())
    st.markdown(
        f'<div class="haru-emoji" role="img" aria-label="HARU {label}">{face}</div>',
        unsafe_allow_html=True,
    )


def render_latest_user_entry(text: str = "") -> None:
    """Show one latest user entry as a compact right-aligned chat bubble."""
    user_text = text.strip()
    if not user_text:
        history = st.session_state.get("history", [])
        if not history:
            return
        latest = history[-1]
        if isinstance(latest, (tuple, list)) and latest:
            user_text = str(latest[0])
        else:
            user_text = str(latest)

    safe_user = html.escape(user_text).replace("\n", "<br>")
    st.markdown(
        f"""
        <div class="latest-entry-row">
          <div class="latest-entry-bubble">{safe_user}</div>
        </div>
        """,
        unsafe_allow_html=True,
    )


def render_thinking_indicator() -> None:
    """Render a lightweight animated three-dot HARU thinking indicator."""
    st.markdown(
        """
        <div class="thinking-row" role="status" aria-label="HARU is thinking">
          <span class="thinking-label">HARU</span>
          <span class="thinking-dots" aria-hidden="true">
            <span></span><span></span><span></span>
          </span>
        </div>
        """,
        unsafe_allow_html=True,
    )


def render_assistant_response(reply: str) -> None:
    """Render model output as compact, readable Markdown."""
    text = (reply or "").strip()
    if not text:
        return
    st.markdown('<div class="response-label">HARU</div>', unsafe_allow_html=True)
    with st.container(key="assistant_response"):
        st.markdown(text)


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
    """Render compact news entries with one lightweight action link."""
    if not items:
        st.caption("No stories available right now.")
        return

    for item in items[:limit]:
        safe_title = html.escape(str(item.title))
        safe_source = html.escape(str(item.source or "News"))
        time_text = html.escape(friendly_time(item))

        if item.link.startswith(("https://", "http://")):
            safe_link = html.escape(item.link, quote=True)
            st.markdown(
                f"""
                <div class="news-entry">
                  <div class="news-title">{safe_title}</div>
                  <div class="news-meta">
                    {safe_source} · {time_text} ·
                    <a href="{safe_link}" target="_blank" rel="noopener noreferrer">Open ↗</a>
                  </div>
                </div>
                """,
                unsafe_allow_html=True,
            )
        else:
            st.markdown(
                f"""
                <div class="news-entry">
                  <div class="news-title">{safe_title}</div>
                  <div class="news-meta">{safe_source} · {time_text}</div>
                </div>
                """,
                unsafe_allow_html=True,
            )




@st.cache_data(ttl=300, show_spinner=False)
def fetch_hazards_cached(nonce: int):
    del nonce
    return fetch_hazard_bundle()


def render_hazard_events(events: list[HazardEvent]) -> None:
    if not events:
        st.info("No current item could be retrieved from this official source.")
        return

    for event in events:
        severity_icon = {
            "warning": "🔴",
            "watch": "🟠",
            "info": "🔵",
        }.get(event.severity, "🔵")
        st.markdown(f"**{severity_icon} {event.title}**")
        meta = event.source
        if event.issued:
            meta += f" · {event.issued}"
        st.caption(meta)
        if event.summary:
            st.write(event.summary)
        if event.url.startswith(("https://", "http://")):
            st.link_button("Official source", event.url, use_container_width=False)
        st.divider()


st.markdown(
    """
    <style>
      .block-container {
          max-width: 940px;
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
      .haru-emoji {
          display:flex;
          justify-content:center;
          align-items:center;
          width:100%;
          min-height:118px;
          margin:.15rem 0 .05rem;
          padding:.15rem .35rem;
          font-size:3.1rem;
          font-weight:700;
          line-height:1.15;
          letter-spacing:.01em;
          text-align:center;
          white-space:nowrap;
          user-select:none;
          font-family:"Segoe UI Symbol","Noto Sans Symbols 2","Arial Unicode MS",sans-serif;
          animation:haru-emoji-breathe 2.6s ease-in-out infinite alternate;
      }
      @keyframes haru-emoji-breathe {
          from { transform:translateY(0) scale(.98); }
          to { transform:translateY(-3px) scale(1.02); }
      }
      .status {
          text-align:center;
          font-size:.72rem;
          font-weight:700;
          letter-spacing:.11rem;
          opacity:.62;
          margin:.05rem 0 .25rem 0;
      }
      .response-label {
          margin:.1rem 0 .28rem;
          font-size:.72rem;
          font-weight:700;
          letter-spacing:.08em;
          opacity:.52;
      }
      .thinking-row {
          display:flex;
          align-items:center;
          gap:.5rem;
          min-height:2.25rem;
          margin:.1rem 0 .55rem;
      }
      .thinking-label {
          font-size:.72rem;
          font-weight:700;
          letter-spacing:.08em;
          opacity:.52;
      }
      .thinking-dots {
          display:inline-flex;
          align-items:center;
          gap:.28rem;
      }
      .thinking-dots span {
          width:.42rem;
          height:.42rem;
          border-radius:50%;
          background:currentColor;
          opacity:.28;
          animation:haru-dot 1.15s infinite ease-in-out;
      }
      .thinking-dots span:nth-child(2) { animation-delay:.16s; }
      .thinking-dots span:nth-child(3) { animation-delay:.32s; }
      @keyframes haru-dot {
          0%, 70%, 100% { transform:translateY(0); opacity:.24; }
          35% { transform:translateY(-4px); opacity:.8; }
      }
      .st-key-assistant_response {
          font-size:.94rem;
      }
      .st-key-assistant_response div[data-testid="stMarkdownContainer"] {
          font-size:.94rem;
          line-height:1.48;
      }
      .st-key-assistant_response div[data-testid="stMarkdownContainer"] p {
          font-size:.94rem;
          line-height:1.5;
          margin:.18rem 0 .62rem;
      }
      .st-key-assistant_response div[data-testid="stMarkdownContainer"] h1,
      .st-key-assistant_response div[data-testid="stMarkdownContainer"] h2,
      .st-key-assistant_response div[data-testid="stMarkdownContainer"] h3,
      .st-key-assistant_response div[data-testid="stMarkdownContainer"] h4,
      .st-key-assistant_response div[data-testid="stMarkdownContainer"] h5,
      .st-key-assistant_response div[data-testid="stMarkdownContainer"] h6 {
          font-size:1rem !important;
          line-height:1.35 !important;
          font-weight:700 !important;
          margin:.85rem 0 .38rem !important;
      }
      .st-key-assistant_response div[data-testid="stMarkdownContainer"] ul,
      .st-key-assistant_response div[data-testid="stMarkdownContainer"] ol {
          margin:.22rem 0 .62rem;
          padding-left:1.35rem;
      }
      .st-key-assistant_response div[data-testid="stMarkdownContainer"] li {
          font-size:.94rem;
          line-height:1.48;
          margin-bottom:.16rem;
      }
      .st-key-assistant_response div[data-testid="stMarkdownContainer"] pre {
          border-radius:10px;
          font-size:.86rem;
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
      .latest-entry-row {
          display:flex;
          justify-content:flex-end;
          width:100%;
          margin:.25rem 0 .5rem;
      }
      .latest-entry-bubble {
          max-width:82%;
          padding:.68rem .9rem;
          border-radius:18px 18px 5px 18px;
          background:rgba(127,127,127,.12);
          border:1px solid rgba(127,127,127,.16);
          font-size:.94rem;
          line-height:1.45;
          overflow-wrap:anywhere;
      }
      .news-entry {
          padding:.35rem 0 .45rem;
          border-bottom:1px solid rgba(127,127,127,.12);
      }
      .news-title {
          font-size:.9rem;
          font-weight:600;
          line-height:1.3;
          margin-bottom:.08rem;
      }
      .news-meta {
          font-size:.72rem;
          line-height:1.25;
          opacity:.58;
      }
      .news-meta a {
          color:inherit;
          text-decoration:none;
          font-weight:600;
      }
      .news-meta a:hover {
          text-decoration:underline;
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
          .haru-emoji { min-height:112px; font-size:2.55rem; }
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

assistant_tab, news_tab, hazard_tab, map_tab = st.tabs(["Assistant", "News", "Hazard Advisories", "Map"])

with assistant_tab:
    pending_command = str(st.session_state.get("pending_command", "")).strip()
    display_mood = "THINKING" if pending_command else st.session_state.mood

    render_haru_mascot(display_mood)
    st.markdown(f'<div class="status">{display_mood}</div>', unsafe_allow_html=True)

    due_now = due_reminders(st.session_state.local_reminders)
    if due_now:
        for item in due_now[:3]:
            st.warning(f"⏰ {item['text']}")
            item["delivered"] = True
        persist_companion_state()

    open_tasks = [task for task in st.session_state.local_tasks if not task.get("done")]
    upcoming = upcoming_reminders(st.session_state.local_reminders, limit=2)
    if open_tasks or upcoming:
        with st.container(border=True):
            st.markdown("**Today**")
            if open_tasks:
                preview = " · ".join(task["text"] for task in open_tasks[:2])
                st.caption(f"Tasks: {preview}" + (" …" if len(open_tasks) > 2 else ""))
            for item in upcoming:
                due = datetime.fromtimestamp(
                    int(item["due_at"]),
                    tz=timezone.utc,
                ).astimezone()
                st.caption(
                    "⏰ "
                    + item["text"]
                    + " · "
                    + due.strftime("%I:%M %p").lstrip("0")
                )

    if pending_command:
        render_latest_user_entry(pending_command)
        render_thinking_indicator()
    else:
        render_latest_user_entry()
        render_assistant_response(str(st.session_state.message))

    command = None
    if not pending_command:
        with st.container(border=True):
            st.caption("Ask HARU · Enter to send · Shift+Enter for a new line")
            command = st.chat_input(
                "Type a question or task…",
                key="haru_chat_input",
            )

    if command:
        st.session_state.pending_command = command.strip()
        st.session_state.mood = "THINKING"
        st.rerun()

    if pending_command:
        try:
            mood, reply = route_command(pending_command)
        except Exception:
            mood = "CONFUSED"
            reply = "HARU hit an unexpected runtime error. Please try the request again."
        st.session_state.mood = mood
        st.session_state.message = reply
        history_reply = (
            reply
            if len(reply) <= 8000
            else reply[:8000] + "\n…"
        )
        st.session_state.history.append((pending_command, history_reply))
        if len(st.session_state.history) > 100:
            st.session_state.history = st.session_state.history[-100:]
        st.session_state.pending_command = ""
        st.rerun()

with news_tab:
    scores = infer_interests(st.session_state.history, st.session_state.explicit_interests)
    interests = top_interests(scores, 2)

    top_left, top_right = st.columns([3, 1])
    with top_left:
        st.subheader("HARU News")
        st.caption("Local and international headlines side by side.")
    with top_right:
        if st.button("Refresh news", use_container_width=True):
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
            cleaned_region = re.sub(r"\s+", " ", region.strip())[:80]
            st.session_state.news_region = cleaned_region or "Philippines"

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
            "Optional interests",
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
        st.caption(f"🔔 {unread} new stories since your last visit.")
    st.session_state.news_last_seen = max(st.session_state.news_last_seen, newest_ts)

    if news_errors:
        with st.expander("Feed status"):
            for err in news_errors:
                st.caption(err)

    local_col, world_col = st.columns(2, gap="large")
    with local_col:
        st.markdown("### 🇵🇭 Local")
        st.caption(f"Area: {st.session_state.news_region}")
        render_news_items(local_items, 7)

    with world_col:
        st.markdown("### 🌍 International")
        st.caption("Major world headlines")
        render_news_items(world_items, 7)

    if interests and personalized_items:
        with st.expander("For you"):
            st.caption(
                "Additional stories based only on HARU activity and selected topics: "
                + ", ".join(i.replace("_", " ") for i in interests)
            )
            render_news_items(personalized_items, 5)


with hazard_tab:
    header_left, header_right = st.columns([3, 1])
    with header_left:
        st.subheader("Hazard Advisories")
        st.caption("Latest official Philippine hazard information.")
    with header_right:
        if st.button("Refresh hazards", use_container_width=True):
            st.session_state.hazard_refresh_nonce += 1
            st.cache_data.clear()
            st.rerun()

    st.markdown("### 🛰️ Live PAGASA PANaHON")
    with st.container(border=True):
        components.iframe(
            "https://www.panahon.gov.ph/",
            height=500,
            scrolling=True,
        )
        st.link_button(
            "Open full-screen map",
            "https://www.panahon.gov.ph/",
            use_container_width=True,
        )

    with st.spinner("Checking official advisories…"):
        pagasa_items, phivolcs_items, noah_items, hazard_errors = fetch_hazards_cached(
            st.session_state.hazard_refresh_nonce
        )

    pagasa_col, right_col = st.columns(2, gap="large")

    with pagasa_col:
        st.markdown("### 🌧️ PAGASA")
        render_hazard_events(pagasa_items[:1])

    with right_col:
        st.markdown("### 🌋 PHIVOLCS")
        render_hazard_events(phivolcs_items[:3])

        if noah_items:
            event = noah_items[0]
            st.markdown("### 🗺️ UP NOAH")
            st.caption(event.summary)
            st.link_button(
                "Open local hazard map",
                event.url,
                use_container_width=False,
            )

    st.caption(
        "Situational awareness only. Follow official agency and local-government emergency instructions."
    )

with map_tab:
    st.subheader("Trusted Locations")
    st.caption(
        "Share a phone location only with permission. HARU uses temporary GPS snapshots, "
        "not background tracking. Share codes are signed and expire automatically."
    )

    st.session_state.trusted_locations = purge_expired_locations(
        st.session_state.trusted_locations
    )

    share_col, receive_col = st.columns(2, gap="large")

    with share_col:
        st.markdown("### Share my location")
        share_name = st.text_input(
            "Name",
            value="",
            placeholder="e.g. Mom",
            key="trusted_share_name",
        )
        expires_label = st.selectbox(
            "Share expires after",
            ["15 minutes", "1 hour", "4 hours", "24 hours"],
            index=1,
        )
        expiry_hours = {
            "15 minutes": 0.25,
            "1 hour": 1.0,
            "4 hours": 4.0,
            "24 hours": 24.0,
        }[expires_label]

        st.caption("Tap below and allow location access on this phone.")
        if is_cloud_haru() and not os.environ.get("HARU_LOCATION_SHARE_SECRET", "").strip():
            st.caption("Temporary signing key active: existing share codes may stop working after an app restart.")
        location = streamlit_geolocation()

        if isinstance(location, dict) and location.get("latitude") is not None:
            latitude = float(location["latitude"])
            longitude = float(location["longitude"])
            accuracy = location.get("accuracy")
            if st.button(
                "Create share code",
                type="primary",
                use_container_width=True,
                key="create_location_share",
            ):
                st.session_state.location_share_code = encode_location_share(
                    share_name or "Loved one",
                    latitude,
                    longitude,
                    float(accuracy) if accuracy is not None else None,
                    expiry_hours,
                )

            if st.session_state.location_share_code:
                st.text_area(
                    "Send this code to someone you trust",
                    value=st.session_state.location_share_code,
                    height=90,
                    key="location_share_code_display",
                    disabled=True,
                )
                accuracy_text = (
                    f" · ±{float(accuracy):.0f} m"
                    if accuracy is not None
                    else ""
                )
                st.caption(
                    f"Location captured{accuracy_text}. This is a snapshot, not continuous tracking."
                )
        else:
            st.caption("No phone location has been shared yet.")

    with receive_col:
        st.markdown("### Find a loved one")
        st.caption(
            "Ask them to open HARU, allow GPS, and send you their temporary share code."
        )
        incoming_code = st.text_area(
            "Location share code",
            placeholder="Paste the code they sent you",
            height=90,
            key="incoming_location_code",
        )

        if st.button(
            "Add shared location",
            use_container_width=True,
            disabled=not incoming_code.strip(),
            key="add_trusted_location",
        ):
            try:
                shared = decode_location_share(incoming_code)
                existing = [
                    item for item in st.session_state.trusted_locations
                    if not (
                        item.get("name") == shared["name"]
                        and abs(float(item.get("lat", 0)) - shared["lat"]) < 1e-6
                        and abs(float(item.get("lon", 0)) - shared["lon"]) < 1e-6
                    )
                ]
                existing.append(shared)
                st.session_state.trusted_locations = existing[-20:]
                st.success(f"Added {shared['name']}.")
                st.rerun()
            except ValueError as exc:
                st.error(str(exc))

        if st.session_state.trusted_locations:
            if st.button(
                "Clear shared locations",
                use_container_width=True,
                key="clear_trusted_locations",
            ):
                st.session_state.trusted_locations = []
                st.rerun()

    if st.session_state.trusted_locations:
        st.markdown("### Shared map")
        map_rows = {
            "lat": [float(item["lat"]) for item in st.session_state.trusted_locations],
            "lon": [float(item["lon"]) for item in st.session_state.trusted_locations],
        }
        st.map(map_rows, zoom=11, use_container_width=True)

        for item in st.session_state.trusted_locations:
            expiry = datetime.fromtimestamp(
                int(item["expires"]),
                tz=timezone.utc,
            ).astimezone()
            accuracy = item.get("accuracy_m")
            detail = f"expires {expiry.strftime('%I:%M %p')}"
            if accuracy is not None:
                detail += f" · ±{float(accuracy):.0f} m"
            st.caption(f"📍 {item['name']} · {detail}")
    else:
        st.info("No active shared locations. Add a trusted person's share code to show them here.")


with assistant_tab:
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
            st.session_state.gemini_api_key = ""
            st.session_state.openrouter_api_key = ""
            st.info("AI is disabled. HARU uses only local deterministic skills.")
        else:
            if ai_mode == "Local":
                if is_cloud_haru():
                    provider_options = ["Ollama"]
                    st.session_state.ai_provider = "Ollama"
                    st.caption(
                        "Local endpoints are disabled on Streamlit Cloud. Run HARU locally to use Ollama or another local server."
                    )
                else:
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
                    "Google Gemini API",
                    "OpenRouter API",
                ]
                if st.session_state.ai_provider not in provider_options:
                    st.session_state.ai_provider = "Google Gemini API"

            provider_labels = {
                "Ollama": "Ollama on this PC — recommended",
                "Local OpenAI-compatible": "Other local OpenAI-compatible server",
                "Android on-device (APK only)": "Android on-device model",
                "Google Gemini API": "Google Gemini API — default",
                "OpenRouter API": "OpenRouter API — free models only",
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
                    "Antigravity — default agent": "antigravity-preview-09-2026",
                    "Gemini 3.5 Flash-Lite — long context / tools": "gemini-3.5-flash-lite",
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
                        "Local AI setup is only available in HARU Local because Streamlit Cloud cannot access your PC."
                    )
                    st.code(".\\run_haru_local.bat", language="powershell")
                    st.caption(
                        "Run the launcher on your Windows PC. HARU Local will help start Ollama and install a model."
                    )
                else:
                    st.markdown("##### Local AI setup")
                    st.caption(
                        "HARU can detect Ollama, help you choose a model, download it locally, and connect it."
                    )

                    ollama_online = False
                    try:
                        detected_models = list_ollama_models(st.session_state.ai_endpoint, timeout_s=2)
                        st.session_state["ollama_models"] = detected_models
                        ollama_online = True
                        st.success("Ollama is running on this PC.")
                    except AiRuntimeError:
                        st.warning(
                            "Ollama is not reachable yet. Install/start Ollama, then press Check again."
                        )
                        st.code("winget install Ollama.Ollama", language="powershell")
                        st.code("ollama serve", language="powershell")

                    if st.button("Check Ollama again", use_container_width=True, key="ollama_check"):
                        try:
                            names = list_ollama_models(st.session_state.ai_endpoint, timeout_s=3)
                            st.session_state["ollama_models"] = names
                            st.session_state.ai_status = f"Ollama ready · {len(names)} installed model(s)."
                        except AiRuntimeError as exc:
                            st.session_state.ai_status = f"Ollama not reachable: {exc}"
                        st.rerun()

                    if ollama_online:
                        model_presets = {
                            "Lightweight — qwen3:4b": "qwen3:4b",
                            "Balanced — qwen3:8b": "qwen3:8b",
                            "Reasoning — gemma3:12b": "gemma3:12b",
                            "Custom model name…": "",
                        }
                        preset_label = st.selectbox(
                            "Model to install",
                            list(model_presets.keys()),
                            help="Start with 4B on modest PCs, 8B for a stronger balance, or choose a custom Ollama model.",
                        )
                        model_to_pull = model_presets[preset_label]
                        if not model_to_pull:
                            model_to_pull = st.text_input(
                                "Custom Ollama model",
                                placeholder="e.g. llama3.2:3b",
                                help="Use an Ollama model name from the Ollama library.",
                            ).strip()

                        pull_col, connect_col = st.columns(2)
                        with pull_col:
                            if st.button(
                                "Download model",
                                use_container_width=True,
                                disabled=not bool(model_to_pull),
                            ):
                                with st.spinner(f"Downloading {model_to_pull}… This can take several minutes."):
                                    try:
                                        pull_ollama_model(
                                            st.session_state.ai_endpoint,
                                            model_to_pull,
                                        )
                                        names = list_ollama_models(st.session_state.ai_endpoint)
                                        st.session_state["ollama_models"] = names
                                        st.session_state.ai_model = model_to_pull
                                        st.session_state.ai_status = f"Installed {model_to_pull}."
                                        st.success(f"{model_to_pull} is installed.")
                                    except AiRuntimeError as exc:
                                        st.error(f"Download failed: {exc}")

                        with connect_col:
                            if st.button(
                                "Auto-connect best installed",
                                type="primary",
                                use_container_width=True,
                            ):
                                try:
                                    names = list_ollama_models(st.session_state.ai_endpoint)
                                    if not names:
                                        st.session_state.ai_status = "Install a model first."
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
                                    st.session_state.ai_applied_signature = ai_config_signature(candidate)
                                    st.session_state.ai_connection_state = "CONNECTED"
                                    st.session_state.ai_runtime_degraded = False
                                    st.session_state.ai_runtime_degraded_reason = ""
                                    st.session_state.ai_connection_message = f"Ollama connected. {chosen} is now HARU."
                                    st.session_state.ai_status = result
                                    st.rerun()
                                except AiRuntimeError as exc:
                                    st.session_state.ai_connection_state = "FAILED"
                                    st.session_state.ai_connection_message = str(exc)
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
                    st.session_state.openrouter_api_key = ""
                elif openrouter_saved_key:
                    st.success("OpenRouter key loaded securely from this PC's credential store.")
                    st.session_state.openrouter_api_key = ""
                elif is_cloud_haru():
                    st.session_state.openrouter_api_key = ""
                    st.warning(
                        "OpenRouter is locked to Streamlit Secrets on HARU Cloud. "
                        "Set OPENROUTER_API_KEY in the app's Secrets settings."
                    )
                else:
                    st.session_state.openrouter_api_key = st.text_input(
                        "OpenRouter API key",
                        value=st.session_state.openrouter_api_key,
                        type="password",
                        placeholder="sk-or-v1-…",
                        help="A successful local connection is saved to Windows Credential Manager.",
                    )

                free_modes = [
                    "Free auto-router — recommended",
                    "Choose a specific free model",
                ]
                if st.session_state.get("openrouter_model_mode") not in free_modes:
                    st.session_state["openrouter_model_mode"] = free_modes[0]

                openrouter_mode = st.radio(
                    "Model access",
                    free_modes,
                    horizontal=False,
                    key="openrouter_model_mode",
                )

                if openrouter_mode == "Free auto-router — recommended":
                    st.session_state.ai_model = "openrouter/free"
                    st.success(
                        "Using OpenRouter Free Models Router. Paid models are hidden in HARU."
                    )
                    st.caption(
                        "Model ID: openrouter/free · Free-plan request limits still apply."
                    )
                else:
                    if st.button("Refresh free OpenRouter models", use_container_width=True):
                        try:
                            names = list_openai_compatible_models(
                                st.session_state.ai_endpoint,
                                resolved_api_key("OpenRouter API", st.session_state.openrouter_api_key),
                            )
                            free_names = [
                                name for name in names
                                if name == "openrouter/free" or name.endswith(":free")
                            ]
                            st.session_state["openrouter_models"] = free_names
                            st.session_state.ai_status = (
                                f"Found {len(free_names)} free OpenRouter model(s)."
                                if free_names else
                                "No specific free models were returned; use the free auto-router."
                            )
                            st.rerun()
                        except AiRuntimeError as exc:
                            st.session_state["openrouter_models"] = []
                            st.session_state.ai_status = f"OpenRouter discovery failed: {exc}"

                    discovered = st.session_state.get("openrouter_models", [])
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
                        help="HARU only exposes OpenRouter's free router and :free model variants.",
                    )
                    st.caption("Free-only OpenRouter mode. Paid models are not selectable.")

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
                            st.session_state.openrouter_api_key,
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

                if provider == "Google Gemini API":
                    labels = list(catalog.keys())
                    current_label = next(
                        (label for label, model_id in catalog.items() if model_id == st.session_state.ai_model),
                        labels[0],
                    )
                    selected_label = st.selectbox(
                        "Model type",
                        labels,
                        index=labels.index(current_label),
                        help="HARU uses Antigravity by default, with Gemini 3.5 Flash-Lite as the lightweight alternative.",
                    )
                    st.session_state.ai_model = catalog[selected_label]

                    if st.session_state.ai_model == "antigravity-preview-09-2026":
                        st.caption("Default · agent workflows · 60 RPM · 100K TPM · 100 RPD.")
                    else:
                        st.caption("Gemini 3.5 Flash-Lite · 15 RPM · 250K TPM · 500 RPD · long context / tools.")
                else:
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
                    st.session_state.gemini_api_key = ""
                elif provider_saved_key:
                    st.success(f"{provider} key loaded securely from this PC's credential store.")
                    st.session_state.gemini_api_key = ""
                elif is_cloud_haru():
                    st.session_state.gemini_api_key = ""
                    st.warning(
                        "Google AI is locked to Streamlit Secrets on HARU Cloud. "
                        "Set GEMINI_API_KEY in the app's Secrets settings."
                    )
                else:
                    st.session_state.gemini_api_key = st.text_input(
                        "API key",
                        value=st.session_state.gemini_api_key,
                        type="password",
                        help="A successful local connection is saved to Windows Credential Manager.",
                    )
            elif provider == "OpenRouter API":
                # OpenRouter renders its dedicated key field above. Preserve that
                # session value so Apply & connect can authenticate successfully.
                pass
            else:
                pass

            if provider != "Ollama":
                st.info(
                    "When applied, HARU becomes the shell for the selected model/agent. "
                    "Google Antigravity is the online default, Gemini 3.5 Flash-Lite is the lightweight alternative, "
                    "and OpenRouter exposes free models only. HARU local tools remain available for device-specific tasks."
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
                                and (
                                    st.session_state.get("gemini_api_key")
                                    if candidate.provider == "Google Gemini API"
                                    else st.session_state.get("openrouter_api_key")
                                )
                            ):
                                if save_stored_key(candidate.provider, candidate.api_key):
                                    if candidate.provider == "Google Gemini API":
                                        st.session_state.gemini_api_key = ""
                                    elif candidate.provider == "OpenRouter API":
                                        st.session_state.openrouter_api_key = ""

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
                        if provider == "Google Gemini API":
                            st.session_state.gemini_api_key = ""
                        elif provider == "OpenRouter API":
                            st.session_state.openrouter_api_key = ""
                
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
                    f"Cloud connections require {SECRET_NAME_BY_PROVIDER[provider]} in the app's Secrets settings."
                )


if os.environ.get("HARU_DEBUG", "").strip() == "1":
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

st.markdown("<div class=\"footer\">HARU Lab v4.1 • companion core</div>", unsafe_allow_html=True)
