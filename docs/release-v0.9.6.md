# HARU v0.9.6 — lock-screen-only companion

v0.9.6 refines HARU's interactive companion so it is shown only while the device keyguard is locked and never remains over the unlocked HARU app.

- Retires the Home-screen widget path from v0.9.5.
- Restores a private compact lock-screen HARU bar with keyguard checks.
- Starts the lock-screen companion after screen-off only when HARU check-ins are enabled.
- Keeps the bar hidden while the display is off and shows it only after Android reports the keyguard as locked.
- Closes the lock-screen companion immediately after successful device unlock.
- Prevents the companion window from dimming, dismissing the keyguard, keeping the screen awake, or drawing over the normal unlocked HARU app.
- Keeps touches outside HARU available to the system lock screen.
- Exposes the companion enable/disable state from the HARU interface.
- Uses HARU's existing local checker content, original face pack, resting/sleeping/running states and tap acknowledgement.
- No accessibility service, draw-over-other-apps permission, wake lock, full-screen notification or background AI request is added.
- Removes obsolete Home-widget classes/resources and adds regression coverage that the Home widget is no longer registered.

## Important Android limitation

This implementation uses a private `showWhenLocked` Activity rather than modifying the manufacturer's keyguard itself. Android does not provide ordinary apps with a universal permanent custom-widget slot inside every OEM lock screen.

## Physical-device checks

1. Enable HARU's lock-screen companion.
2. Lock the phone with the power button.
3. Wake the display without unlocking and verify the normal lock-screen wallpaper, clock and system controls remain visible.
4. Tap HARU and verify the acknowledgement reaction.
5. Swipe/tap outside HARU and verify the system keyguard remains usable.
6. Unlock and verify HARU disappears immediately and does not overlap the main HARU app.
7. Re-lock and verify HARU returns while the check-in setting remains enabled.
8. Disable the feature and verify the bar no longer appears.
