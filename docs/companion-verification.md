# HARU companion verification — v0.9.5

## Behavior model

HARU uses a standard Android Home-screen AppWidget for its persistent interactive companion.

- No lock-screen Activity or live wallpaper.
- No draw-over-other-apps permission.
- No accessibility service.
- No foreground companion service.
- No background AI polling.
- No automatic chat or microphone activation.
- Launcher owns widget placement, dragging and resizing.
- HARU widget content is local and privacy-preserving: task/reminder counts may be shown, never task/reminder text.

## Interaction

- Tap the HARU bar: immediate local acknowledgement.
- Acknowledgement display duration: about 650 ms.
- No Activity launches on acknowledgement.
- The widget then advances to the next local check-in.
- Android controls periodic widget refresh timing; HARU requests a 30-minute refresh and also refreshes after app updates, reboot, task edits and app resume.

## Installation and persistence

Android does not allow an ordinary app to silently place a Home-screen widget. The first placement requires launcher confirmation through the standard pin-widget flow, or manual placement from the launcher's Widgets picker.

After placement, the widget remains on the Home screen until the user removes it. Long-press to drag, resize or remove it.

## Device checks

1. Open HARU → **Home companion**.
2. Tap **Add HARU to Home screen** and confirm the launcher prompt.
3. Verify the widget appears as a compact horizontal HARU bar.
4. Long-press and drag it to another Home-screen position.
5. Tap the bar repeatedly; each reaction should feel immediate and should not open HARU.
6. Open the HARU app while the widget remains installed; verify the app is fully usable and no HARU surface overlaps it.
7. Add/edit/delete a task and verify the widget refreshes without exposing task text.
8. Pause check-ins in Settings and verify the widget shows a paused state.
9. Reboot and verify reminders are rescheduled and the widget remains usable.
10. Resize the widget and verify text/face remain legible.

## Automated gates

CI should run:
- Python runtime tests;
- Android unit tests;
- Home-widget provider/action tests;
- release lint;
- optimized APK assembly;
- stable signing and checksum verification.
