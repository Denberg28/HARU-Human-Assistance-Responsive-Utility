# HARU v0.10.1 — Task entry and Today reliability

- New tasks appear first, with the existing manual order preserved beneath them. Blank input does nothing. The existing 200-task limit retains the first 200 rows after insertion.
- Today uses a lazy scrolling list for every task and stored reminder; the old 12-task and four-reminder cutoffs are removed.
- Editing, completion, and deletion use persistent task IDs, so inserting or reordering other tasks cannot redirect an action. Missing and duplicate legacy IDs are repaired and persisted.
- Tap a task and choose **Mark complete** to clear it from open tasks and update the local companion and lock screen.
- Dragging changes order in memory and saves at gesture end or cancellation, reducing synchronous storage writes and avoiding rebuilding the order during a gesture.
- Task drafts, selection, and Today visibility survive configuration changes.
- Returning from task editing retains the list's scroll position. Disabling the lock-screen service clears its separate task notification.
- Due reminders remain visible until notification delivery succeeds, including when notification permission or the reminder channel is disabled.

Journal encryption, date archive, themes, map sharing, and the signing identity are unchanged. Install over the existing app to retain local records.

Validation covers task insertion, manual-order retention, capacity, stable actions, legacy IDs, stale drag orders, full-list scrolling, overdue reminders, and add/edit/complete UI behavior. Android unit tests, release lint, and release assembly run in the APK workflow; phone drag/scroll and manufacturer lock-screen behavior still require physical-device checks.
