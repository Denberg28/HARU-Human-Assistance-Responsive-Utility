# HARU Android review — v0.11.2

## Findings and fixes

| Area | Finding | Resolution |
| --- | --- | --- |
| Journal classification | Per-entry age filters split one calendar week between daily cards and a week header, and could similarly split a month/year. | Classify complete calendar buckets, then expose the appropriate level. |
| Archive state | Prefixing IDs with parents changed a week's identity when promoted into a month/year. Old hidden keys also kept Collapse all visible. | Canonical IDs independent of parent; show Collapse all only for visible expanded buckets. |
| Collapse rendering | Removed children retained fade-out surfaces briefly. | Keep placement animation and remove outgoing fade. |
| Date refresh | Journal/Finance only refreshed on resume. | Shared date observer for midnight, clock/zone changes, and resume; unregister on disposal. |
| Labels | Single-entry weeks displayed “1 days”. | Correct singular/plural summary. |

## Review scope

Reviewed Android calendar/archive UI and state, journal/finance stores and save paths, task ordering and reminder delivery, map/location background cleanup and sharing validation, theme and lock-screen lifecycle, update URL validation, manifest privacy settings, and the signed build/publication workflow. The separately retained legacy Python runtime and location-service contract are included in regression verification. No unrelated feature redesign is included.

Existing protections remain in place: transaction rollback on failed journal batches, authenticated encryption bound to date/row identity, explicit error/retry handling, app-private data with backup disabled, bounded network reads and trusted release URLs, foreground location lifecycle cleanup, immutable reminder intents, and original signer verification before publication.

## Verification

The release workflow runs all Android unit/Compose checks, release lint, optimized APK assembly, signature verification, artifact checksums, and publication. New regression tests exercise real expansion/collapse/filter/edit interactions and local calendar refresh on API 26 and 36. Existing storage tests cover encrypted reopen/edit/delete and rollback after write failure. Offline Python and Node suites are also required.

Physical-device launcher, OEM lock-screen behavior, actual GPS reception, and installation over the user's specific existing installation require device acceptance; these are not inferred from unit tests. Install over the current app, never uninstall as an update step.
