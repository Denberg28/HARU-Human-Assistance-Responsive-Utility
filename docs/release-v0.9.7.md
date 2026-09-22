# HARU v0.9.7 — lock-screen launch fix

v0.9.7 fixes the Android 14–16 launch path for HARU's lock-screen companion and simplifies the HARU chat page.

- Replaces the chat-page "Lock screen · ON/OFF" shortcut with "Clear memory · N/10".
- Keeps lock-screen controls inside Settings, where they already belong.
- Keeps lock-screen HARU enabled by default.
- Changes the screen-off launch to a PendingIntent with explicit background-activity-start opt-in on modern Android.
- Uses Android 16's `MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS` for the private HARU lock-screen activity and the Android 14–15 compatible mode on earlier supported releases.
- Preserves the existing keyguard gate: the HARU bar remains hidden while the display is off, appears only when the keyguard is actually locked, and closes after unlock.
- Does not add draw-over-other-apps, accessibility, wake-lock, or full-screen-notification permissions.

## Physical-device checks

1. Install v0.9.7 and open HARU once after installation.
2. Confirm Settings shows Lock-screen HARU ON.
3. Press the power button to lock the phone.
4. Wake the display without unlocking and verify HARU appears on the lock screen.
5. Tap HARU and verify the acknowledgement reaction.
6. Unlock and verify HARU disappears immediately.
7. On the HARU page, verify the former lock-screen shortcut now reads Clear memory.
