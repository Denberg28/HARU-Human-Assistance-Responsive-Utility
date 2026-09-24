# HARU v0.9.18 — background themes

v0.9.18 expands HARU's appearance controls while keeping the existing lightweight runtime unchanged.

## Theme
- Softens the Lavender accent for a calmer default appearance.
- Adds independent background choices: Light, Sepia, and Dark.
- Keeps Lavender, Blue, Green, Rose, and Amber as separate accent choices.
- Applies theme changes immediately across Material surfaces and controls.
- Persists both accent and background choices across app restarts.
- Keeps existing installations on Light background by default.
- Falls back safely to Lavender + Light if stored theme values are invalid.

## Runtime
- Theme selection uses local preferences only.
- No new polling, network requests, foreground services, or wakeups are introduced.
- Lock-screen, reminders, maps, AI providers, memory, and live-location behavior are unchanged by this release.

## Validation targets
1. Open Settings and verify Accent color and Background are separate controls.
2. Switch among Light, Sepia, and Dark and verify text/controls remain readable.
3. Change the accent while each background is active.
4. Restart HARU and confirm both selections persist.
5. Verify lock-screen HARU, Today/tasks, Map, reminders, voice, and AI still operate normally.
