HARU v0.7.3 hardens the primary UI and action-state flow after a full interaction review.

- Fixes companion-generated chat starters remaining in Tell HARU after the companion is paused.
- Separates companion-generated drafts from user-owned text. If the user edits a starter, it becomes a normal user draft and is preserved.
- "Another" clears only an untouched companion draft before rotating to a new starter, preventing stale prompt/text mismatches.
- Home-bubble chat starters use the same generated-draft lifecycle and never overwrite a real user draft.
- Sanitizes Tell HARU input at the ViewModel boundary, removes control characters/newlines, and bounds input to 2,000 characters.
- Uses one authoritative Send eligibility rule across the button, keyboard IME action, and activity/backend entry point. Blank or busy submissions are ignored.
- Prevents Mic re-entry while HARU is already busy/listening and safely transitions recognized speech back into the same validated input path.
- Conversation reset clears draft ownership as well as visible input and memory state.
- No changes to Map behavior, reminder scheduling, location sharing, AI provider selection, Home widget scheduling, or the stable Android release signer.

Regression tests cover generated-vs-user draft ownership, edited starter preservation, companion-draft cleanup, input sanitization/bounds, busy/blank submission guards, and reset behavior.
