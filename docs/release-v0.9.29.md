# HARU v0.9.29

## DNS and network resilience
- Keeps Android/system DNS as the primary resolver.
- Retries a failed system DNS lookup once after a short delay.
- Falls back to secure DNS-over-HTTPS only when Android DNS still cannot resolve the provider hostname.
- Uses Google DoH with bootstrap IP addresses so the fallback itself does not depend on the failing system DNS path.
- HTTPS requests continue to use the original provider hostname, preserving TLS certificate and SNI validation.
- Applies the resilient DNS path to Gemini, Antigravity, model-catalog updates, and Groq requests.

## Clearer diagnostics
- Adds Android network-state awareness.
- Distinguishes **no validated internet connection** from **DNS lookup failure**.
- Removes raw Java `UnknownHostException` text from the user-facing AI status.

## Preserved safeguards
- Existing 429 quota diagnostics remain unchanged.
- Existing single Flash-Lite fallback remains limited to transient Gemini 5xx responses only.
- No fallback is added for quota or authentication failures.
- Map/GPS, task workflow, lock-screen HARU, reminders, themes, and update behavior are unchanged.

## Regression coverage
- Verifies system DNS success does not invoke secure DNS.
- Verifies system DNS is tried twice before secure fallback.
- Verifies final DNS failure is propagated when both resolvers fail.
