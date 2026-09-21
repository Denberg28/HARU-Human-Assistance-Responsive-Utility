# HARU v0.9.3 — original faces and interactive lock-screen session

The v0.9.2 wallpaper already enabled touch events. A phone's system lock screen can still withhold those events; enlarging the hit area cannot override it. This version removes the promise that wallpaper taps work on every phone.

- Restores the original HARU face and acknowledgement pack, removing the separately drawn full-body cat. Both lock-screen surfaces now share one renderer.
- Adds **Lock screen → Start interactive session**. Leave the session open, press the power button to lock, then wake the screen and tap HARU. The expression changes for 4.2 seconds, then returns to the sleeping/resting/running state.
- This is an explicitly started HARU screen above the device lock, not an always-present button on the manufacturer's lock screen. Close session returns to the system lock screen. Unlocking ends the session. Wallpaper taps remain dependent on the phone.
- The session has its own private task, keeps the existing device lock, and provides no access to tasks, messages, maps or settings. No background launch, overlay permission, accessibility service, wake lock or screen-on flag is added.
- Reduces the bubble height, keeps its tap area stationary, and measures text to prevent it colliding with the face. Screen-off and paused sessions stop frame callbacks. Wallpaper stops rendering when hidden or its surface is destroyed. Check-in content is cached between changes.

Validation: Android unit tests, lint and release assembly are required by the existing release workflow. Regression tests cover original faces, night-time acknowledgement, duplicate taps, disabled check-ins, card hit area, private task configuration, screen-off cleanup, and keeping the keyguard locked. Python runtime tests passed locally.

Physical-device validation is still required on the user's phone: lock during a session, wake without unlocking, tap HARU, verify the reaction, close and verify that PIN/fingerprint protection remains. Successful compilation or wallpaper preview alone does not establish locked-screen compatibility.
