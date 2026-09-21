HARU v0.9.2 refines the interactive lock-screen HARU introduced in v0.9.1.

- Improves lock-screen tap reliability by reacting on touch-down instead of requiring a complete down/up gesture.
- Uses the Android wallpaper tap command as a second interaction path for broader launcher compatibility.
- Expands the tappable area beyond HARU's visible card and drawing, making petting less precise and more natural.
- Adds a short debounce window so duplicate touch + wallpaper tap events do not trigger multiple reactions.
- Replaces the filled dark cat silhouette with a lightweight outlined HARU drawing that better matches the app's line-art/chibi identity.
- Preserves idle blink, tail motion, running paws, sleeping breathing, and delighted reaction states.
- Keeps rendering visibility-gated and local-only: no wake lock, foreground service, overlay, AI request, location request, or network activity is added.
- Lock-screen task/reminder privacy remains unchanged; private text is never rendered.

Version 0.9.2 / code 37.
