# HARU v0.9.28

## AI provider resilience
- Antigravity Auto now exposes separate **Test Fast Gemini** and **Test Antigravity agent** diagnostics.
- This prevents HARU from reporting both paths as down when only the shared fast Gemini path is failing.
- Fast Gemini retries once with Gemini Flash-Lite only after transient HTTP 5xx server errors.
- HARU never performs that fallback on 429 quota/rate-limit responses or on 401/403 authentication failures.
- If HARU is already using Flash-Lite, no second fallback request is attempted.

## Diagnostics
- HTTP 5xx errors now include the exact status code and a bounded provider error detail when Google returns one.
- The existing v0.9.27 429 quota detail remains preserved.
- Diagnostic labels now identify which path actually failed.

## Locked baseline
- Map/GPS, restored task workflow, reminders, lock-screen HARU, themes, update flow, API-key encryption, and Gemini catalog optimization remain unchanged.
- No new background polling or automatic provider tests were introduced.
