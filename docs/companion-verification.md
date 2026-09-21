# HARU checker verification — v0.9.0

## Behavior model

HARU's caring presence is no longer a Home-screen AppWidget. v0.9.0 uses an Android live-wallpaper service intended for the lock screen.

The checker is local-only:
- no automatic AI request;
- no microphone activation;
- no foreground service;
- no wake lock;
- no overlay permission;
- no background polling.

Normal Tell HARU, Mic, reminders, Map, and online AI remain separate explicit actions.

## Lock-screen states

- **Idle:** caring check-in and normal HARU face.
- **Running:** HARU moves horizontally with alternating running frames.
- **Resting:** calm face with the current local check-in.
- **Sleeping:** used automatically from 22:00 through 05:59 and whenever Rest mode is active.
- **Delighted:** tapping HARU's bubble produces a short purring/acknowledgement reaction.

The wallpaper uses a compact bubble and a day/night background. Fast redraws are used only for running/delighted states; static states use a one-second heartbeat. All rendering callbacks stop when Android reports the wallpaper as not visible.

## Device acceptance checks

1. Install v0.9.0 over v0.8.0 or any release using the stable v0.7.1+ signer.
2. Open HARU → **Lock screen** → **Set HARU as live wallpaper**.
3. In the Android/HyperOS wallpaper preview, choose **Lock screen only** if the phone offers it. Some OEM wallpaper pickers only offer Home or Home + Lock; this is an Android/OEM limitation and HARU does not bypass it with overlays.
4. Lock the phone and confirm HARU appears below the typical clock region without covering the main clock.
5. Tap HARU's bubble. Confirm a brief delighted/purring reaction, then a return to the checker state.
6. Observe long enough to see idle, resting, and running states. Running should remain inside screen bounds.
7. At night (22:00–05:59), or after selecting Rest mode, confirm HARU shows the sleeping state.
8. Pause check-ins from HARU settings. Confirm the live wallpaper background remains but HARU's checker bubble is hidden. Resume and confirm it returns.
9. Add tasks/reminders. Check-ins may show counts, but must never display task/reminder text on the lock screen.
10. Verify Tell HARU, Mic, Today, reminders, Map, and location sharing still work independently.
11. Check battery behavior over an equal-duration comparison. No runtime/battery percentage is claimed without physical measurement.

## Automated gates

CI requires:
- Python runtime validation;
- Android unit tests;
- checker content/cadence/store migration tests;
- lock-screen service manifest registration test;
- release lint;
- optimized APK assembly;
- stable release signing and APK signature verification;
- SHA-256 checksum verification before publication.

## Platform limitation

Android live wallpaper is the supported interactive animated surface used here. The phone's wallpaper picker controls whether a live wallpaper can be assigned to Lock only, Home only, or both. HARU opens the system live-wallpaper preview and does not request overlay privileges to circumvent that choice.
