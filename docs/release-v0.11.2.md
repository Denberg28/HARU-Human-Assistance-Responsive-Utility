# HARU v0.11.2

- Fixed journal week classification: entries from a completed Monday–Sunday week collapse together. November 3, 5, and 6 now appear under **2 Nov – 8 Nov**, rather than being split between daily cards and a partial week.
- The current calendar week stays visible as days. Complete months roll up after their final date is four weeks old; complete years roll up after their final date is twelve months old. Expanding Year → Month → Week → Day preserves all dates and memorable stars. Crossing weeks retain the existing month-boundary clipping, with labels matching their dates.
- Kept archive identities stable when weeks move beneath months or years. Collapse all removes every expanded descendant; hidden stale expansion preferences no longer show a misleading collapse control. Removed outgoing-row fade remnants and corrected “1 day”.
- Journal and Finance refresh their calendar date on midnight, time/zone changes, and resume, without polling.

Install this APK over your current HARU installation to keep saved entries and settings. The application ID, database schemas, and release signing certificate are retained.

Regression coverage includes the reported November dates, Sunday/Monday transitions, complete-month/year promotion, leap days and 1,501 consecutive dates, expansion/filter behavior, editing an expanded date, collapse all, and time-zone changes. Android widget checks run on API 26 and 36 alongside the existing task, finance, reminder, map/location, storage/encryption, theme, and update checks. Physical-device acceptance is not performed by CI.
