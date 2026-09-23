# HARU v0.9.13 — task ordering and cleaner lock-screen tasks

v0.9.13 improves task organization in the Today panel and makes the lock-screen task section easier to scan.

- Adds press-and-hold drag reordering for open tasks in Today.
- Persists the custom task order so it is retained after closing the dialog or restarting HARU.
- Adds a visible ≡ drag affordance while preserving normal tap-to-edit behavior.
- Fixes wrapped task text alignment: continuation lines now align with the task text instead of returning under the bullet/left edge.
- Keeps completed-task positions stable while reordering open tasks.
- Changes the expanded lock-screen Today notification to an inbox layout with one task per row.
- Shows up to six task rows when expanded, then a compact “+N more” summary.
- Keeps the collapsed lock-screen task summary compact.
- Keeps lock-screen task display read-only; editing and reordering remain inside HARU.
- Adds regression tests for persistent task ordering.

## Physical-device verification

1. Open **Today**.
2. Press and hold a task row, then drag it above or below another task.
3. Close Today and reopen it; confirm the reordered task positions remain.
4. Verify a long task such as “make a 1/2 pvc tubing 1 side with thread” wraps with the second line aligned under the task text.
5. Lock the phone and expand **Today** using the notification arrow.
6. Confirm each visible task appears on its own row rather than in one continuous sentence.
7. Collapse the notification and confirm the compact task summary remains concise.
