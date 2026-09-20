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


## Simple HARU v0.6.1

- Lock screen: HARU chibi large icon plus Today task/reminder lines.
- Collapsed lock-screen notification shows the first Today item; expanded view shows up to four.
- Today task flow: add -> tap task -> edit / done / delete.
- Tell HARU is anchored to the bottom interaction area and stays above the keyboard.
- Map remains unchanged and locked.


## Simple HARU v0.6.2

- Centered HARU brand header with **Human Assistance & Responsive Utility** subtitle.
- Smaller companion face for a tighter professional layout.
- Task edit dialog now shows only **Delete** and **Save**.
- Voice diagnostics removed from the visible main screen.
- Tell HARU keeps safe spacing above Android navigation controls.
- Map remains unchanged and locked.


## Simple HARU v0.6.3

Stabilization release:

- AI responses render structured headings, lists, quotes, inline emphasis, and code blocks instead of raw Markdown symbols.
- New answers reset to the top of the response area.
- Retired News/Hazards services and legacy pre-v0.6 UI code are removed.
- Primary surface remains **HARU + Today + Settings + Map**.
- Map behavior remains unchanged and locked.
- Response parsing has unit coverage for normal, malformed, and control-character input.


## Simple HARU v0.6.4 — feature lock

The visible feature set is now locked around **HARU + Today + Settings + Map**.

- Map primary actions (My GPS / Google Maps) stay in a fixed bottom bar above Android navigation.
- Tap the large **HARU** header for About information, including **Creator: MD**.
- HARU companion notifications default to enabled unless the user explicitly turns them off.
- Android notification permission is requested once when needed.
- The companion notification uses a fresh channel and shows Today tasks/reminders in the notification shade and lock screen.
- Fired reminders use a fresh HARU reminder channel with public lock-screen visibility.
- Notification behavior remains event-driven: no polling, wake lock, or additional standby GPS/network activity.

Post-v0.6.4 development should prioritize notification reliability, reminder delivery, and companion behavior rather than adding visible feature clutter.


## HARU v0.7.0 — Home Bubble

HARU companion presence moves away from a persistent notification and into a native Android home-screen widget.

- **HARU Bubble** uses the existing HARU emoji pack.
- Time-of-day greetings: Good morning / Good afternoon / Good evening.
- Idle expressions and short companion lines rotate locally about every 30 minutes.
- Today context is local: open task and reminder counts appear without using AI.
- Tap the HARU face to cycle expression/idle text immediately.
- Tap the rest of the bubble to open HARU.
- Settings provides **Add HARU Bubble to Home** and **HARU Bubble · On/Paused**.
- Launcher controls exact placement and removal; place it in the upper-right if preferred.
- Persistent companion notification code is removed.
- Notifications are retained only for actual due reminders.
- No foreground service, wake lock, continuous polling, or background AI usage is added.
- Map remains unchanged and locked.
