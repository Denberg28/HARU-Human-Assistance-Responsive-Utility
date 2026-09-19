# HARU — Human Assistance & Responsive Utility

HARU is a lightweight, free-first personal assistant prototype with two front ends:

- **Streamlit HARU Lab** for rapid testing and cloud/local use.
- **Native Android app** for local voice, deterministic skills, and future device actions.

## Design principles

- Keep the UI simple and responsive.
- Local deterministic skills continue to work without an AI provider.
- Online AI is optional and provider-isolated.
- Google online mode defaults to **Antigravity**, with **Gemini 3.5 Flash-Lite** as the lightweight alternative.
- OpenRouter exposes **free routes/models only** in HARU.
- API keys are never committed. Streamlit Cloud uses app Secrets; local Windows uses the OS credential store after a successful connection.
- AI providers receive text context only. Android microphone and TTS remain in the device voice layer.

## Streamlit

```powershell
python -m venv .venv
.\.venv\Scripts\activate
python -m pip install -r requirements.txt
python -m streamlit run streamlit_app.py
```

For local Ollama setup, run:

```powershell
.\run_haru_local.bat
```

## Secrets

Never commit API keys. The repository ignores:

- `.streamlit/secrets.toml`
- `.env`
- `.env.*`

For Streamlit Cloud, configure:

```toml
GEMINI_API_KEY = "..."
OPENROUTER_API_KEY = "..."
HARU_LOCATION_SHARE_SECRET = "use-a-long-random-secret"
```

`HARU_LOCATION_SHARE_SECRET` signs temporary Trusted Locations share codes so they remain valid across Streamlit app restarts. Location codes contain the shared coordinates and should only be sent to people the user trusts.

## Android

Open the repository in Android Studio, allow Gradle sync, and run the `app` configuration. Microphone permission is requested only when voice input is used.

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
