# HARU v0.9.27

## Gemini API efficiency
- **Update agents now uses one Gemini model-catalog request only.**
- Removes the v0.9.26 generateContent probe calls that could consume free-tier generation quota during catalog refresh.
- Prevents repeated taps from starting overlapping Gemini catalog refreshes.
- Keeps the newest stable Flash and Flash-Lite entries returned by Google's live generateContent-capable model catalog.
- Preserves the selected model when it remains available and falls back cleanly when it disappears.

## Quota diagnostics
- HTTP 429 responses now preserve Google's provider error message when available instead of replacing it with only a generic quota message.
- This makes RPM, TPM, RPD, or other quota-limit causes easier to distinguish from authentication or connectivity problems.
- Provider error text is bounded before display.

## Locked baseline
- Map/GPS, task workflow, reminders, lock-screen HARU, themes, update flow, encrypted credential storage, and other stable HARU features are unchanged.
- No new background polling or automatic Gemini requests were introduced.
