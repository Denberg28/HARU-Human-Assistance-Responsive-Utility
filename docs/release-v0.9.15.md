# HARU v0.9.15 — stable task drag and persistence

v0.9.15 addresses intermittent drag glitches and task-order resets seen on-device after v0.9.14.

- Gives every task a stable persistent ID instead of relying on list positions during drag/reorder.
- Migrates older saved tasks automatically by assigning an ID the next time they are loaded and saved.
- Uses actual measured row heights when deciding when a dragged task crosses another task.
- Handles multi-line task rows more accurately, reducing jumpy swaps.
- Saves the task order once at the end of a completed or cancelled drag instead of repeatedly saving during dialog close.
- Preserves the reordered state across Today reopen, app restart, and lock-screen task display.
- Keeps the drag interaction confined to the ≡ handle and preserves tap-to-edit behavior.
- Adds regression coverage for stable task IDs and ID-based reorder persistence.

## Physical-device verification

1. Open Today and long-press the ≡ handle.
2. Drag a short task through one or more rows and release.
3. Drag a multi-line task and verify row crossings are smooth and predictable.
4. Tap Done, reopen Today, and verify the order remains.
5. Fully close and reopen HARU and verify the order remains.
6. Lock the phone and confirm the lock-screen Today list follows the saved order.
