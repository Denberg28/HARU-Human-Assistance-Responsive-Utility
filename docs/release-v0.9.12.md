# HARU v0.9.12 — calmer lock-screen companion

v0.9.12 refines HARU's lock-screen experience after successful no-PIN pet interaction on HyperOS.

- Keeps the first lock-screen notification dedicated to HARU's face and gentle check-in.
- Tapping the **♡ HARU** action cycles to a visibly different cute HARU face and response.
- Extends the reaction display from about 2.5 seconds to about 8 seconds so the change is easy to notice.
- Adds a separate compact **Today** notification beneath HARU for open tasks.
- Shows up to two open task titles plus a remaining-count indicator; completed tasks are omitted.
- Shows **✓ Tasks clear** when there are no open tasks.
- Keeps the pet action local, silent, and authentication-free, with no app launch and no PIN request.
- Keeps the task notification read-only on the lock screen to avoid accidental edits or keyguard navigation.

## Installation security scan

Android/HyperOS may perform an additional security or package scan when installing a sideloaded APK. HARU remains signed with the existing protected release key; the OS-level scan is controlled by the device and is not bypassed by HARU.

## Physical-device verification

1. Install v0.9.12 over the existing HARU installation.
2. Lock and wake the phone.
3. Confirm HARU appears as the first notification and **Today** appears beneath it.
4. Expand HARU if needed and tap **♡ HARU**.
5. Confirm the HARU face and response visibly change and remain changed for several seconds.
6. Tap again and confirm another cute response appears without opening the PIN screen.
7. Add or complete tasks in HARU, relock the phone, and confirm the **Today** line reflects the current open tasks.
