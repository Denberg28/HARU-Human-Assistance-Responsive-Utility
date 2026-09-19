# HARU — Human Assistance & Responsive Utility

HARU is a lightweight Android companion focused on fast, local-first assistance with an expressive animated face.

## v0.1 goals

- Native Android app using Kotlin + Jetpack Compose
- Lightweight animated HARU face
- Explicit state machine: idle, listening, thinking, working, success, confused, alert, sleepy
- Text command input
- Deterministic local skill router
- Time, calculator, notes, and help skills
- Clean interfaces for Android speech recognition and TTS
- AI remains an optional future fallback, never a dependency for basic commands

## Design principle

**Local deterministic skills first. AI only when it adds value.**

HARU is intentionally independent from Cube OS and MCore. Future bridge skills can connect to those systems without making the phone app heavy.

## Planned progression

- v0.1: face + text commands + local skills
- v0.2: tap-to-talk + text-to-speech
- v0.3: reminders/notifications and persistent notes
- v0.4: optional AI fallback
- v0.5: Cube OS / MCore bridge

## Development

Open the repository in Android Studio, allow Gradle sync, then run the `app` configuration on an Android emulator or physical device.
