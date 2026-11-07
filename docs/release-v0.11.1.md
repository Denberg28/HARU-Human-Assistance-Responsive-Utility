# HARU v0.11.1

- Added a clearly labelled income-versus-expenses bar graph below the totals. Both bars share the same scale and zero baseline, with peso amounts and a short explanation of the difference.
- The comparison remains useful with just one expense category or income-only records. The existing category spending breakdown remains below it.
- Left-aligned the month with the finance content and moved previous/next controls to the right.

Install over your current HARU app to keep your entries. No database migration is needed.

Regression checks cover the reported ₱700 income / ₱15,000 expense example, zero income, an empty period, and month alignment on Android API 26 and 36. Physical-device acceptance remains a separate step.
