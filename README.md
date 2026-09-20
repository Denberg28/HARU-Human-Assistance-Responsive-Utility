# HARU — Human Assistance & Responsive Utility

HARU is a lightweight, free-first personal companion platform. Android v0.6.0 deliberately simplifies the visible experience to two tabs: HARU and Map.

- **Streamlit HARU Lab** for rapid testing and cloud/local use.
- **Native Android app** for low-power voice, deterministic skills, Groq/Gemini/Antigravity AI, OpenStreetMap, hazards, reminders, and standalone APK distribution.

## Design principles

- Keep the UI simple and responsive.
- Local deterministic skills continue to work without an AI provider.
- Online AI is optional and provider-isolated.
- Android online mode defaults to **Antigravity**. Groq uses `qwen/qwen3.8-27b`; Gemini models are discovered from Google's live model catalog with a manual Refresh button.
- API keys are never committed. Streamlit Cloud uses app Secrets; local Windows uses the OS credential store after a successful connection.
- AI providers receive text context only. Android microphone and TTS remain in the device voice layer.

## Streamlit

```powershell
python -m venv .venv
.\.venv\Scripts\activate
python -m pip install -r requirements.txt
python -m streamlit run streamlit_app.py
```

## Secrets

Never commit API keys. The repository ignores:

- `.streamlit/secrets.toml`
- `.env`
- `.env.*`

For Streamlit Cloud, configure:

```toml
GEMINI_API_KEY = "..."
HARU_LOCATION_SHARE_SECRET = "use-a-long-random-secret"
```

`HARU_LOCATION_SHARE_SECRET` signs temporary Trusted Locations share codes so they remain valid across Streamlit app restarts. Location codes contain the shared coordinates and should only be sent to people the user trusts.

## Android

Open the repository in Android Studio, allow Gradle sync, and run the `app` configuration. Microphone permission is requested only when voice input is used.

HARU Android keeps the on-device LLM stack out of the standard build to reduce APK size, RAM use, heat, background work, and battery drain. Online AI supports Antigravity, Gemini, and Groq Qwen 3.8 27B. Gemini and Groq credentials are encrypted locally with Android Keystore-backed storage. All providers share HARU's bounded encrypted rolling conversation memory.

## APK build

HARU uses a simple reproducible GitHub Actions build instead of committing Gradle wrapper binaries.

- GitHub: run **Build Android APK** from Actions, or push an Android code change to `main`.
- Output artifact: **HARU-v0.3.3-Standalone-arm64**
- APK: `HARU-v0.3.3-Standalone-arm64.apk`

For a local build, open the project in Android Studio and use **Build > Build APK(s)**.

## Reliability scope

HARU is intentionally small: one UI shell, deterministic local skills, provider adapters, bounded external requests, and minimal persistent state. Add integrations as isolated adapters rather than expanding the core UI.


## Companion Node direction

HARU Android v0.5.0 is local-first and mode-aware. Home is the primary experience; the Assistant tab is a secondary reasoning console. Persistent modes are Normal, Flight, Travel, Work, Safety, and Rest. Mode switching is local and costs no AI request. Cloud AI is optional and receives the current mode only when reasoning is actually needed.

HARU Android v0.5.3 fixes lock-screen visibility on Android/OEM skins by migrating HARU to a fresh visual notification channel with DEFAULT importance while keeping it silent and non-vibrating. A Test now action and direct Lock-screen settings shortcut help verify OEM notification policy.

HARU Android v0.5.1 adds an optional lock-screen companion status. It is implemented as a quiet persistent notification rather than a background service: no wake lock, location polling, network polling, or periodic timer is started by the feature. The notification exposes only the current HARU mode and generic companion status.

HARU's companion loop is intentionally small:

- local notes and tasks
- relative reminders such as `remind me in 30 minutes to charge batteries`
- a compact Today summary
- Android system reminder notifications that can fire while HARU is closed
- Android reminders are restored after phone reboot

Desktop/local Streamlit stores companion state privately in `~/.haru/companion.json`.
Streamlit Cloud keeps notes, tasks, and reminders session-only to avoid cross-user persistence.
Android stores companion state in app-private preferences.

## Standalone Android install

HARU Android is distributed as a standalone APK. Uninstall the previous HARU build before installing a standalone APK if Android presents it as an update. The app does not request package-install permission and contains no in-app updater.


## Simple HARU v0.6.0

The Android interface is intentionally compact:

- **HARU** — one companion face, one Today card, one universal text/voice input, and one Settings entry.
- **Map** — retained without redesign; existing MapLibre/GPS/live-sharing behavior is locked.
- Tasks: type `task Buy milk` or open Today and add a task.
- Reminders: type `remind me in 30 min to call`.
- Advanced AI, memory reset, lock-screen controls, and app update checks are kept behind Settings.
- News, hazards, modes, priorities, and diagnostic cards are no longer shown as primary UI.
