# HARU v0.9.23

## Map and GPS reliability
- Uses every usable Android location provider instead of relying on only the first enabled provider.
- Uses network location and GPS together when precise permission is available.
- Keeps approximate-location operation usable when only coarse permission is granted.
- Distinguishes **Locating**, **Location ON**, **GPS**, and **Approximate** states instead of reporting GPS success before a fix exists.
- Prefers fresh cached fixes over older but more accurate fixes.
- Refuses stale or excessively inaccurate locations for new location shares.
- Live location publishing receives the same provider-fallback improvement.
- Location listeners remain foreground-only and are removed when HARU leaves the foreground.

## Reliability and feature audit
- Lock-screen enablement now requests Android notification permission when required instead of silently running without a visible notification.
- Existing task ordering, reminders, Daily Pulse, AI chat actions, voice, MapLibre lifecycle, app update flow, themes, and lock-screen service were reviewed for regression risk.
- Added regression tests for GPS/network provider selection and coordinate validation.
- Added manifest-security tests to enforce private receivers/services and disabled Android backup.

## Security review
- API credentials and conversation memory remain protected by Android Keystore-backed AES-GCM storage.
- Cleartext network traffic remains disabled.
- Reminder and lock-screen components remain non-exported.
- Release APK publication remains protected by the existing pinned signing certificate and CI signature verification.
- App update discovery remains restricted to the HARU GitHub release path and HTTPS.
- No new background polling or persistent GPS work was introduced.
