from datetime import datetime
import re

import streamlit as st

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

DEFAULTS = {
    "mood": "IDLE",
    "message": "Hello. I'm HARU.",
    "history": [],
    "news_region": "Philippines",
    "news_last_seen": 0.0,
    "news_refresh_nonce": 0,
    "explicit_interests": {},
    "ai_mode": "Off",
    "ai_provider": "Disabled",
    "ai_model": "",
    "ai_endpoint": "",
    "ai_api_key": "",
    "ai_fallback": False,
    "ai_status": "Not tested",
    "ai_connection_state": "OFF",
    "ai_connection_message": "AI runtime is disabled.",
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


def local_tool_result(command: str):
    clean = command.strip()
    low = clean.lower()

    if not clean:
        return "CONFUSED", "Type or say a command first."

    if low in {"hi", "hello", "hey", "haru", "hello haru", "good morning", "good afternoon", "good evening"}:
        return "HAPPY", "Ready. What can I help you with?"

    if "what time" in low or "current time" in low:
        return "HAPPY", datetime.now().strftime("%I:%M %p").lstrip("0")

    if "what date" in low or low == "today":
        return "HAPPY", datetime.now().strftime("%A, %B %d, %Y").replace(" 0", " ")

    if low in {"news", "latest news", "brief me", "news briefing"}:
        return "HAPPY", (
            "Live news is available in HARU's News tab. "
            "Use that feed for current local, international, and interest-aware headlines."
        )

    if low in {"help", "commands", "what can you do"}:
        return (
            "HAPPY",
            "HARU can answer through the connected AI brain and also use local tools for time/date, "
            "simple calculations, and the live News tab.",
        )

    m = re.fullmatch(
        r"\s*(?:calculate|compute|what is)?\s*(-?\d+(?:\.\d+)?)\s*([+\-*/x×])\s*(-?\d+(?:\.\d+)?)\s*\??\s*",
        clean,
        re.IGNORECASE,
    )
    if m:
        a = float(m.group(1))
        b = float(m.group(3))
        op = m.group(2).lower()
        if op == "+":
            value = a + b
        elif op == "-":
            value = a - b
        elif op in {"*", "x", "×"}:
            value = a * b
        else:
            if b == 0:
                return "CONFUSED", "I can't divide by zero."
            value = a / b

        shown = str(int(value)) if value.is_integer() else f"{value:.4f}".rstrip("0").rstrip(".")
        return "HAPPY", f"{m.group(1)} {m.group(2)} {m.group(3)} = {shown}"

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

    tool_result = local_tool_result(clean)

    if ai_is_active():
        config = current_ai_config()

        recent_history = st.session_state.get("history", [])[-4:]
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
                "\n\nHARU LOCAL TOOL RESULT (authoritative for this request):\n"
                f"{tool_text}\n"
                "Use this result rather than inventing or recalculating it."
            )

        prompt = clean
        if history_text:
            prompt = f"Recent HARU conversation:\n{history_text}\n\nCurrent user request:\n{clean}"
        prompt += tool_context

        system_prompt = (
            "You are HARU, the user's primary phone assistant. The currently selected AI model is HARU's brain, "
            "so answer the user's query or address the task directly as HARU. Be concise, practical, warm, and factual. "
            "Do not describe yourself as a fallback model or separate provider. "
            "HARU has deterministic local tools for exact time/date, calculations, and a live News tab. "
            "When a HARU LOCAL TOOL RESULT is supplied, treat it as authoritative. "
            "Do not claim that you completed phone actions, sent messages, changed settings, or accessed live information "
            "unless HARU actually provides that tool/result."
        )

        try:
            reply = ask_ai(config, prompt, system_prompt)
            return "HAPPY", reply
        except AiRuntimeError as exc:
            st.session_state.ai_connection_state = "FAILED"
            st.session_state.ai_connection_message = f"AI request failed: {exc}"
            st.session_state.ai_status = f"AI request failed: {exc}"

            if tool_result is not None:
                mood, tool_text = tool_result
                return mood, f"{tool_text}\n\nAI connection failed, so I used my local tool."
            return "CONFUSED", f"My AI connection failed: {exc}"

    if tool_result is not None:
        return tool_result

    return (
        "CONFUSED",
        "Connect and apply an AI model in AI selector so it can become HARU's primary brain for general queries and tasks.",
    )


def draft_ai_config() -> AiConfig:
    return AiConfig(
        mode=st.session_state.get("ai_mode", "Off"),
        provider=st.session_state.get("ai_provider", "Disabled"),
        model=st.session_state.get("ai_model", ""),
        endpoint=st.session_state.get("ai_endpoint", ""),
        api_key=st.session_state.get("ai_api_key", ""),
        timeout_s=25,
    )


def ai_config_signature(config: AiConfig) -> str:
    # API key intentionally excluded from display but included in the signature
    # so replacing a key requires Apply again.
    return "|".join(
        [
            config.mode,
            config.provider,
            config.model.strip(),
            config.endpoint.strip(),
            config.api_key,
        ]
    )


