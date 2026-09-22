# HARU v0.9.8 — HyperOS lock-screen compatibility

v0.9.8 addresses the remaining lock-screen failure seen on Xiaomi/POCO/Redmi devices and cleans up duplicated controls.

- Separates Lock-screen HARU from Check-ins so each setting works independently.
- Keeps Lock-screen HARU enabled by default.
- Listens for both screen-off and screen-on keyguard events, so HARU gets a second launch opportunity when the locked display wakes.
- Retains the modern Android PendingIntent background-launch path added in v0.9.7.
- Adds a direct App settings shortcut and HyperOS/MIUI guidance for the OEM permissions that can block lock-screen presentation.
- Removes the duplicate Clear AI memory control from Settings. Clear memory remains available on the main HARU page.
- Keeps the lock-screen activity private, keyguard-gated, transparent, and hidden immediately after unlock.
- Does not add draw-over-other-apps, Accessibility Service, wake lock, keyguard-dismiss, or full-screen-intent permissions.

## Xiaomi / POCO / Redmi physical-device setup

On HyperOS/MIUI, Android's normal showWhenLocked API can still be blocked by Xiaomi's app-specific permission layer.

1. Open HARU > Settings > Lock-screen HARU.
2. Tap Open phone app settings.
3. Open Other permissions.
4. Enable Show on Lock screen.
5. If the option exists, also enable Display pop-up windows while running in the background.
6. Return to HARU, lock the phone, then wake the display without unlocking.

## Verification

- Lock-screen HARU and Check-ins can be toggled independently.
- Clear memory appears only on the main HARU page.
- The HARU lock-screen bar is still gated by the actual Android keyguard state.
