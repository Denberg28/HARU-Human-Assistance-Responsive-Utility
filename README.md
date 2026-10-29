# HARU — Human Assistance & Responsive Utility

HARU is a lightweight personal journal and companion. The Android app has **Journal** and **Map** tabs. Creator: MD.

## Android v0.10.0

AI chat has been replaced by a date-based journal. Write today or choose a past date, then mark a memorable day with ★. Journal text is encrypted on this phone and autosaves after a short typing pause. Save and open errors are shown without clearing stored entries.

Recent seven dates stay visible. Older entries collapse into weeks, months after four weeks, and years after twelve months. Expand through Year → Month → Week → Day. Calendar months retain their true lengths and crossing weeks are clipped to the parent month. Expansion state and stable list IDs keep navigation in place. The Memorable filter retains the same archive structure.

Tasks, task dragging, reminders, the local companion/check-ins, lock-screen setup, themes, and location sharing are retained. Create reminders with the direct date/time form. Tap the HARU title for About or open Settings for appearance and updates.

Maps and live sharing still need connectivity and permission. Journal entries are never shared through location sharing. AI providers, API keys, model selection, and microphone features have been removed from Android. Old encrypted conversation data is preserved without being converted into journal entries. See [release notes](docs/release-v0.10.0.md).

## Install and build

Get the APK from [GitHub Releases](https://github.com/Denberg28/HARU-Human-Assistance-Responsive-Utility/releases). Install over your existing HARU app to preserve local data. Uninstalling deletes tasks, credentials, and conversation history; it is not a routine update step.

GitHub Actions **Build Android APK** runs Android unit/widget tests, release lint, and the optimized release build before publishing the APK and SHA-256 checksum. The toolchain is pinned in the Gradle files and workflow (Java 21, Gradle 9.6.0, compile SDK 37, target SDK 36, minimum SDK 26). For local builds use Android Studio with the same toolchain or installed Gradle:

```sh
gradle --no-daemon :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

Robolectric widget tests cover Android API 26 and 36. Physical launcher acceptance steps and known limits are recorded in [companion verification](docs/companion-verification.md). Release notes are in [v0.7.1](docs/release-v0.7.1.md).

CI restores the existing signer from a protected signing secret and checks its certificate fingerprint. Publication fails if the signer is unavailable, preserving compatibility with installed HARU updates.

## Privacy and reliability

- Journal text uses Android Keystore-backed AES-GCM encryption; dates and stars are app-private database metadata. Android backup is disabled.
- Tasks, notes, and reminders retain their existing app-private storage. Upgrading preserves these records.
- Companion check-ins are local. Due reminder notifications may display reminder text according to Android settings; journal text is never included.
- Network access remains for maps, explicit location sharing, and update checks. No AI runtime or API requests remain in the Android app.
- Never commit API keys, signing keys, local properties, or app secrets.

## Streamlit HARU Lab

The separate, legacy Streamlit research lab is unchanged by the Android journal release and still supports cloud/local AI testing:

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