def current_ai_config() -> AiConfig:
    if st.session_state.get("ai_applied_signature"):
        return AiConfig(
            mode=st.session_state.get("ai_applied_mode", "Off"),
            provider=st.session_state.get("ai_applied_provider", "Disabled"),
            model=st.session_state.get("ai_applied_model", ""),
            endpoint=st.session_state.get("ai_applied_endpoint", ""),
            api_key=st.session_state.get("ai_applied_api_key", ""),
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
        "FAILED": ("#c62828", "FAILED"),
    }
    color, label = palette.get(state, ("#7a7f87", state))
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
            <div style="font-weight:700; font-size:.9rem;">{label}</div>
            <div style="font-size:.82rem; opacity:.72;">{message}</div>
        </div>
    </div>
    """


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


def render_news_items(items, section_key: str, limit: int = 6):
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
      .block-container { max-width: 760px; padding-top: 1.5rem; }
      .haru-title { text-align:center; font-size:2.2rem; font-weight:800; margin-bottom:.1rem; }
      .haru-sub { text-align:center; opacity:.62; margin-bottom:1rem; }
      .haru-wrap { display:flex; justify-content:center; margin: 1rem 0 1.2rem; }
      .haru-face {
          width:220px; height:220px; border:8px solid;
          border-radius:50%; display:flex; flex-direction:column;
          align-items:center; justify-content:center;
          box-shadow:0 8px 30px rgba(0,0,0,.08);
          animation: breathe 2.2s ease-in-out infinite alternate;
      }
      .eyes { font-size:2.2rem; font-weight:800; letter-spacing:.4rem; line-height:1; }
      .mouth { font-size:2.6rem; margin-top:1rem; line-height:1; }
      @keyframes breathe { from { transform:scale(.985); } to { transform:scale(1.015); } }
      .status { text-align:center; font-size:.8rem; font-weight:700; letter-spacing:.12rem; opacity:.65; margin-top:.25rem; }
      .reply { padding:1rem 1.1rem; border-radius:16px; background:rgba(127,127,127,.08); margin-bottom:1rem; font-size:1.05rem; }
      .footer { text-align:center; opacity:.5; font-size:.78rem; margin-top:1rem; }
    </style>
    """,
    unsafe_allow_html=True,
)

st.markdown('<div class="haru-title">HARU</div>', unsafe_allow_html=True)
st.markdown('<div class="haru-sub">Human Assistance & Responsive Utility</div>', unsafe_allow_html=True)

assistant_tab, news_tab = st.tabs(["Assistant", "News"])

with assistant_tab:
    st.markdown(face_html(st.session_state.mood), unsafe_allow_html=True)
    st.markdown(f'<div class="status">{st.session_state.mood}</div>', unsafe_allow_html=True)
    st.markdown(f'<div class="reply">{st.session_state.message}</div>', unsafe_allow_html=True)

    if ai_is_active():
        st.caption(
            f"🟢 HARU brain: {st.session_state.ai_applied_provider} · "
            f"{st.session_state.ai_applied_model}"
        )
    else:
        st.caption("⚪ HARU brain: local tools only — connect a model in AI selector")

    with st.form("haru_command", clear_on_submit=True):
        command = st.text_input("Ask HARU", placeholder="Try: latest news or calculate 22.2 * 60")
        c1, c2 = st.columns([3, 1])
        with c1:
            sent = st.form_submit_button("Send", use_container_width=True)
        with c2:
            help_clicked = st.form_submit_button("Help", use_container_width=True)

    if help_clicked:
        command = "help"
        sent = True

    if sent:
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
    render_news_items(local_items, "local", 6)

    st.markdown("### 🌍 International")
    render_news_items(world_items, "world", 6)

    st.markdown("### 🧭 General + your interests")
    if interests:
        st.caption("Based only on your HARU activity and topics you selected: " + ", ".join(i.replace("_", " ") for i in interests))
        render_news_items(personalized_items, "personal", 6)
    else:
        st.caption("Use HARU normally or choose topics in News settings. General/local coverage will still remain visible.")
        general = deduplicate(philippines_headlines(6) + world_items[:4]) if not local_items else deduplicate(local_items[:3] + world_items[:3])
        render_news_items(general, "general", 6)

