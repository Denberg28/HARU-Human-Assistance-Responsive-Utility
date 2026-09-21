# HARU v0.9.5 — Home companion

v0.9.5 retires the lock-screen interaction experiment and moves HARU's persistent interactive presence back to the Android Home screen using a standard AppWidget.

- Removes the lock-screen Activity, renderer, motion policy, setup dialog, theme and related regression tests.
- Restores a compact horizontal HARU Home widget that the launcher can drag and resize.
- Widget placement uses Android's standard pin-widget flow; silent automatic placement is not possible on Android.
- Once pinned, the widget remains until the user removes it.
- Tapping the whole HARU bar triggers a local acknowledgement without opening HARU or starting AI/chat.
- Shortens acknowledgement duration from 1.4 s to about 650 ms for a faster, smoother response.
- Keeps widget action broadcasts private and PendingIntents immutable.
- Refreshes widget content after reboot/app update and when HARU data changes.
- Keeps task/reminder text private; only generic counts/check-ins may appear.
- No overlay permission, accessibility service, wake lock, foreground companion service or lock-screen replacement is used.

This design fixes the v0.9.4 overlap where the lock-screen bar could remain above the HARU app and interfere with normal controls.
