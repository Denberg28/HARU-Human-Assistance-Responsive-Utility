# HARU v0.7.1 function, battery and security review

Reviewed the v0.7.1 candidate at d3debff37a03092acb52b2f627dedc9c3980059a and applied the corrections below. Version remains 0.7.1 / code 30 because no 0.7.1 release had been published.

## Findings and changes

| Area | Finding | Resolution |
| --- | --- | --- |
| Home companion | Custom cycle/refresh commands shared the exported launcher provider | Dedicated non-exported action receiver; immutable PendingIntents retain widget clicks and pin confirmation |
| Battery | MapView stayed resumed when the Activity backgrounded while its composition remained mounted | Lifecycle binding forwards start/resume/pause/stop/destroy and closes once on disposal |
| Reminders | Delivery used stale Intent text, could re-notify deleted items, and removed reminders when the channel/app was muted | Resolve the saved ID and due time, keep blocked reminders, re-arm pending reminders on foreground/reboot/upgrade |
| Alarm identity | Java string hash collisions could replace another reminder | Unique URI identity, tagged notifications and cancellation of legacy alarm identities |
| AI transport | Authenticated connections allowed default HTTP redirect behavior | Redirects disabled for key-bearing catalog and AI calls |
| Update links | Download validation accepted any github.com repository | Require the exact HARU repository/version/asset path; reject credentials, alternate ports, queries and fragments |
| Release | Build job had write access and release signing was not durable | Read-only build job, separate publishing job, Python gate, protected stable signing bundle, pinned certificate verification and checksum verification |
| Signing | v0.7.0 and earlier relied on an ephemeral CI debug signing identity that was not retained | Establish a new stable HARU release key for v0.7.1 onward; remove signing-key cache fallback; require protected repository secret |

## Battery behavior established from code

- Native Home widget refresh interval is 30 minutes, scheduled by Android; taps update immediately. Android may delay periodic refresh. Pause hides content but does not cancel Android's provider-wide periodic callbacks.
- In-app expression rotation uses a 20-second coroutine only while resumed, idle and enabled. It pauses for typing, dialogs, busy states and Rest mode.
- Idle expressions and starters are local; they do not call AI or activate the microphone. Send is required for a remote chat request.
- No foreground service, overlay permission, background-location permission, wake-lock permission, exact-alarm permission or battery-optimization exemption is requested.
- GPS listeners and live-monitor jobs stop in Activity.onStop. Map rendering now follows the same lifecycle. A blocking network request already underway may finish within its configured timeout; coroutine cancellation is not immediate socket cancellation.
- Reminder wakeups are one-shot inexact alarms. Re-arming occurs on explicit foreground/system events, with no polling/retry service.

No physical battery measurements were performed. A percentage-per-hour or runtime claim would be unsupported. Measure on the target POCO with the same brightness/network conditions: baseline, widget idle, open idle, map and active chat, and compare batterystats over an equal duration.

## Security boundaries and remaining limitations

- Credentials and chat memory use Android Keystore AES-GCM. Tasks/notes/reminders are ordinary app-private preferences; do not describe all HARU storage as encrypted. Android backup and cleartext network traffic are disabled.
- Actual due reminders intentionally retain the existing public lock-screen visibility. Device notification privacy settings control display of their content.
- Online AI sends the user's prompt and bounded context to the selected provider. This review does not validate provider retention, quotas or live account access.
- The exported launcher activity can accept a bounded conversation draft but never automatically sends it. Widget content exposes counts/starters rather than task contents.
- v0.7.0's private signing key was not retained by CI and cannot be reconstructed from the APK. Android therefore cannot perform an in-place upgrade from that signer to the new v0.7.1 signer without the old private key.
- The v0.7.1 stable signing bundle must remain private and backed up. CI requires it as a protected repository secret and checks its public certificate fingerprint before signing.
- This is targeted source review and automated regression validation, not an independent penetration test or dependency vulnerability audit.

## Verification and device acceptance

The release workflow requires Python unit tests, Android unit/widget regressions on API 26/36, release lint, optimized release assembly, verification of the protected signing bundle against the pinned certificate fingerprint, APK signature verification, and checksum verification before publication. GitHub Actions is the authoritative record of final pass/fail outcomes.

New regressions cover private widget actions, map background/return/disposal, deleted/duplicate/muted reminder delivery, alarm collisions, missed reminders after reboot, and hostile update URLs. Existing tests cover memory bounds, response formatting, idle policy, content, widget placement/clicks/resizing and bounded response reads.

Device checks remain: clean-install v0.7.1 after removing an earlier signer-incompatible build; place and tap the widget in the POCO launcher; test reminder delivery with notifications allowed/blocked, reboot and Doze; background/return from Map; verify voice and one AI conversation with the user's configured account. See companion-verification.md for the full launcher checklist.

## References

- [Android widgets](https://developer.android.com/develop/ui/views/appwidgets): platform widget scheduling and host behavior.
- [MapLibre MapView lifecycle](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.maps/-map-view/index.html): lifecycle forwarding requirements.
- [GitHub encrypted secrets](https://docs.github.com/en/actions/security-for-github-actions/security-guides/using-secrets-in-github-actions): protected secret handling for workflow credentials.
