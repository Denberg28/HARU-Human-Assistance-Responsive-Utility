# HARU v0.9.11 — lock-screen pet action

v0.9.11 fixes the lock-screen interaction path observed on HyperOS.

- Keeps HARU visible on the lock screen as a silent ongoing notification.
- Removes the notification-body content intent that could be interpreted by HyperOS as an app-open request and send the user to the PIN screen.
- Adds one dedicated **♡ HARU** action for a local pet/acknowledgement response.
- The pet action targets HARU's already-running foreground service, opens no Activity, and explicitly does not require authentication.
- Removes the automatic show-when-locked Activity launch from the wake path to avoid keyguard conflicts.
- HARU responds locally for about 2.5 seconds, then returns to the normal check-in.
- No keyguard dismissal, overlay, Accessibility Service, full-screen intent, or background AI request is used.

## Important Android/HyperOS behavior

The lock-screen notification row itself is owned by Android/HyperOS SystemUI. On a secure lock screen, the OS may treat tapping the notification body as a request to open the app and require the PIN. HARU cannot safely bypass that behavior.

For the no-unlock interaction, use the **♡ HARU** notification action. Depending on the lock-screen layout, expand the notification once to expose the action.

## Physical-device verification

1. Install v0.9.11 over the existing HARU installation.
2. Keep **Lock-screen HARU** ON, lock the phone, and wake the display.
3. Confirm the old “Tap HARU to acknowledge” text is gone.
4. Expand the HARU notification if necessary and tap **♡ HARU**.
5. Confirm HARU changes to a cute acknowledgement/purr without opening the PIN screen.
6. Repeated taps should trigger subsequent local reactions.
7. Unlock normally and confirm the HARU app itself was not launched by the pet action.
