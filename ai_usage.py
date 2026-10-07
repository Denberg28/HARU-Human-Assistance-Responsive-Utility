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


def needs_agent(prompt: str) -> bool:
    return bool(re.search(
        r"\b(deep research|research thoroughly|in-depth research|investigate|audit|compare sources|verify sources|cross-check|cross check)\b"
        r"|\b(open|visit|read|inspect)\s+(?:this\s+)?(?:url|website|web page|webpage|link)\b",
        prompt.lower(),
    ))


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
