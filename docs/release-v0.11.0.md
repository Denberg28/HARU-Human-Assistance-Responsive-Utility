# HARU v0.11.0

- New **Finance** tab: quickly record income and expenses in Philippine pesos, with a date, category, and optional note.
- See income, expenses, and net for a selected month or all time. Net reflects recorded entries, rather than a bank balance.
- A compact spending chart below the totals shows the four largest expense categories and combines the remainder. Labels and percentages accompany the chart.
- Categories start collapsed. Tap to expand their entries, edit an entry, or delete it with confirmation. Custom category names are supported; income and expenses stay separate even when category names match.
- Monetary calculations use exact centavos. Invalid, zero, negative, and overprecision amounts cannot be saved. Newest entries appear first within each date.
- Financial fields are encrypted on the phone with a separate Android Keystore key. Successful disk writes update totals; failed saves keep the editor open, and failed reads do not clear stored records.

Install over your current HARU app to preserve your journal, tasks, reminders, and settings. Finance works offline. Maps and explicit location sharing retain their existing connectivity requirements.

Verification: finance calculation and date-filter tests, encrypted database reopen/edit/delete and failure tests, and Compose category/form tests on API 26 and 36 are included in the Android validation workflow. Physical-device testing remains a separate acceptance step.
