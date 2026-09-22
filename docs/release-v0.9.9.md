# HARU v0.9.9 — persistent lock-screen runtime

v0.9.9 fixes the remaining lock-screen failure caused by HARU monitoring screen events only while MainActivity's process was alive.

- Moves lock-screen wake monitoring into a user-enabled foreground service instead of relying on MainActivity's dynamic receiver.
- Keeps the runtime active after the HARU app is backgrounded, so screen-on events are still observed.
- Launches the compact HARU lock-screen activity only after the display wakes and Android confirms the keyguard is locked.
- Restores the lock-screen runtime after reboot and after app updates when Lock-screen HARU remains enabled.
- Keeps the existing Xiaomi/POCO/Redmi permissions guidance for Show on Lock screen and background pop-up windows.
- Keeps Lock-screen HARU separate from Check-ins.
- Keeps Clear memory only on the main HARU page.
- The runtime notification is silent, secret on the lock screen, non-badged, and only exists while Lock-screen HARU is enabled.
- No draw-over-other-apps, Accessibility Service, wake lock, keyguard-dismiss, or full-screen-intent permission is added.

## Physical-device verification

1. Install v0.9.9 and open HARU once.
2. Confirm Lock-screen HARU is ON.
3. Confirm Xiaomi/HyperOS permissions Show on Lock screen and Open new windows while running in the background are allowed.
4. Leave HARU or lock the phone.
5. Press the power button to wake the locked display.
6. Verify the HARU companion bar appears while the system keyguard remains visible.
7. Unlock and confirm the HARU bar closes.
8. Turn Lock-screen HARU OFF and confirm the foreground runtime stops and HARU no longer appears.
