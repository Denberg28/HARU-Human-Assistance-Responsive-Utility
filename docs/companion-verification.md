# Companion verification — v0.7.1

## Defects found

- The v0.7.0 enabled preference was shown as On without querying bound widget instances. Pin requests ignored the Boolean result and had no success callback; unsupported launcher help was written behind an open Settings dialog.
- Idle lines were unreachable whenever any open task or future reminder existed.
- The in-app face was deliberately static; no idle conversation loop existed.
- Widget resize, time-zone changes, and app replacement were not handled.
- The launcher-visible widget receiver also accepted custom state-changing broadcasts from other apps. Custom commands now use a separate non-exported receiver.
- Four network readers limited text only after reading the entire response into memory.

## Implemented behavior

Use AppWidgetManager for actual placement, a dedicated setup dialog for all outcomes, a private action/callback receiver, and manual widget-picker instructions. Preserve the Android-controlled 30-minute update schedule. User taps get immediate local content updates. In-app idle rotation is lifecycle-scoped to RESUMED and stops while hidden, typing, busy, paused, or in Rest mode. Conversation starters produce editable drafts; only Send starts a request. No microphone activation, ongoing service, wake lock, overlay permission, or automatic network request is introduced.

Regression tests cover content with pending/overdue items, pause gating, placement status, widget inflation and clicks, compact sizing, app replacement refresh, receiver isolation, and bounded network reads. Robolectric runs widget tests at API 26 and API 36. These tests simulate Android; they are not a physical launcher test.

## Device acceptance checks (not executed in the build runner)

1. If v0.7.0 or an earlier build is installed, note any information you need, then uninstall it. The old CI signing key was not retained, so Android cannot update that build in place. Uninstalling resets HARU app-private data because backup is disabled.
2. Install v0.7.1 as the new stable-signing baseline.
3. Open Home bubble with no widget: status says Not on Home screen yet. Cancel Add: no false success.
4. Confirm Add, return to HARU, and Check placement: status says active. On a launcher without pin support, add from Widgets manually and verify detection.
5. Add a task, return Home, and tap the face at least four times: expressions and conversation starters still change. Tap text: HARU opens its primary tab with a draft. No message is sent until Send.
6. Start typing or receiving an AI reply; background and return through the widget: the draft/reply is not replaced. Return from the Map tab via widget: HARU tab is selected.
7. Inside HARU, idle for 20 seconds: expression or starter changes. Open a dialog, type, switch to Map, or background: automatic rotation pauses. Pause in Settings, restart HARU, and confirm the widget is still paused. Resume restores it.
8. Resize the widget; verify face/text stay legible at default and compact sizes. Check with large system text settings.
9. Reboot and reopen; verify widgets refresh, reminders remain scheduled, and taps work. Force-stop intentionally prevents Android background operation until HARU is opened again.
10. Check Home after about 30 minutes with normal battery policy. Record device model, launcher version, and battery mode if Android delays refresh. No exact periodic deadline is promised.

## Platform references

- https://developer.android.com/reference/android/appwidget/AppWidgetManager#requestPinAppWidget(android.content.ComponentName,%20android.os.Bundle,%20android.app.PendingIntent)
- https://developer.android.com/develop/ui/views/appwidgets/advanced
- https://developer.android.com/develop/ui/views/appwidgets/overview

## Release limitations

v0.7.0 and earlier were signed by an ephemeral CI debug key that was not retained, so their signing identity cannot be used for a future update. v0.7.1 establishes a new stable signing identity. CI requires HARU_SIGNING_BUNDLE_BASE64, verifies the decoded keystore against the pinned public certificate SHA-256 fingerprint, signs the release APK with that key, and verifies the APK fingerprint again before publication. The signing bundle must be kept private and backed up; losing it would again prevent normal Android upgrades.
