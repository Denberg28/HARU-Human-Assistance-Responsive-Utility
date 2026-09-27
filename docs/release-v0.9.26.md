# HARU v0.9.26

## Gemini agent catalog cleanup
- Gemini dropdown now keeps only the newest stable **Flash** and **Flash-Lite** agents.
- Preview, experimental, deprecated, legacy, image, TTS, audio, robotics, and other specialized variants are excluded.
- Cached catalogs are sanitized whenever HARU reads them, so obsolete entries do not persist in the selector.
- Adds **Update agents** directly inside the Gemini dropdown.
- Update agents fetches Google's live model catalog, keeps current generateContent-capable candidates, then runs a small connection probe with the saved Gemini key.
- Only candidates that actually answer HARU's probe are saved back to the dropdown.
- If the previously selected agent disappears, HARU automatically switches to the preferred remaining current agent.
- Existing Map/GPS, task workflow, reminders, lock-screen, themes, and other HARU features are unchanged.

## Current target
Google currently recommends Gemini 3.8 Flash and Gemini 3.5 Flash-Lite for new Gemini API projects. HARU discovers them dynamically rather than hard-coding the selector to a permanent historical list.
