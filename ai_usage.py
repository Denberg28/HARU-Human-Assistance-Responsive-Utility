"""Small in-process Gemini budget gate for the Streamlit lab; no secrets are logged."""
from __future__ import annotations
import hashlib
import math
import re
import threading
import time
from contextlib import contextmanager
from datetime import datetime, timedelta, timezone
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

_LOCK = threading.Lock()
_STATES: dict[str, dict] = {}


def current_query(prompt: str) -> str:
    marker = "\n\nCurrent user request:\n"
    value = prompt.rsplit(marker, 1)[-1] if marker in prompt else prompt
    return value.split("\n\nHARU LOCAL TOOL CONTEXT:\n", 1)[0].strip()


def needs_agent(prompt: str) -> bool:
    return bool(re.search(r"\b(deep research|research thoroughly|in-depth research)\b", current_query(prompt).lower()))


def needs_url_context(prompt: str) -> bool:
    value = current_query(prompt)
    return bool(re.search(r"https://[^\s<>]+", value, re.I) and re.search(
        r"\b(read|summari[sz]e|summary|open|visit|inspect|compare|review this (?:article|page|link))\b", value.lower()))


def needs_reasoning(prompt: str) -> bool:
    value = current_query(prompt)
    return len(value) > 450 or bool(re.search(
        r"\b(solve|derive|calculate|prove|debug|audit|investigate|analy[sz]e|design|code|equation|step.by.step|detailed|thorough|long|essay|complete|full|compare)\b", value.lower()))


def needs_web_search(prompt: str, previous_prompts=()) -> bool:
    value = current_query(prompt).lower()
    if needs_agent(value) or re.search(
        r"\b(search (?:the web|online|for)|browse|google|look up|lookup|find online|check online|on the web|internet|compare sources|verify sources|cross.check|latest|breaking|currently|right now|tonight|recent|recently|live|ngayon|pinakabago|dosage|contraindications|drug interactions|medical advice|legal advice|tax law|investment advice|caap regulations|pcar)\b"
        r"|\b(current|today)\b.{0,60}\b(news|weather|forecast|price|prices|stock|availability|score|scores|schedule|events|updates|president|ceo|law|regulations)\b"
        r"|\b(news|weather|forecast|price|prices|stock|availability|score|scores|schedule|events|updates|president|ceo|law|regulations)\b.{0,60}\b(current|today)\b", value):
        return True
    if needs_url_context(value) or re.search(
        r"^(?:(?:explain|define|describe|teach)(?: me)? (?:the )?(?:basics|concept|principles|weather forecasting|price elasticity|exchange rates?|electric current|news literacy)|what (?:is|are) (?:a |an |the )?(?:news|weather|forecasting|price elasticity|exchange rate|electric current)[?.!]*$|how (?:does|do) (?:weather|forecasting|pricing))\b", value):
        return False
    if re.search(r"\bwhat time\b.{0,60}\b(flight|train|bus|event|meeting)\b|\b(news|weather|forecast|price|prices|availability|exchange rate)\b|\b(what(?:'s| is) (?:happening|going on)|what happened|any updates?|situation (?:in|at)|status of|who is (?:the )?(?:president|ceo|prime minister))\b|\b(sino|ano|anong|kumusta|kamusta)\b.{0,60}\b(balita|panahon|presyo|nangyari|nangyayari)\b", value):
        return True
    follow = r"^(?:and\b|what about\b|how about\b|tell me more\b|more details\b|why\??$|when\??$|where\??$|continue\??$|check again\b|try again\b|update me\b|is (?:that|it)\b)"
    if re.search(follow, value):
        for previous in reversed(list(previous_prompts)[-3:]):
            if not re.search(follow, current_query(previous).lower()):
                return needs_web_search(previous)
    return False


def output_budget(prompt: str) -> int:
    value = current_query(prompt)
    if value == "Reply with exactly: HARU OK":
        return 256
    if needs_reasoning(value):
        return 2400
    if needs_web_search(value) or needs_url_context(value):
        return 1200
    return 640 if len(value) <= 120 else 1200


def cooldown_seconds(error: dict, retry_after: str | None = None) -> float:
    seconds = 60.0
    try:
        value = float(retry_after or "")
        if math.isfinite(value):
            seconds = max(seconds, value)
    except ValueError:
        pass
    daily = False
    for detail in error.get("details", []):
        if not isinstance(detail, dict):
            continue
        if str(detail.get("@type", "")).endswith("google.rpc.RetryInfo"):
            try:
                value = float(str(detail.get("retryDelay", "")).removesuffix("s"))
                if math.isfinite(value):
                    seconds = max(seconds, value)
            except ValueError:
                pass
        for violation in detail.get("violations", []):
            if not isinstance(violation, dict):
                continue
            metric = str(violation.get("quotaId", "")) + str(violation.get("quotaMetric", ""))
            daily |= "perday" in metric.lower() or "per_day" in metric.lower()
    if daily:
        try:
            now = datetime.now(ZoneInfo("America/Los_Angeles"))
            tomorrow = datetime.combine(now.date() + timedelta(days=1), datetime.min.time(), tzinfo=now.tzinfo)
            seconds = max(seconds, (tomorrow - now).total_seconds())
        except ZoneInfoNotFoundError:
            seconds = 86_400.0  # Conservative fallback on Windows without timezone data.
    return min(86_400.0, seconds)


@contextmanager
def gemini_request(api_key: str):
    # A fingerprint is retained only in process memory. Different keys in the same
    # Google project still share server quotas; project identity is not discoverable here.
    identity = hashlib.sha256(api_key.encode("utf-8")).hexdigest()
    now = time.monotonic()
    with _LOCK:
        state = _STATES.get(identity)
        if state is None:
            if len(_STATES) >= 128:
                expired = [key for key, value in _STATES.items()
                           if not value["busy"] and max(value["until"], value["last"] + 3) <= now]
                for key in expired:
                    del _STATES[key]
            if len(_STATES) >= 128:
                raise RuntimeError("AI request capacity reached. Try again later.")
            state = {"busy": False, "last": -math.inf, "until": 0.0}
            _STATES[identity] = state
        if state["busy"]:
            raise RuntimeError("An AI request is already running. Wait for it to finish.")
        if state["until"] > now:
            raise RuntimeError(f"Gemini/Antigravity quota cooldown: wait {math.ceil(state['until'] - now)}s. Check project limits in Google AI Studio.")
        if now - state["last"] < 3:
            raise RuntimeError("Please wait 3 seconds between AI requests or connection tests.")
        state["busy"], state["last"] = True, now
    try:
        yield
    except Exception as exc:
        if getattr(exc, "status_code", None) == 429:
            with _LOCK:
                state["until"] = time.monotonic() + getattr(exc, "cooldown_s", 60.0)
        raise
    finally:
        with _LOCK:
            state["busy"] = False
