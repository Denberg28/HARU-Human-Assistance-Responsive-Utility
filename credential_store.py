from __future__ import annotations

SERVICE_NAME = "HARU AI Credentials"

try:
    import keyring
except Exception:  # pragma: no cover - optional backend safety
    keyring = None


def get_key(provider: str) -> str:
    if not provider or keyring is None:
        return ""
    try:
        return (keyring.get_password(SERVICE_NAME, provider) or "").strip()
    except Exception:
        return ""


def save_key(provider: str, api_key: str) -> bool:
    if not provider or not api_key or keyring is None:
        return False
    try:
        keyring.set_password(SERVICE_NAME, provider, api_key)
        return True
    except Exception:
        return False


def delete_key(provider: str) -> bool:
    if not provider or keyring is None:
        return False
    try:
        keyring.delete_password(SERVICE_NAME, provider)
        return True
    except Exception:
        return False
