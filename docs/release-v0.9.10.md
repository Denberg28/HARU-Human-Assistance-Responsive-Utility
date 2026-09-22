# HARU v0.9.10 — lock-screen companion notification

v0.9.10 changes the primary lock-screen presentation to the Android notification surface that is already confirmed to render on the user's HyperOS lock screen.

- Promotes the persistent HARU notification from a background-runtime status message into the actual lock-screen companion.
- Shows HARU's rotating face, greeting, and check-in directly on the lock screen.
- Makes the whole HARU notification tappable for acknowledgement/petting without opening the main app.
- Shows a short acknowledgement reaction, then returns to the normal HARU check-in.
- Uses a new public lock-screen notification channel so the previous low-importance channel configuration cannot suppress the new companion presentation.
- Keeps the separate showWhenLocked activity as a best-effort richer fallback on devices that permit it.
- Keeps the service silent, non-badged, ongoing, and user-controlled by the Lock-screen HARU toggle.
- Keeps HyperOS permissions guidance in place.
- No draw-over-other-apps, Accessibility Service, wake lock, keyguard-dismiss, or full-screen-intent permission is added.

## Why this changes the architecture

The device screenshots confirmed that the foreground service notification reaches the lock screen, while HyperOS continues to block the separately launched showWhenLocked activity even with its OEM permissions enabled. v0.9.10 therefore uses the supported surface that is already proven on-device instead of treating it only as an implementation detail.

## Physical-device verification

1. Install v0.9.10 and open HARU once.
2. Keep Lock-screen HARU ON.
3. Lock the phone and wake it.
4. Confirm the notification now shows HARU's face plus a greeting/check-in instead of "Ready while Lock-screen HARU is on."
5. Tap the HARU notification and confirm the text changes briefly to an acknowledgement such as a purr/happy reaction.
6. Unlock and confirm HARU remains only as the normal silent runtime notification in the notification shade.
7. Turn Lock-screen HARU OFF and confirm the companion notification disappears.
