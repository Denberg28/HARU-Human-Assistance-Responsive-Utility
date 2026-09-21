HARU v0.7.1 repairs Home Bubble setup and adds an active, local companion inside the app.

- Home bubble setup now checks actual widget placement, explains launcher confirmation, and includes manual launcher instructions and a Check placement button.
- Tasks and reminders no longer suppress every idle conversation starter. Tap the face to cycle immediately; tap text to open a draft and press Send to chat.
- Inside HARU, greetings and expressions change every 20 seconds while idle and visible. Typing, dialogs, AI/voice work, Rest mode, and backgrounding pause automatic changes. Existing replies and drafts are preserved.
- Companion pause/resume persists. Widget refresh handles resizing, app updates, reboot, and clock/time-zone changes.
- Widget commands use a separate private receiver; launcher discovery remains available, actions use immutable PendingIntents, and idle behavior makes no background AI requests. Network response limits now apply while reading instead of after allocating the whole response.
- Map rendering now pauses/stops when HARU goes into the background and resumes on return.
- Reminder delivery checks the saved reminder, avoids duplicate/deleted alerts, preserves reminders when notifications are disabled, and recovers missed reminders after reboot or reopening. Alarm identities avoid hash collisions.
- Authenticated AI requests do not follow redirects. APK links must match the exact HARU repository, version and filename.
- Release builds use a protected stable HARU signing bundle and verify the built APK against the pinned HARU release certificate before publication.
- Retired lock-screen formatting and stale documentation are removed. Map functionality is retained.

Important install migration: v0.7.0 and earlier were built with an ephemeral CI debug signing identity that was not retained. Android therefore cannot accept v0.7.1 as an in-place update over those builds. Uninstall the older HARU build before installing v0.7.1. Because Android backup is disabled, uninstalling resets app-private HARU data. From v0.7.1 onward, the stable protected release key is intended to preserve normal upgrade compatibility.

After installing, open HARU → Home bubble → Add to Home screen and confirm Add. If your launcher shows no prompt, long-press Home → Widgets → HARU → HARU Bubble. Drag it to your preferred position.

Home widget updates are scheduled by Android about every 30 minutes and can be delayed by battery policy. This is a native Home widget, not an overlay over other apps. Tap for an immediate change.

Automated unit/widget tests and release lint/build run before publication. Physical POCO/HyperOS launcher placement and background timing still require testing on the device.

Security: the HARU release key is no longer stored in an Actions cache. CI requires the protected HARU_SIGNING_BUNDLE_BASE64 repository secret and verifies the configured keystore and final APK against the pinned public certificate SHA-256 fingerprint before publication.