with st.expander("AI selector"):
    st.markdown("#### AI runtime")
    st.caption(
        "Apply a model to make it HARU's primary brain for queries and tasks. "
        "HARU's local functions remain available as deterministic tools."
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
                st.session_state.ai_provider = provider_options[0]
            st.caption(
                "In hosted Streamlit, localhost means the Streamlit server—not your phone or home PC. "
                "For Ollama on your own machine, use HARU Lab locally or expose a secured reachable endpoint. "
                "The Android APK will later support true phone-local runtimes."
            )
        else:
            provider_options = [
                "OpenAI API",
                "Google Gemini API",
                "Anthropic Claude API",
                "Cloud OpenAI-compatible",
            ]
            if st.session_state.ai_provider not in provider_options:
                st.session_state.ai_provider = provider_options[0]

        provider = st.selectbox(
            "Provider",
            provider_options,
            index=provider_options.index(st.session_state.ai_provider),
        )
        st.session_state.ai_provider = provider

        model_catalogs = {
            "OpenAI API": {
                "GPT-5.6 Sol — flagship reasoning/coding": "gpt-5.6-sol",
                "GPT-5.6 Terra — balanced intelligence/cost": "gpt-5.6-terra",
                "GPT-5.6 Luna — low-cost/high-volume": "gpt-5.6-luna",
            },
            "Google Gemini API": {
                "Gemini 3.8 Flash — newest stable Flash": "gemini-3.8-flash",
                "Gemini 3.7 Flash — previous stable": "gemini-3.7-flash",
                "Gemini 3.6 Flash — balanced stable": "gemini-3.6-flash",
                "Gemini 3.5 Flash — stable general model": "gemini-3.5-flash",
                "Gemini 3.5 Flash-Lite — low-cost/high-throughput": "gemini-3.5-flash-lite",
                "Gemini 3.1 Flash-Lite — very low-cost stable": "gemini-3.1-flash-lite",
                "Gemini 3.1 Pro Preview — higher capability preview": "gemini-3.1-pro-preview",
            },
            "Anthropic Claude API": {
                "Claude Fable 5 — newest high-capability model": "claude-fable-5",
                "Claude Opus 5 — advanced reasoning": "claude-opus-5",
                "Claude Sonnet 5 — balanced general model": "claude-sonnet-5",
                "Claude Haiku 4.5 — fast/low-cost": "claude-haiku-4-5-20251001",
            },
            "Android on-device (APK only)": {
                "Phone local runtime — model selected in Android": "phone-local",
            },
        }

        default_endpoints = {
            "Ollama": "http://localhost:11434",
            "Local OpenAI-compatible": "http://localhost:1234",
            "Cloud OpenAI-compatible": "https://example.com",
        }

        if provider in default_endpoints:
            st.session_state.ai_endpoint = st.text_input(
                "Endpoint",
                value=st.session_state.ai_endpoint or default_endpoints[provider],
                help="Use the base URL only. HARU adds the provider API path automatically.",
            )

        if provider == "Ollama":
            if st.button("Discover installed Ollama models", use_container_width=True):
                try:
                    names = list_ollama_models(st.session_state.ai_endpoint)
                    st.session_state["ollama_models"] = names
                    st.session_state.ai_status = f"Found {len(names)} Ollama model(s)." if names else "No Ollama models found."
                except AiRuntimeError as exc:
                    st.session_state["ollama_models"] = []
                    st.session_state.ai_status = f"Ollama discovery failed: {exc}"

            discovered = st.session_state.get("ollama_models", [])
            options = discovered + ["Custom model…"] if discovered else ["Custom model…"]
            current_label = st.session_state.ai_model if st.session_state.ai_model in discovered else "Custom model…"
            selected_model = st.selectbox(
                "Model type",
                options,
                index=options.index(current_label),
            )
            if selected_model == "Custom model…":
                st.session_state.ai_model = st.text_input(
                    "Custom Ollama model",
                    value=st.session_state.ai_model if st.session_state.ai_model not in discovered else "",
                    placeholder="e.g. qwen3:4b",
                )
            else:
                st.session_state.ai_model = selected_model

        elif provider in {"Local OpenAI-compatible", "Cloud OpenAI-compatible"}:
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

        if provider in {"OpenAI API", "Google Gemini API", "Anthropic Claude API", "Cloud OpenAI-compatible"}:
            st.session_state.ai_api_key = st.text_input(
                "API key",
                value=st.session_state.ai_api_key,
                type="password",
                help="Held only in the current Streamlit session. Do not commit API keys to GitHub.",
            )
        else:
            st.session_state.ai_api_key = ""

        st.info(
            "When this connection is applied successfully, the selected model becomes HARU's primary brain. "
            "Local skills remain available as tools for exact time/date, calculations, and news routing."
        )

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
                    st.session_state.ai_applied_api_key = candidate.api_key
                    st.session_state.ai_applied_signature = ai_config_signature(candidate)
                    st.session_state.ai_connection_state = "CONNECTED"
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
            if st.button("Clear key", use_container_width=True):
                st.session_state.ai_api_key = ""
                st.session_state.ai_applied_api_key = ""
                st.session_state.ai_applied_signature = ""
                st.session_state.ai_connection_state = "UNTESTED"
                st.session_state.ai_connection_message = "API key cleared. Apply a configuration again."
                st.session_state.ai_status = "API key cleared for this session."
                st.rerun()

        st.caption(f"Diagnostic response: {st.session_state.ai_status}")


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

st.markdown('<div class="footer">HARU Lab v0.6 • connected AI becomes HARU's primary brain</div>', unsafe_allow_html=True)
