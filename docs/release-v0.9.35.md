# HARU v0.9.35

## Launcher icon fit refinement
- Reduces the approved HARU cat foreground from 72% to 66% around the icon center.
- Adds more dark margin so the cat sits comfortably inside the launcher tile and visually matches the TeleRC icon scale more closely.
- Applies the same 66% scale to adaptive, standard, and round launcher resources.

## Sanitization
- Keeps the approved happy-cat artwork and adaptive-icon structure unchanged.
- Changes only icon foreground scale and release version; HARU runtime features are untouched.

## Verification
- Android unit tests, release lint, optimized APK build, release signature, and SHA-256 checksum are verified by the release workflow.
