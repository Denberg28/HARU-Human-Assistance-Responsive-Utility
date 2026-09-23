# HARU v0.9.14 — smooth and reliable task reordering

v0.9.14 fixes the task drag behavior reported on-device after v0.9.13.

- Saves the reordered task state immediately using a synchronous preference commit.
- Saves again when the drag ends, when a drag is cancelled, and when the Today dialog is closed.
- Restricts dragging to the visible ≡ handle so normal task taps remain predictable.
- Adds a lifted/translated drag visual so the active task follows the finger more naturally.
- Reduces the swap threshold for smoother movement between adjacent rows.
- Preserves wrapped-text alignment and the existing edit/delete behavior.
- Keeps lock-screen task ordering consistent with the saved Today order.

## Physical-device verification

1. Open Today and long-press the ≡ handle of a task.
2. Drag it above or below another task and release.
3. Confirm the dragged row visually follows the movement more smoothly.
4. Close Today, reopen it, and confirm the order is unchanged.
5. Close and reopen HARU and confirm the same order remains.
6. Lock the phone and confirm the Today notification follows the saved task order.
