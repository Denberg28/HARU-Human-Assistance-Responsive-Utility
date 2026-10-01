# HARU v0.9.33

## Launcher icon correction
- Rebuilds the HARU launcher icon from the original approved happy-cat artwork rather than a hand-drawn approximation.
- Uses Android adaptive icon resources so OEM launchers can apply their mask without shrinking a pre-rounded icon twice.
- Separates the dark #12171C background from the white cat foreground to preserve the intended proportions on round and squircle launchers.
- Removes the obsolete legacy `haru_launcher_icon` asset to prevent accidental reuse.

## Sanitization
- Standard and round manifest icon references now point to dedicated mipmap resources.
- Adds a launcher-resource regression test so future builds fail if the app falls back to the old drawable path.

## Verification
- Android unit tests, release lint, optimized APK build, release signature, and SHA-256 checksum are verified by the release workflow.
