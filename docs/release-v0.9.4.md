# HARU v0.9.4 — compact interactive lock-screen bar

This release removes the two competing lock-screen approaches from v0.9.3 and keeps one small, testable interaction surface.

- Removes the HARU live-wallpaper service and wallpaper chooser path. HARU no longer replaces the user's lock-screen wallpaper.
- Reworks `HaruLockScreenActivity` into a translucent, bar-sized window instead of a full HARU screen.
- The window contains only the existing HARU check-in card.
- Adds `FLAG_NOT_TOUCH_MODAL` so taps outside the HARU bar continue to the system keyguard rather than being swallowed by HARU.
- Keeps the normal keyguard intact. HARU does not dismiss it, turn the display on, keep the display awake, request accessibility, request draw-over-other-apps, or use a full-screen intent.
- Unlocking closes the HARU bar automatically. Screen-off and inactive states stop animation callbacks.
- Removes obsolete wallpaper code, manifest entries, resource XML, UI copy and MainActivity wiring.
- Updates unit tests for the compact window, touch-modal flag, private task, retired wallpaper service, and centered card hit area.

## Platform boundary

Android does not give ordinary applications a universal permanent custom-control slot inside every manufacturer's keyguard. HARU therefore uses an explicitly opened, tightly sized `showWhenLocked` window. Visually and touch-wise it is only the HARU bar; it is not a replacement lock-screen page.

## Physical-device check

1. Open **Lock screen → Show interactive HARU bar**.
2. Press the power button to lock.
3. Wake the screen without unlocking.
4. Verify that the original wallpaper, clock and fingerprint/lock controls remain visible.
5. Tap HARU and verify the delighted reaction.
6. Swipe/tap outside HARU and verify normal keyguard interaction.
7. Unlock and verify that HARU closes.
