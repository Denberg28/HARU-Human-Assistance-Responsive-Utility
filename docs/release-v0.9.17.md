# HARU v0.9.17 — UI cleanup, sanitization, and battery tuning

v0.9.17 follows the successful task-drag fix with a cleanup and reliability pass across HARU.

## Today / task UI
- Removes the redundant open-circle bullet from each task row.
- Keeps the ≡ drag handle as the single reorder affordance.
- Preserves stable task IDs, drag persistence, wrapped-text alignment, and tap-to-edit behavior.

## Theme
- Adds a persistent theme-color option in Settings.
- Includes Lavender, Blue, Green, Rose, and Amber.
- Applies the selected color immediately across Material controls and keeps the choice after restart.
- Falls back safely to Lavender if a stored preference is invalid.

## Security and surface reduction
- Removes the obsolete show-when-locked Activity path and its background launcher.
- Removes the unused animated lock-bar scene/motion code and its dedicated theme.
- Keeps the current lock-screen implementation on the private foreground notification service only.
- Keeps cleartext traffic disabled and app backup disabled.
- Keeps API credentials in the Android Keystore-backed encrypted credential store.
- Keeps lock-screen, reminder, and internal receiver/service components non-exported.

## Battery / runtime
- Keeps live GPS and live monitoring paused when HARU leaves the foreground.
- Reduces user-enabled live-location sampling from 2 s to 5 s.
- Reduces normal live uploads from a minimum 4 s cadence to 10 s.
- Extends the live heartbeat from 12 s to 30 s.
- Reduces live-monitor polling from 4 s to 10 s and allows exponential retry up to 60 s.
- Raises the movement threshold from 2.5 m to 5 m and accuracy-improvement threshold from 5 m to 8 m to avoid unnecessary radio/GPS work.
- The lock-screen companion remains event-driven by screen/keyguard events rather than polling.

## Validation targets
1. Verify task rows show only the ≡ handle and task text.
2. Reorder tasks and confirm state persists.
3. Lock/wake the phone and verify HARU still appears through the notification path.
4. Confirm no separate lock-bar Activity appears.
5. Start/stop live sharing and verify it pauses when HARU goes to background.
6. Confirm reminders, voice, map, update check, AI providers, and lock-screen pet action still function.
