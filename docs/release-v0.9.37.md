# HARU v0.9.37

This update reduces unnecessary Gemini and Antigravity requests and improves quota errors.

- Routine chat and news lookups use a direct Gemini request with Search grounding when needed. Agent tasks are reserved for explicit multi-step research.
- Conversation context sent to providers is bounded. Direct answers allow up to 1,200 output tokens; connection tests allow 256. Agent tasks have a 4,096 total-token budget and agent diagnostics 512.
- One AI operation runs at a time, with a three-second interval between requests and tests. Gemini and Antigravity share a persistent local cooldown after HTTP 429; no quota retries or model hopping.
- Agent execution uses one create request and bounded status polling. Clearing the conversation or cancelling its job cancels the HTTP call and attempts server cancellation for a known background interaction.
- Direct Gemini uses minimal/low reasoning levels supported by the selected model to reduce unnecessary thinking. Truncated or empty responses due to a token limit are identified explicitly.
- Quota, access, unavailable-model, configuration and budget failures have separate messages. Provider error text is not echoed, avoiding accidental disclosure of keys or prompts.
- Settings shows app-session generation, polling, quota and reported token/search counts after requests.
- The Streamlit lab also routes simple agent-mode questions to Gemini, caps output and agent budgets, and gates requests after quota errors.

Install over the existing HARU APK to preserve local data. The approved logo and map/location-sharing behavior are retained.

Device acceptance still needs a POCO test with the owner's saved Google key and actual project quota. App usage counters cannot see other apps or keys, and reported tokens may omit failed requests. Google project quotas and service availability remain external dependencies.
