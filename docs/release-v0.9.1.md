HARU v0.9.1 evolves the lock-screen live wallpaper into a visibly interactive animated HARU cat.

- Replaces the lock-screen emoticon-only representation with a lightweight Canvas-drawn cat.
- Sleeping now visibly breathes instead of remaining static.
- Idle HARU blinks and moves its tail.
- Running HARU moves across the lock screen with animated paws and bounce.
- Tapping/petting HARU triggers a delighted reaction for about four seconds, even during night-time sleep, then resumes the normal animation state.
- Accepts both raw wallpaper touch events and Android wallpaper tap commands for broader launcher/lock-screen compatibility.
- Keeps task/reminder details private; only safe summary text is shown.
- Disables wallpaper offset notifications because HARU does not use parallax.
- Rendering callbacks run only while Android reports the wallpaper as visible. When the lock screen/screen is not visible, the animation loop is removed, so there is no continuous off-screen drawing.
- No foreground service, wake lock, overlay permission, AI request, location request, or network activity is added for the animation.

Version 0.9.1 / code 36.
