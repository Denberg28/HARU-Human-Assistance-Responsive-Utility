# HARU v0.9.24

## Focus Actions
- Adds one-tap **Done focus** for the first unfinished task on HARU's Today card.
- Adds the same focus completion action inside the Today task dialog.
- Adds a lock-screen **Done focus** action to the Today notification.
- Completes tasks by persistent task ID, so drag/reorder cannot complete the wrong item.
- Stale lock-screen actions are rejected if the focus has changed.
- Completing a focus task immediately promotes the next open task and refreshes HARU's lock-screen content.
- HARU gives a short local completion reaction without calling an AI provider.

## Efficiency and reliability
- No new background polling, timers, network calls, or GPS work.
- Existing task order persistence remains unchanged.
- Added regression coverage for ID-based completion after task reordering.
