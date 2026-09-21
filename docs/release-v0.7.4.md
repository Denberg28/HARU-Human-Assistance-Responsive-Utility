HARU v0.7.4 fixes the remaining Tell HARU input contamination seen on-device.

- Companion prompts no longer populate the editable Tell HARU field.
- Tapping "Let's chat" now focuses the chat field only; the field stays empty until the user types or uses Mic.
- Home Bubble chat opens HARU and focuses the same clean input path without carrying a hidden/generated prompt extra.
- The editable command state now has one owner: user keyboard or recognized speech.
- Retains the v0.7.3 input sanitization, 2,000-character bound, blank/busy Send guards, and Mic re-entry protection.
- Corrects focus handling so the requester is attached to Tell HARU itself, not another card.
- Removes obsolete generated-draft ownership state and prompt plumbing.
- No changes to Map, reminders, live location, AI providers, memory limit, Home Bubble scheduling, or the stable Android release signer.

Regression checks cover user-only input, bounded/sanitized input, busy/blank submission, submitted-message clearing, reset behavior, and Home Bubble launches with no editable prompt payload.
