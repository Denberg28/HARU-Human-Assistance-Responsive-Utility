# HARU v0.9.34

## Launcher icon scale correction
- Reduces the approved happy-cat foreground to 72% around the icon center so it stays inside Android's adaptive-icon safe zone.
- Restores the intended dark border around the cat instead of letting OEM launchers crop the ears and cheeks.
- Applies the same scale to adaptive and legacy launcher resources for consistent appearance across Android launchers.

## Sanitization
- Keeps the approved v0.9.33 artwork and mipmap/adaptive-icon structure unchanged.
- Changes only launcher artwork scale and the release version; no HARU runtime features are modified.

## Verification
- Android unit tests, release lint, optimized APK build, release signature, and SHA-256 checksum are verified by the release workflow.
