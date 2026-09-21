# HARU — Human Assistance & Responsive Utility

HARU is a lightweight, free-first personal assistant and companion. The Android app has two primary tabs: **HARU** and **Map**. Creator: MD.

## Android v0.7.1

- A local companion with time-of-day greetings, rotating expressions, and conversation starters. Inside HARU it updates every 20 seconds while idle and visible; typing, dialogs, voice/AI work, Rest mode, and backgrounding pause automatic changes.
- A native **HARU Bubble** Home-screen widget. Open **Home bubble → Add to Home screen**, then confirm the launcher's Add prompt. If pinning is unsupported or no prompt appears, long-press Home → Widgets → HARU → HARU Bubble. Drag it to the upper-right or any free space.
- Setup verifies actual widget placement separately from the companion On/Paused setting. Tap the face for another expression; tap text to open an editable chat draft. Press Send to start chatting. Pause/resume is in Settings.
- Widget background updates use Android's approximately 30-minute schedule; battery policy may delay them. Idle content works offline and makes no AI requests. This is a Home widget, not a floating overlay over other apps.
- Today supports local tasks, reminders, and notes. Type `task Buy milk` or `remind me in 30 min to call`. Task editing offers Save and Delete. Due reminders use Android notifications, subject to permission and device policy.
- AI is optional: Antigravity, Gemini, and Groq are available in Settings. Local deterministic commands work offline. Online requests use bounded encrypted rolling conversation memory; Settings can clear it.
- Mic starts voice input only on request. Microphone permission is requested when used.
- Map retains MapLibre/GPS and trusted location sharing. Live sharing/monitoring pauses while the app is in the background.
- Tap the HARU heading for About. Settings includes AI configuration and update checks.

No persistent companion notification, background AI polling, overlay permission, or companion foreground service is used. The Android APK does not bundle an on-device LLM.

## Install and build

Get the APK from [GitHub Releases](https://github.com/Denberg28/HARU-Human-Assistance-Responsive-Utility/releases). Install over your existing HARU app to preserve local data. Uninstalling deletes tasks, credentials, and conversation history; it is not a routine update step.

GitHub Actions **Build Android APK** runs Android unit/widget tests, release lint, and the optimized release build before publishing the APK and SHA-256 checksum. The toolchain is pinned in the Gradle files and workflow (Java 21, Gradle 9.6.0, compile SDK 37, target SDK 36, minimum SDK 26). For local builds use Android Studio with the same toolchain or installed Gradle:

```sh
gradle --no-daemon :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

Robolectric widget tests cover Android API 26 and 36. Physical launcher acceptance steps and known limits are recorded in [companion verification](docs/companion-verification.md). Release notes are in [v0.7.1](docs/release-v0.7.1.md).

CI retains the existing signing key and fails if it is unavailable rather than producing an incompatible update. The current signing cache should eventually be migrated to a protected, durable signing secret while preserving the same key.

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

### HARU Home companion

HARU's interactive companion is now a normal Android Home-screen widget rather than a lock-screen surface. Open **Home companion** in HARU and choose **Add HARU to Home screen**. Android requires launcher confirmation the first time a widget is pinned; after that, it stays on the Home screen until you remove it.

Long-press the widget to drag, resize, or remove it. Tapping the HARU bar gives an immediate local reaction and returns to the current check-in after about 650 ms. No chat opens, no network request is made, and no overlay service runs.

This avoids the lock-screen overlap seen on some OEMs and keeps the HARU app fully usable while the companion remains available on the launcher.
