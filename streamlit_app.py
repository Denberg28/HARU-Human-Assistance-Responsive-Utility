import math
from datetime import datetime
import re

import streamlit as st

st.set_page_config(
    page_title="HARU",
    page_icon="🙂",
    layout="centered",
    initial_sidebar_state="collapsed",
)

if "mood" not in st.session_state:
    st.session_state.mood = "IDLE"
if "message" not in st.session_state:
    st.session_state.message = "Hello. I'm HARU."
if "history" not in st.session_state:
    st.session_state.history = []


def route_command(command: str):
    clean = command.strip()
    low = clean.lower()

    if not clean:
        return "CONFUSED", "Type or say a command first."

    if low in {"hi", "hello", "hey", "haru", "hello haru", "good morning", "good afternoon", "good evening"}:
        return "HAPPY", "Ready. What can I help you with?"

    if "what time" in low or "current time" in low:
        return "HAPPY", datetime.now().strftime("%-I:%M %p")

    if "what date" in low or low == "today":
        return "HAPPY", datetime.now().strftime("%A, %B %-d, %Y")

    if low in {"help", "commands", "what can you do"}:
        return (
            "HAPPY",
            "I can tell the time/date, do simple calculations, and respond to basic greetings. "
            "Notes, reminders, phone actions, and connected skills are next.",
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
        try:
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
        except Exception:
            return "CONFUSED", "I couldn't complete that calculation."

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
        eyes = "^   ^"
        mouth = "◡"
    elif mood == "CONFUSED":
        eyes = "•   •"
        mouth = "︵"
    elif mood == "ALERT":
        eyes = "○   ○"
        mouth = "o"
    elif mood == "SLEEPY":
        eyes = "—   —"
        mouth = "ᴗ"
    elif mood == "THINKING":
        eyes = "◔   ◔"
        mouth = "—"
    elif mood == "LISTENING":
        eyes = "◉   ◉"
        mouth = "—"
    else:
        eyes = "●   ●"
        mouth = "ᴗ"

    return f"""
    <div class="haru-wrap">
        <div class="haru-face" style="border-color:{color}; color:{color};">
            <div class="eyes">{eyes}</div>
            <div class="mouth">{mouth}</div>
        </div>
    </div>
    """


st.markdown(
    """
    <style>
      .block-container { max-width: 720px; padding-top: 2rem; }
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
      .status {
          text-align:center; font-size:.8rem; font-weight:700;
          letter-spacing:.12rem; opacity:.65; margin-top:.25rem;
      }
      .reply {
          padding:1rem 1.1rem; border-radius:16px; background:rgba(127,127,127,.08);
          margin-bottom:1rem; font-size:1.05rem;
      }
      .footer { text-align:center; opacity:.5; font-size:.78rem; margin-top:1rem; }
    </style>
    """,
    unsafe_allow_html=True,
)

st.markdown('<div class="haru-title">HARU</div>', unsafe_allow_html=True)
st.markdown('<div class="haru-sub">Human Assistance & Responsive Utility</div>', unsafe_allow_html=True)
st.markdown(face_html(st.session_state.mood), unsafe_allow_html=True)
st.markdown(f'<div class="status">{st.session_state.mood}</div>', unsafe_allow_html=True)
st.markdown(f'<div class="reply">{st.session_state.message}</div>', unsafe_allow_html=True)

with st.form("haru_command", clear_on_submit=True):
    command = st.text_input("Ask HARU", placeholder="Try: calculate 22.2 * 60")
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

with st.expander("Developer panel"):
    mood_options = ["IDLE", "LISTENING", "THINKING", "WORKING", "HAPPY", "CONFUSED", "ALERT", "SLEEPY"]
    selected = st.selectbox("Preview expression", mood_options, index=mood_options.index(st.session_state.mood))
    if st.button("Apply mood"):
        st.session_state.mood = selected
        st.rerun()

    if st.session_state.history:
        st.caption("Recent commands")
        for q, a in reversed(st.session_state.history[-5:]):
            st.write(f"**You:** {q}")
            st.write(f"**HARU:** {a}")

st.markdown('<div class="footer">HARU Lab v0.1 • local-first simulator</div>', unsafe_allow_html=True)
