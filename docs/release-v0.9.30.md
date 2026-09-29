# HARU v0.9.30

## AI quota safeguards
- Routes ordinary current-information questions through fast Gemini with search, reserving Antigravity agent for explicit research or web actions.
- Reduces Antigravity agent token budget and keeps its automatic web tool set small.
- Prevents overlapping chat requests and connection tests. Connection tests consume real model quota.
- Maintains local task/reminder handling without model requests.

## Verification
- Validate HARU and Android APK build passed on the change branch.
- The v0.9.30 APK build must complete before distribution.

AI Studio's 28-day dashboard displays peak usage. It cannot attribute a historical token spike to one HARU request; Google request logs are required for that.
