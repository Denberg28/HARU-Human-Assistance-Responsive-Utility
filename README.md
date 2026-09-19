# HARU — Human Assistance & Responsive Utility

HARU is a lightweight, free-first personal assistant prototype with two front ends:

- **Streamlit HARU Lab** for rapid testing and cloud/local use.
- **Native Android app** for low-power voice, deterministic skills, Gemini/Antigravity AI, OpenStreetMap, hazards, reminders, and in-app updates.

## Design principles

- Keep the UI simple and responsive.
- Local deterministic skills continue to work without an AI provider.
- Online AI is optional and provider-isolated.
- Android online mode defaults to **Antigravity**. Gemini models are discovered from Google's live model catalog with a manual Refresh button.
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

HARU Android v0.3.0 removes the on-device LLM stack entirely to reduce APK size, RAM use, heat, background work, and battery drain. Online AI is limited to Antigravity and Gemini. The Gemini model list refreshes on demand from Google's model catalog.

## APK build

HARU uses a simple reproducible GitHub Actions build instead of committing Gradle wrapper binaries.

- GitHub: run **Build Android APK** from Actions, or push an Android code change to `main`.
- Output artifact: **HARU-v0.3.0-arm64**
- APK: `HARU-v0.3.0-arm64.apk`

For a local build, open the project in Android Studio and use **Build > Build APK(s)**.

## Reliability scope

HARU is intentionally small: one UI shell, deterministic local skills, provider adapters, bounded external requests, and minimal persistent state. Add integrations as isolated adapters rather than expanding the core UI.


## Companion Core

HARU keeps the companion loop intentionally small:

- local notes and tasks
- relative reminders such as `remind me in 30 minutes to charge batteries`
- a compact Today summary
- Android system reminder notifications that can fire while HARU is closed
- Android reminders are restored after phone reboot

Desktop/local Streamlit stores companion state privately in `~/.haru/companion.json`.
Streamlit Cloud keeps notes, tasks, and reminders session-only to avoid cross-user persistence.
Android stores companion state in app-private preferences.
