from datetime import datetime
import re

import streamlit as st

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
}
for key, value in DEFAULTS.items():
    if key not in st.session_state:
        st.session_state[key] = value.copy() if isinstance(value, (list, dict)) else value


def route_command(command: str):
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
        return "HAPPY", "Open the News tab for your local-first briefing."

    if low in {"help", "commands", "what can you do"}:
        return (
            "HAPPY",
            "I can tell the time/date, do simple calculations, show a local-first news briefing, "
            "and respond to basic greetings. Notes, reminders, and connected skills are next.",
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

    return "CONFUSED", "I don't have a local skill for that yet. Try “help”."


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

with st.expander("Developer panel"):
    mood_options = ["IDLE", "LISTENING", "THINKING", "WORKING", "HAPPY", "CONFUSED", "ALERT", "SLEEPY"]
    selected_mood = st.selectbox("Preview expression", mood_options, index=mood_options.index(st.session_state.mood))
    if st.button("Apply mood"):
        st.session_state.mood = selected_mood
        st.rerun()

    if st.session_state.history:
        st.caption("Recent commands")
        for q, a in reversed(st.session_state.history[-5:]):
            st.write(f"**You:** {q}")
            st.write(f"**HARU:** {a}")

st.markdown('<div class="footer">HARU Lab v0.2 • local-first assistant + news briefing</div>', unsafe_allow_html=True)
