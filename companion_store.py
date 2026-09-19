from __future__ import annotations

from datetime import datetime, timedelta, timezone
import json
import os
from pathlib import Path
import re
import tempfile
import uuid


MAX_ITEMS = 200


def utc_now_ts() -> int:
    return int(datetime.now(timezone.utc).timestamp())


def parse_relative_reminder(text: str, now_ts: int | None = None) -> tuple[str, int] | None:
    """Parse: remind me in N minutes/hours/days to <text>."""
    match = re.fullmatch(
        r"remind me in\s+(\d{1,4})\s*"
        r"(minute|minutes|min|mins|hour|hours|hr|hrs|day|days)"
        r"\s+(?:to\s+)?(.+)",
        (text or "").strip(),
        flags=re.IGNORECASE,
    )
    if not match:
        return None

    amount = int(match.group(1))
    unit = match.group(2).lower()
    reminder_text = re.sub(r"\s+", " ", match.group(3).strip())[:500]
    if not reminder_text or amount <= 0:
        return None

    if unit in {"minute", "minutes", "min", "mins"}:
        delta = timedelta(minutes=amount)
    elif unit in {"hour", "hours", "hr", "hrs"}:
        delta = timedelta(hours=amount)
    else:
        delta = timedelta(days=amount)

    if delta > timedelta(days=30):
        return None

    base = datetime.fromtimestamp(
        now_ts if now_ts is not None else utc_now_ts(),
        tz=timezone.utc,
    )
    return reminder_text, int((base + delta).timestamp())


def new_reminder(text: str, due_at: int) -> dict:
    return {
        "id": uuid.uuid4().hex,
        "text": re.sub(r"\s+", " ", (text or "").strip())[:500],
        "due_at": int(due_at),
        "delivered": False,
    }


def sanitize_state(raw: dict | None) -> dict:
    raw = raw if isinstance(raw, dict) else {}

    notes = [
        re.sub(r"\s+", " ", str(item).strip())[:500]
        for item in raw.get("notes", [])
        if str(item).strip()
    ][-MAX_ITEMS:]

    tasks = []
    for item in raw.get("tasks", []):
        if not isinstance(item, dict):
            continue
        text = re.sub(r"\s+", " ", str(item.get("text", "")).strip())[:500]
        if not text:
            continue
        tasks.append({"text": text, "done": bool(item.get("done"))})
    tasks = tasks[-MAX_ITEMS:]

    reminders = []
    for item in raw.get("reminders", []):
        if not isinstance(item, dict):
            continue
        text = re.sub(r"\s+", " ", str(item.get("text", "")).strip())[:500]
        try:
            due_at = int(item.get("due_at") or 0)
        except (TypeError, ValueError):
            continue
        if not text or due_at <= 0:
            continue
        reminder_id = re.sub(r"[^a-zA-Z0-9_-]", "", str(item.get("id", "")))[:64]
        reminders.append(
            {
                "id": reminder_id or uuid.uuid4().hex,
                "text": text,
                "due_at": due_at,
                "delivered": bool(item.get("delivered")),
            }
        )
    reminders = reminders[-MAX_ITEMS:]

    return {"notes": notes, "tasks": tasks, "reminders": reminders}


class CompanionStore:
    """Small, atomic local-only persistence store for HARU companion state."""

    def __init__(self, path: str | Path | None = None) -> None:
        default_path = Path.home() / ".haru" / "companion.json"
        self.path = Path(path) if path else default_path

    def load(self) -> dict:
        try:
            if not self.path.exists():
                return sanitize_state({})
            raw = json.loads(self.path.read_text(encoding="utf-8"))
            return sanitize_state(raw)
        except (OSError, ValueError, json.JSONDecodeError):
            return sanitize_state({})

    def save(self, state: dict) -> None:
        clean = sanitize_state(state)
        self.path.parent.mkdir(parents=True, exist_ok=True)

        fd, temp_name = tempfile.mkstemp(
            prefix="companion-",
            suffix=".json.tmp",
            dir=str(self.path.parent),
        )
        try:
            with os.fdopen(fd, "w", encoding="utf-8") as handle:
                json.dump(clean, handle, ensure_ascii=False, separators=(",", ":"))
                handle.flush()
                os.fsync(handle.fileno())
            os.replace(temp_name, self.path)
        finally:
            try:
                if os.path.exists(temp_name):
                    os.remove(temp_name)
            except OSError:
                pass


def due_reminders(reminders: list[dict], now_ts: int | None = None) -> list[dict]:
    current = now_ts if now_ts is not None else utc_now_ts()
    return [
        item
        for item in reminders
        if not item.get("delivered") and int(item.get("due_at") or 0) <= current
    ]


def upcoming_reminders(
    reminders: list[dict],
    now_ts: int | None = None,
    limit: int = 5,
) -> list[dict]:
    current = now_ts if now_ts is not None else utc_now_ts()
    items = [
        item
        for item in reminders
        if not item.get("delivered") and int(item.get("due_at") or 0) > current
    ]
    return sorted(items, key=lambda item: int(item["due_at"]))[: max(0, limit)]
