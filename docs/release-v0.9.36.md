# HARU v0.9.36

## Interface
- Compact labeled dropdowns for accent, background, AI provider, and Gemini model.
- Map sharing adds a 15/30/60/120-minute duration selector, preparing/cancel states,
  and a bounded share-package field. Duplicate Start taps are blocked.
- Map permission, import, and acquisition errors appear in the Map tab.
- Marker updates preserve manual panning; expired imported markers are hidden.
- Map loading failures show a connection message without hiding sharing controls.

## Location reliability and privacy
- Fix GPS timeout scheduling to use Android's uptime clock. Check GPS age with
  elapsed realtime and require a recent fix with valid accuracy before sharing.
- Prevent late create/upload/read callbacks from reactivating stopped sessions.
- Preserve coroutine cancellation, throttle failed upload attempts, and sample
  stationary fixes so heartbeat updates can still run.
- Stop monitoring on revoked, missing, unauthorized, or expired sessions.
- Reject malformed live/signed codes without silently falling back to a snapshot
  link. Validate timestamps and replace older snapshots at the same coordinates.
- Report remote revocation failures accurately; local publishing still stops.

## Service and sanitization
- Restore the existing inactive live-sharing project.
- Version the location Edge Function in the repository. Bound actual request
  bytes, validate duration, authorize before expired-row deletion, and require
  an atomic sequence match for updates. Report revocation errors.
- Correct stale companion, sharing, and signing descriptions in README.
- Keep the approved 66% launcher icon and existing credential encryption,
  task/reminder persistence, lock-screen controls, and AI quota safeguards.

## Verification and limits
- Existing Python runtime tests and location-service contract tests run before
  publication. Android unit tests, release lint, optimized APK build, signer
  fingerprint, APK signature, and SHA-256 are checked by CI.
- New regression coverage includes encrypted share round trips, malformed codes,
  snapshot replacement, monotonic fix age, timestamp validation, cancellation,
  token checks, expiry, conflicting updates, and revocation failures.
- Live sharing still requires the hosted service and an active sender app.
  Codes grant location access to whoever holds them; sender identity is not
  independently verified. A Google Maps snapshot link cannot be revoked.
- Physical POCO M6 Pro checks of GPS, two-phone sharing, enlarged text, and
  background/foreground transitions remain required after installation.
