# HARU v0.9.36 review

Reviewed the Android map, location capture and sharing, application lifecycle,
AI/voice cancellation, credentials and conversation storage, tasks/reminders,
lock-screen service, update URLs, manifest permissions, signing pipeline, and
the live-location Edge Function. This is a code and automated-test review;
physical phone behavior is not established by it.

| Finding | Resolution |
| --- | --- |
| GPS timeout was scheduled against wall time instead of uptime. | Use Android uptime and return the locator to idle on timeout without a fix. |
| Cached/shared fixes could lack accuracy or reliable age. | Require valid accuracy and monotonic age before publishing; validate imported times. |
| Duplicate creation and late network callbacks could overwrite stopped sessions. | Single creation guard, request/session ownership checks, and foreground checks. |
| Failed uploads could retry every sample and stationary shares lacked heartbeats. | Throttle attempts and sample stationary fixes; recover a lost response's sequence once. |
| Cancellation was treated as a network failure. | Propagate cancellation and prevent stopped jobs from overwriting newer upload state. |
| Expired/revoked monitoring continued polling. | Stop on terminal service responses and hide expired map markers. |
| Re-importing coordinates retained the older record. | Keep the newest snapshot at each coordinate. |
| Malformed code could be interpreted as a plain map snapshot. | Reject malformed labeled codes rather than downgrade the import. |
| Map errors were shown in AI status, and camera moves disrupted panning. | Report in Map and center when the focused marker changes. |
| Live-location project was inactive. | Restore the existing project; confirm ACTIVE_HEALTHY. |
| Service trusted Content-Length, allowed stale sequence updates, and hid stop errors. | Bound the actual body, use atomic sequence/expiry matching, and report stop failures. |
| README described a removed home widget and old signing cache. | Document the current lock-screen service, sharing limits, and protected signer. |

## Evidence

- All 12 existing offline Python runtime tests pass locally.
- Three Node service-contract tests pass locally, covering request bounds,
  authorization, conflicting updates, expiry, and revocation failures.
- A real synthetic encrypted share at coordinates 0,0 completed create/read/
  update/revoke verification. Incorrect tokens returned 403; stale sequence
  returned 409; reading the revoked session returned 404. Test data was removed.
- The live-session table has RLS enabled, with direct SELECT denied to both
  anon and authenticated roles. No direct client policy is intended: access is
  through the token-checked Edge Function.
- Android unit tests, release lint, optimized APK, and release-signature checks
  are CI gates; consult the release commit's workflow result.
- The high-confidence source secret scan and whitespace check pass.

## Remaining limits and phone checks

Check the POCO M6 Pro with precise and approximate location permissions, GPS
off/on, network interruption, enlarged text, task reordering, and transitions
to/from the background. Use a second phone to verify the live code, stationary
updates, Stop sharing, and automatic expiry. Publishing requires the sender app
to remain active. The service can become unavailable or be paused again under
its hosting plan. A shared Google Maps URL is a permanent snapshot copy.

The shared Supabase project also reports an existing anonymous-executable
`SECURITY DEFINER` function named `get_waypoint_telemetry_summary`, outside
HARU's location endpoint. Its access policy was preserved to avoid changing the
other application's behavior. See the
[Supabase remediation guidance](https://supabase.com/docs/guides/database/database-linter?lint=0028_anon_security_definer_function_executable).
