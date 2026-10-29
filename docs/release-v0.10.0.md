# HARU v0.10.0 — Daily journal

The Android AI chat has been replaced with a private journal anchored to calendar dates.

- Write today or pick a past date. Edit an entry and mark a memorable date with a star; a Memorable filter finds starred dates.
- Recent seven dates remain visible. Older dates fold into weeks; buckets at least four weeks old fold into months, and buckets at least twelve months old fold into years. Expand Year → Month → Week → Day independently. Real calendar months are used, so a month may contain partial weeks rather than always exactly four weeks. Each date appears once.
- Stable item IDs, remembered expansion state, short list animations, and viewport anchoring keep expanding and collapsing controlled. Offscreen descendants are not composed.
- Entries autosave locally after a short typing pause, with a Save retry action. Journal text uses AES-GCM encryption backed by Android Keystore; dates and stars are app-private database metadata. Database transactions save text and stars together. Save/open errors are visible and do not silently clear stored content.
- AI providers, model menus, API tests, voice chat, and their Android runtime have been removed. Saved AI credentials are retired on upgrade. Legacy encrypted conversations are left untouched and are not displayed as journal entries.
- Tasks, task ordering, reminders, the local cat/check-ins, themes, map, and location sharing remain. Reminders now have a direct date/time form.

Install over existing HARU with the established signer to retain app data. Android backup remains disabled. Uninstalling removes local data. Journal text never enters map sharing, notifications, or an AI service.

The separate Streamlit research lab is unchanged; this release changes the Android application.

Validation: calendar hierarchy, leap-day/boundary coverage, unique stable IDs, nested expansion state, encrypted database save/edit/reopen/delete, and wrong-key/date protection tests accompany the existing Android regression suite. Physical-device animation and keyboard acceptance remain manual checks.
