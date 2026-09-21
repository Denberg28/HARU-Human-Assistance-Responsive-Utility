HARU v0.7.1 repairs Home Bubble setup and adds an active, local companion inside the app.

- Home bubble setup now checks actual widget placement, explains launcher confirmation, and includes manual launcher instructions and a Check placement button.
- Tasks and reminders no longer suppress every idle conversation starter. Tap the face to cycle immediately; tap text to open a draft and press Send to chat.
- Inside HARU, greetings and expressions change every 20 seconds while idle and visible. Typing, dialogs, AI/voice work, Rest mode, and backgrounding pause automatic changes. Existing replies and drafts are preserved.
- Companion pause/resume persists. Widget refresh handles resizing, app updates, reboot, and clock/time-zone changes.
- Widget commands use a separate private receiver; launcher discovery remains available, and actions use immutable PendingIntents, and idle behavior makes no background AI requests. Network response limits now apply while reading instead of after allocating the whole response.
- Map rendering now pauses/stops when HARU goes into the background and resumes on return.
- Reminder delivery checks the saved reminder, avoids duplicate/deleted alerts, preserves reminders when notifications are disabled, and recovers missed reminders after reboot or reopening. Alarm identities avoid hash collisions.
- Authenticated AI requests do not follow redirects. APK links must match the exact HARU repository, version and filename.
- Release builds verify the APK signature against v0.7.0 and publish only after Android and Python checks pass. Build jobs have read-only repository access; publishing has a separate permission scope.
- Retired lock-screen formatting and stale documentation are removed. Map functionality is retained.

After installing, open HARU → Home bubble → Add to Home screen and confirm Add. If your launcher shows no prompt, long-press Home → Widgets → HARU → HARU Bubble. Drag it to your preferred position.

Home widget updates are scheduled by Android about every 30 minutes and can be delayed by battery policy. This is a native Home widget, not an overlay over other apps. Tap for an immediate change.

Automated unit/widget tests and release lint/build run before publication. Physical POCO/HyperOS launcher placement and background timing still require testing on the device. Install over the existing app to preserve your local data; do not uninstall as a troubleshooting step.

Security limitation: this update preserves the existing signing identity. CI prefers the protected HARU_SIGNING_KEY_BASE64 secret when configured, but retains the legacy cached-key fallback for upgrade compatibility. Cached signing keys are not secure production key storage. The repository owner must move the same key into the protected secret and remove the legacy cache; production signing migration remains open. No claim of compromise is made.
