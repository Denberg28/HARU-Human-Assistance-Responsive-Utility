# Live-news diagnosis and acceptance

The supplied screenshot shows a completed AI answer stating it cannot access real-time internet. That message is not HARU's network transport exception. A reply alone cannot establish the selected provider, original prompt, or Google's returned grounding metadata.

Code review of v0.9.37 found:

1. Keyword routing misses natural current-event questions and follow-ups. Ordinary Gemini/Antigravity lookups already enable Search for explicit `news`/`latest` prompts; keyword detection alone cannot explain every refusal.
2. Groq chat sends no search tool, regardless of the user's need for fresh facts.
3. The Android system prompt instructs use of local news tools which are not supplied to the AI request.
4. Responses discard grounding metadata and accept text without validating returned sources. A model refusal can be recorded as successful conversation memory.
5. Generic transport failures imply the user should check their internet, while provider/DNS failures can occur on an otherwise connected phone.

v0.9.38 addresses these paths with contextual routing, explicit date/search instructions, returned-source validation, source/suggestion UI and accurate failure messages. It does not force a model to issue a search or override Google project limits. Missing sources fail visibly without sending another generation.

Regression coverage uses synthetic transport responses and never requires real credentials: natural/Filipino questions; chained follow-ups and topic changes; stale refusal in memory; grounded sources and suggestions; missing evidence without retries; Groq ordinary chat vs Google search and shared quota cooldown; no-key guidance; source URL validation; local date; agent citations. Existing quota, cancellation, model fallback, unit/widget and lint checks remain required before release publication.

Device acceptance on POCO:

- Update in place; ask `What's happening to the Saudi fuel line?` and `Latest Saudi fuel news`. Expect a source-backed answer with source links or a precise search/quota error.
- Ask `Tell me more`, then `And why?`; search remains enabled. Switch to a technical explanation; no news search is added.
- Repeat with Gemini, Antigravity Auto and Groq. Groq live search uses the saved Gemini key; ordinary Groq chat stays on Groq.
- Expand Sources and open a publisher. Google Search Suggestions appear when returned. Ensure no stale sources remain after a new request, error or memory reset.
- Test true offline mode separately from a working connection with provider DNS/access failures. Check Settings request/token/search counters.

Google API grounding references: https://ai.google.dev/gemini-api/docs/generate-content/google-search?hl=en and https://ai.google.dev/api/generate-content .
