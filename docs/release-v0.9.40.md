# HARU v0.9.40

Restores broad, useful model answers while keeping secure storage and bounded API usage.

- Settings → HARU AI → Answer mode: **Auto · live lookup** searches detected current facts; **General knowledge** answers directly without search or background agents.
- If Google returns text without safe source metadata, show it with a prominent unverified-current-facts notice instead of replacing the entire reply with an error. No additional generation or retry is added.
- Groq can answer useful general parts of current-information questions when no Gemini key is saved, with an explicit live-information notice.
- Secure conversation memory retains the user's question and a verification note instead of storing unverified model assertions. Stable answers keep the existing encrypted memory workflow.
- Translations, rewrites and fictional news writing avoid unnecessary search. A direct “without search” or “use your knowledge” instruction also selects a knowledge answer for that turn.
- General assistant instructions support questions across topics and useful partial answers. Modes set priorities rather than topic restrictions.
- Python source links now require HTTPS without embedded credentials. Invalid links cannot count as retrieval evidence.

Encrypted keys, tasks, reminders, saved settings, request spacing, quota cooldowns, response limits, redirect protection and existing signing remain in place. Inaccessible supplied pages still require pasted text; HARU does not invent their contents. Provider refusals, outages, quota limits and unavailable tools cannot be removed by app routing. General knowledge cannot establish today's news.

Validation uses simulated provider responses without production credentials. Install over HARU and test Auto versus General knowledge on the POCO; actual provider costs, entitlements and live answer quality remain unverified.
