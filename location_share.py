from __future__ import annotations

from datetime import datetime, timedelta, timezone
import base64
import hashlib
import hmac
import json
import math
import os
import re
import secrets


_PROCESS_SECRET = secrets.token_bytes(32)


def _secret_bytes() -> bytes:
    configured = os.environ.get("HARU_LOCATION_SHARE_SECRET", "").strip()
    if configured:
        return hashlib.sha256(configured.encode("utf-8")).digest()
    return _PROCESS_SECRET


def _b64encode(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).decode("ascii").rstrip("=")


def _b64decode(text: str) -> bytes:
    padding = "=" * (-len(text) % 4)
    return base64.urlsafe_b64decode(text + padding)


def _clean_name(name: str) -> str:
    return re.sub(r"\s+", " ", (name or "Loved one").strip())[:40] or "Loved one"


def encode_location_share(
    name: str,
    latitude: float,
    longitude: float,
    accuracy_m: float | None,
    expires_hours: float,
    *,
    now: datetime | None = None,
) -> str:
    """Create a signed, expiring location snapshot code."""
    lat = float(latitude)
    lon = float(longitude)
    if not (math.isfinite(lat) and math.isfinite(lon)):
        raise ValueError("Location coordinates are invalid.")
    if not (-90 <= lat <= 90 and -180 <= lon <= 180):
        raise ValueError("Location coordinates are invalid.")

    accuracy = None
    if accuracy_m is not None:
        accuracy = float(accuracy_m)
        if not math.isfinite(accuracy) or accuracy < 0 or accuracy > 100_000:
            accuracy = None

    current = now or datetime.now(timezone.utc)
    if current.tzinfo is None:
        current = current.replace(tzinfo=timezone.utc)

    hours = max(0.25, min(float(expires_hours), 24.0))
    payload = {
        "v": 2,
        "name": _clean_name(name),
        "lat": round(lat, 5),
        "lon": round(lon, 5),
        "acc": round(accuracy, 1) if accuracy is not None else None,
        "iat": int(current.timestamp()),
        "exp": int((current + timedelta(hours=hours)).timestamp()),
        "nonce": secrets.token_urlsafe(6),
    }
    raw = json.dumps(payload, separators=(",", ":"), ensure_ascii=True).encode("utf-8")
    signature = hmac.new(_secret_bytes(), raw, hashlib.sha256).digest()[:16]
    return f"{_b64encode(raw)}.{_b64encode(signature)}"


def decode_location_share(
    code: str,
    *,
    now: datetime | None = None,
) -> dict:
    """Verify and decode a signed HARU location snapshot code."""
    clean = re.sub(r"\s+", "", code or "")
    if not clean or len(clean) > 800 or clean.count(".") != 1:
        raise ValueError("Invalid location share code.")

    payload_part, signature_part = clean.split(".", 1)
    try:
        raw = _b64decode(payload_part)
        supplied_signature = _b64decode(signature_part)
    except Exception as exc:
        raise ValueError("Location share code could not be read.") from exc

    expected_signature = hmac.new(_secret_bytes(), raw, hashlib.sha256).digest()[:16]
    if not hmac.compare_digest(supplied_signature, expected_signature):
        raise ValueError("Location share code was altered or is no longer valid.")

    try:
        payload = json.loads(raw.decode("utf-8"))
    except Exception as exc:
        raise ValueError("Location share code could not be read.") from exc

    if payload.get("v") != 2:
        raise ValueError("Unsupported location share code.")

    try:
        lat = float(payload.get("lat"))
        lon = float(payload.get("lon"))
        issued = int(payload.get("iat") or 0)
        expires = int(payload.get("exp") or 0)
    except (TypeError, ValueError) as exc:
        raise ValueError("Location share code is malformed.") from exc

    if not (math.isfinite(lat) and math.isfinite(lon)):
        raise ValueError("Location coordinates are invalid.")
    if not (-90 <= lat <= 90 and -180 <= lon <= 180):
        raise ValueError("Location coordinates are invalid.")

    current = now or datetime.now(timezone.utc)
    if current.tzinfo is None:
        current = current.replace(tzinfo=timezone.utc)
    now_ts = int(current.timestamp())

    if issued > now_ts + 300:
        raise ValueError("Location share time is invalid.")
    if expires <= now_ts:
        raise ValueError("This location share has expired.")
    if expires - issued > 24 * 3600 + 60:
        raise ValueError("Location share duration is invalid.")

    accuracy = payload.get("acc")
    try:
        accuracy = float(accuracy) if accuracy is not None else None
    except (TypeError, ValueError):
        accuracy = None
    if accuracy is not None and (
        not math.isfinite(accuracy) or accuracy < 0 or accuracy > 100_000
    ):
        accuracy = None

    return {
        "name": _clean_name(str(payload.get("name") or "Loved one")),
        "lat": lat,
        "lon": lon,
        "accuracy_m": accuracy,
        "expires": expires,
    }


def purge_expired_locations(
    items: list[dict],
    *,
    now: datetime | None = None,
) -> list[dict]:
    current = now or datetime.now(timezone.utc)
    if current.tzinfo is None:
        current = current.replace(tzinfo=timezone.utc)
    now_ts = int(current.timestamp())

    clean_items: list[dict] = []
    for item in items or []:
        try:
            expires = int(item.get("expires") or 0)
            lat = float(item.get("lat"))
            lon = float(item.get("lon"))
        except (AttributeError, TypeError, ValueError):
            continue
        if (
            expires > now_ts
            and math.isfinite(lat)
            and math.isfinite(lon)
            and -90 <= lat <= 90
            and -180 <= lon <= 180
        ):
            clean_items.append(item)
    return clean_items
