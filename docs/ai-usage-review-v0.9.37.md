# HARU AI usage review — v0.9.37

## Evidence and limits

Reviewed the Android call sites, provider transport, routing, memory limits, diagnostics, Python runtime, Streamlit triggers and CI. This review uses source and fake-provider regression tests. The owner's Google AI Studio usage chart, project quota metrics and phone network history were unavailable. No production Gemini key was read or used to generate test traffic. This review does not establish an account-level cause of the reported spike or prove a credential has never been copied elsewhere.

Google's Gemini API quotas apply per project, not per API key. RPM, input TPM and RPD are independent; exceeding any can produce 429. Daily request quotas reset at midnight Pacific. Gemini managed-agent work and Android chat use the same saved API key. Antigravity IDE subscription allowances are a separate product and should not be treated as the API project's remaining quota.

Primary references checked on 2026-10-07:
- https://ai.google.dev/gemini-api/docs/rate-limits
- https://ai.google.dev/gemini-api/docs/antigravity-agent
- https://ai.google.dev/gemini-api/docs/background-execution
- https://ai.google.dev/api/interactions-api
- https://ai.google.dev/gemini-api/docs/google-search
- https://ai.google.dev/gemini-api/docs/generate-content/thinking

## Findings and changes

| Trigger | Previous behavior and consequence | New behavior |
| --- | --- | --- |
| Simple “search the web” question | Escalated to an autonomous Antigravity loop | Direct Gemini with Google Search |
| “today”, “current”, “score” in ordinary tasks/technical explanations | Broad routing could enable Search without a live-data need | Narrower lookup/current-information patterns |
| Repeated send or connection-test taps | Activity guarded active jobs, but no shared manager gate or interval | Manager rejects overlapping operations; three-second spacing |
| HTTP 429, then another test/provider choice | Repeated explicit calls were allowed immediately | Gemini and Antigravity share a persisted cooldown; Retry-After/RetryInfo honored; structured daily quota waits for Pacific reset |
| HTTP 5xx | One Gemini fallback already existed | Preserve exactly one Flash-Lite fallback; retain search requirement; never fallback on 429/auth/configuration errors |
| Network trouble during generation | OkHttp connection recovery could repeat a POST; coroutine cancellation did not cancel a synchronous call | Disable automatic connection retries and redirects; asynchronous call tied to coroutine cancellation |
| Long conversations | Up to five exchanges plus recap resent; one exchange can contain 20,000 characters | Last three exchanges capped at 6,000 characters combined plus 1,200-character recap; agent gets only the newest exchange capped at 1,000 characters and a 500-character recap, with current prompt/instructions capped at 6,500 characters combined |
| Agent continuation | Previous server interaction/environment could bring an expanding 20-hour context; rejected continuation could trigger a second create | Fresh bounded task snapshot, no hidden recreation, no unbounded server continuation |
| Agent budget and response parsing | 1,500 total tokens could be exhausted by input/reasoning; intermediate outputs/no final text treated as generic errors | 4,096 token ceiling for deliberately selected research; explicit incomplete/failed/action status; last model output only |
| Agent request lifetime | One synchronous 75-second request; server work could outlive disconnection | One background create, at most 15 GET polls, 120-second limit, best-effort cancel for a known ID |
| Newer Flash default reasoning | Medium reasoning could exhaust the output budget before final text | Minimal/low thinking according to the known model's supported levels; future/legacy defaults retained; explicit response-budget errors |
| Diagnostics | Gemini test allowed 1,200 output tokens; agent test inherited default tools when tools omitted | Gemini test 256; agent test 512 with explicit empty tool list |
| Provider errors | Arbitrary provider message displayed | Fixed bounded errors; structured retry metadata only |
| Streamlit lab | Agent up to 12,000 tokens (test 2,500), direct Gemini output uncapped, no quota gate | Simple requests route to Flash-Lite; 4,096/512 agent budgets; 1,200/256 direct output; per-key in-process gate and cooldown; bounded polling/cancellation |

The Android agent ceiling was increased only for explicit research because the prior 1,500 total-token ceiling could be consumed before answering. Routine lookups no longer pay for an agent loop. This is a bounded tradeoff, not a promise of percentage savings. Agent budgets are best-effort Google controls and include input, output and thinking. Direct output limits also include model thinking where applicable, so complex requests can still hit a budget.

No timer, lock-screen companion, map listener, app startup hook or CI job was found generating automatic Gemini chat traffic. Model listing remains an explicit user action. Streamlit generation and connection tests are triggered by chat/actions/buttons; reruns alone do not issue those calls. CI regression tests use fake transports and synthetic keys.

## Diagnostics and follow-up

Settings status reports cumulative app-session generation attempts, GET polls, cancel attempts, quota responses, reported tokens and reported search queries. It stores no key, prompt, response, location or interaction ID in diagnostic logs. Counts reset on process restart and do not cover other apps, API keys, unknown server work or requests whose usage metadata was absent. Agent tool-query counts are not inferred when the provider does not supply them.

To identify the actual spike, compare an AI Studio usage window against these phone counters, checking RPM, TPM, daily requests and Search quotas for the actual project/model. A low count on the phone plus a high project count indicates another caller or unknown prior work; it alone does not prove a leak. An error with zero permitted quota can mean model/tool/tier access rather than heavy use. Do not rotate keys to bypass project limits. If usage remains unexplained, revoke the affected key in AI Studio and save a new restricted key in HARU; changing keys within the same project does not reset quota.

Cooldowns prevent repeated requests locally. Android persists a shared Google cooldown across provider changes/restarts. Streamlit's cooldown is in process memory per key fingerprint; it cannot identify which different keys belong to the same project. Server cancellations are best effort; loss of the create response may leave no known ID to cancel. Pending agent work may survive an unreachable cancellation endpoint.

## Acceptance

Automated checks cover route choice, payload bounds, single-flight admission, shared persisted quota cooldown, metadata parsing, preservation of one server fallback, diagnostic token limits, thought filtering, one agent creation with backoff polling, cancellation, bounded polls and Python equivalents. Android lint and release build run in the pull request workflow. Main builds use the existing protected signer and checksum-verified publication.

Physical POCO acceptance remains pending: one normal message, one latest-news request, repeated test taps, a quota error followed by a blocked test, clearing memory during research, and a provider switch while a request completes. The owner must verify source freshness and actual project usage with their own key.
