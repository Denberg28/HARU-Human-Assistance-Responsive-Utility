# HARU — Human Assistance & Responsive Utility

HARU is a lightweight, free-first personal assistant and companion. The Android app has two primary tabs: **HARU** and **Map**. Creator: MD.

## Android v0.9.37

The Android app includes tasks and reminders, on-demand AI and voice, a lock-screen
companion, and a map with location sharing. Appearance and AI choices use compact
labeled dropdowns. See [release notes](docs/release-v0.9.37.md).

- Tap the HARU title for About. Open Settings for appearance, AI, lock-screen setup,
  and update checks.
- Enable lock-screen HARU in Settings. On POCO/HyperOS, allow lock-screen
  notifications and the applicable phone app permissions. HARU uses a silent
  companion notification and an explicitly enabled foreground service to respond
  to screen/keyguard events. It does not request an overlay, accessibility, or
  keyguard-dismiss permission.
- Add tasks and reminders from HARU. Task dragging saves the new order locally.
  Reminder timing uses Android's inexact allow-while-idle alarms.
- Map shows your position on request. Start a live share for 15, 30, 60, or 120
  minutes and send its package through the system share sheet. Anyone with its
  code can monitor the location; only share it with people you trust. The Google
  Maps link is a snapshot and does not stop working when the HARU code expires.
- Live publishing and monitoring pause while HARU is in the background. Stop
  sharing revokes the server session when the service is reachable; if revocation
  fails, the app reports that the code remains valid until automatic expiry.
- Live coordinates are encrypted before upload. The sharing service validates
  separate read and write tokens. Sender identity is not independently verified.
  Location sharing depends on the existing hosted service being online.

AI starts only on an explicit request or connection test. Routine news searches use one grounded Gemini generation; bounded agent tasks use status polling and best-effort server cancellation. Quota cooldowns apply to Gemini and Antigravity together. See [AI usage review](docs/ai-usage-review-v0.9.37.md) for limits and verification.

## Install and build

Get the APK from [GitHub Releases](https://github.com/Denberg28/HARU-Human-Assistance-Responsive-Utility/releases). Install over your existing HARU app to preserve local data. Uninstalling deletes tasks, credentials, and conversation history; it is not a routine update step.

GitHub Actions **Build Android APK** runs Android unit/widget tests, release lint, and the optimized release build before publishing the APK and SHA-256 checksum. The toolchain is pinned in the Gradle files and workflow (Java 21, Gradle 9.6.0, compile SDK 37, target SDK 36, minimum SDK 26). For local builds use Android Studio with the same toolchain or installed Gradle:

```sh
gradle --no-daemon :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

Robolectric widget tests cover Android API 26 and 36. Physical launcher acceptance steps and known limits are recorded in [companion verification](docs/companion-verification.md). Release notes are in [v0.7.1](docs/release-v0.7.1.md).

CI restores the existing signer from a protected signing secret and checks its certificate fingerprint. Publication fails if the signer is unavailable, preserving compatibility with installed HARU updates.

## Privacy and reliability

- Credentials and AI conversation memory use Android Keystore-backed encryption. Tasks, notes, and reminders use app-private preferences. Android backup is disabled.
- Widget content exposes generic counts and local prompts, not task/reminder text. Actual due reminder notifications can show their text on the lock screen according to Android settings.
- Widget custom broadcasts are private and click actions use immutable PendingIntents. Network response size limits are enforced during reading.
- AI calls occur only for explicit requests. No location, weather, telemetry, or completed action is invented by local companion prompts.
- Never commit API keys, signing keys, local properties, or app secrets.

## Streamlit HARU Lab

The separate Streamlit app supports rapid cloud/local testing:

```powershell
python -m venv .venv
.\.venv\Scripts\activate
python -m pip install -r requirements.txt
python -m streamlit run streamlit_app.py
```

Local Windows credentials use the OS credential store after connection. Streamlit Cloud uses app Secrets; do not commit `.streamlit/secrets.toml`. Configure `GEMINI_API_KEY` and a strong `HARU_LOCATION_SHARE_SECRET` in the secret store as required. Location share codes contain coordinates and should only be sent to trusted recipients.

Desktop companion data is stored in `~/.haru/companion.json`. Streamlit Cloud uses session-only companion data to avoid sharing it across users.

```sh
python -m unittest discover -s tests -v
```
