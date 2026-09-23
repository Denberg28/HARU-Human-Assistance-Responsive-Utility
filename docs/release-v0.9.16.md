# HARU v0.9.16 — task drag recovery fix

v0.9.16 fixes the regression where existing tasks could stop reordering after the stable-ID migration introduced in v0.9.15.

- Persists generated IDs immediately when older saved tasks are migrated, so the same task keeps the same identity on every load.
- Prevents reorder validation from rejecting existing tasks because their IDs changed between reads.
- Enlarges the ≡ drag-handle touch target for easier acquisition.
- Saves the latest order immediately after every successful row crossing, so an interrupted gesture cannot lose the new position.
- Keeps measured-row drag behavior for short and multi-line tasks.
- Retains tap-to-edit behavior and lock-screen ordering.
- Adds regression coverage for legacy-task ID migration persistence.

## Physical-device verification

1. Install over v0.9.15 without clearing app data.
2. Open Today and drag one of the existing older tasks using the ≡ handle.
3. Confirm the row can move again.
4. Release, tap Done, reopen Today, and confirm the order stays.
5. Force-close HARU, reopen it, and confirm the order still stays.
6. Repeat with the multi-line PVC task and verify movement remains smooth.
