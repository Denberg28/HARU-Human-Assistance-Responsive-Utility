HARU v0.9.0 moves HARU's caring presence from the Home-screen widget to an interactive lock-screen live wallpaper.

- Retires the Home-screen HARU widget, widget receiver, widget layout/resources, and old bubble-specific setup flow.
- Adds an Android live-wallpaper engine intended for the lock screen.
- HARU now has lightweight visual states: idle, running, resting, sleeping, and delighted.
- Tapping HARU's lock-screen bubble triggers the same short delighted/purring acknowledgement used by the caring checker.
- HARU sleeps automatically at night (22:00–05:59) and in Rest mode.
- Running/resting/idle states rotate deterministically without AI or network calls.
- The wallpaper animation loop runs only while Android reports the wallpaper as visible; no foreground service, wake lock, overlay permission, or background polling is added.
- The lock-screen bubble is more compact vertically (about 88 dp high with reduced padding) and is positioned below the typical clock area.
- In-app HARU checker padding is also reduced for a tighter card.
- Caring check-ins remain local and privacy-preserving: tasks/reminders are represented by counts, never their private text.
- Normal Tell HARU, reminders, Map, live location, AI providers, voice, encrypted memory, and stable Android signing remain separate and unchanged.
- Existing v0.8.0 check-in On/Paused preference is migrated to the new checker store.

Android limitation: live-wallpaper placement is controlled by the phone's wallpaper picker. HARU opens the Android live-wallpaper preview directly. Choose "Lock screen only" when your launcher offers it. Some OEM launchers may only offer Home screen or Home + Lock screen for live wallpapers. HARU does not request overlay privileges to bypass this platform restriction.

Battery design: rendering targets a modest 12.5 fps only while visible, uses simple Canvas text/shapes, performs no AI/network activity, and fully stops its frame callback when hidden.
