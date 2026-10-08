# HARU query coverage and API economy — v0.9.39

Reviewed Android text/voice submission, local skills, Gemini/Groq/Antigravity requests, memory, model catalogue, quota handling, and the Streamlit/Python provider routes. This is a code and synthetic transport review, not an evaluation of every possible question. No production API key was used. The owner's billing tier, quotas, model access and actual POCO behaviour remain unverified.

HARU can send broad text questions to a capable model. It cannot guarantee every answer is correct, current, complete, or permitted by the provider. Model context alone is insufficient for live facts, inaccessible pages, personal account data and physical phone actions.

## Question coverage and cost control

| Question type | v0.9.39 route | Practical limit |
| --- | --- | --- |
| Exact device clock/date, supported calculations, saved tasks/reminders | Local utility; no generation call | Only recognized commands are local; Android arithmetic is exact for terminating decimal operations, otherwise marked approximate |
| Translation, stable explanations, short writing | One direct selected-model request; typically 640 output tokens | Short output cap; ask for detailed output when needed |
| Code review, debugging, mathematical reasoning, comparisons | One direct request; 2,400 output tokens and low reasoning on supported models | Supplied text must fit; low reasoning is an economy tradeoff, not a quality guarantee |
| News, weather, prices, schedules and detected sensitive/current facts | Direct Google Search grounding | Requires project tool entitlement/quota and returned HTTPS sources |
| Natural follow-up to recent live question | Search retained from bounded recent user turns | Heuristic topic detection; ambiguous or unrecognized phrasing may need explicit “search the web” |
| Summarize/read supplied HTTPS page | Direct Gemini URL context; no separate Search unless the question also asks for current verification | Requires successful provider retrieval metadata; paste inaccessible/paywalled page text |
| Explicit “deep research” in Antigravity Auto | One background task, 4,096 total-token budget, bounded polling, best-effort cancellation | May stop incomplete; no automatic continuation |
| Files/images, authenticated account pages, arbitrary device operations | No general attachment/account/action tool implemented in Android chat | Paste relevant text or use a supported device command; do not claim the action happened |

Search/source metadata confirms that retrieval occurred; it does not prove all model statements are supported. The existing Sources UI lists provider links, not sentence-level claim verification. Current-fact routing is a low-cost heuristic rather than an exhaustive classifier. Newly worded or multilingual requests can miss it; explicit search is the dependable override. User instructions still determine whether an answer should be short or detailed within the bounded request.

## Findings fixed

- Ordinary audits, investigations and source comparisons previously escalated to agents. Only explicit deep-research language starts an agent now; no classifier AI call is added.
- Routine Streamlit cloud questions previously attached web tools. Tools now follow the current query and bounded topic history. Confirmed deterministic utilities return immediately instead of paying for an AI paraphrase.
- Direct provider routes now have bounded output budgets: 256 for connection tests, 640 for short ordinary questions, 1,200 for normal/live/page requests, and 2,400 for detected reasoning/detail. These are maximums, not token usage predictions. Requests that hit the cap report it; no automatic retry is added.
- Android Groq Qwen uses reasoning effort `none` for routine queries and `low` for detected complex ones. Hidden format returns final text only; hiding reasoning alone does not save tokens.
- Android clock matching no longer intercepts flight schedules or time concepts. Decimal arithmetic avoids floating-point errors and integer overflow; divide-by-zero stays local.
- Android input preserves code indentation/newlines. Requests over 8,000 characters are blocked with visible guidance, rather than silently sending a truncated 2,000-character version. Large codebases still require smaller excerpts.
- The refreshed Google catalogue retains explicitly listed 2.5 Flash-Lite as an economy option. Android searches prefer that listed model; no speculative model probe is sent. Failed 2.5 search does not automatically switch to another search billing regime. If unavailable, the selected model is used.
- Session diagnostics show the last model and output cap alongside requests, reported tokens/search queries and quota responses. These are not an account bill or a hard monetary cap.

Existing safeguards remain: bounded local conversation memory, a shared single-flight request gate, 3-second spacing, persistent Android quota cooldowns, no automatic 429 retry, and one bounded transient Gemini fallback where allowed. Catalogue listing does not establish successful generation or tool entitlement. Android and Streamlit counters are separate; other callers on the same project are outside their visibility.

## Provider economics checked 8 October 2026 (Asia/Manila)

Standard paid text rates, USD per million tokens: Gemini 3.5 Flash-Lite $0.30 input/$2.50 output; 2.5 Flash-Lite $0.10/$0.40; Groq Qwen 3.8 27B $0.80/$4.00. Output billing can include reasoning. Illustratively, 100 requests of 1,000 input plus 600 output tokens cost $0.18, $0.034 and $0.32 respectively, excluding tools and extra reasoning. Actual prompts/history vary.

Google lists 3.5 Flash-Lite Search as unavailable on the API free tier. Paid 3.x shares 5,000 search requests monthly, then $14/1,000; a generation can issue multiple queries. 2.5 Flash-Lite lists up to 500 free-tier grounded requests daily, shared with 2.5 Flash and subject to project quota. Google restricts 2.5 access to existing active users. A zero tool quota can therefore reflect entitlement, not a usage spike. No free access or savings amount is guaranteed.

Sources: [Google pricing](https://ai.google.dev/gemini-api/docs/pricing), [Google model access/deprecations](https://ai.google.dev/gemini-api/docs/deprecations), [Groq Qwen parameters/pricing](https://console.groq.com/docs/model/qwen/qwen3.8-27b), [Google generateContent/URL metadata](https://ai.google.dev/api/generate-content), [OpenAI output caps](https://platform.openai.com/docs/api-reference/responses/create), [Ollama chat options](https://github.com/ollama/ollama/blob/main/docs/api.md). Prices and availability can change.

## Validation and remaining acceptance

Local validation: 25 Python tests, 3 Node tests, Python compilation and whitespace checks. Added Android regression coverage checks direct versus agent routing, adaptive budgets, Groq reasoning fields, verified URL retrieval, no inaccessible-page retry, listed economy model handling, multiline input, local clock matching and exact arithmetic. GitHub Android CI runs unit tests, release lint and APK assembly before merge; the main release workflow verifies the established signer and publishes the APK/checksum.

Install the signed v0.9.39 update over HARU, refresh models in Settings, and test a stable question, pasted code, a live question, a page summary and a local calculation using the owner's account. Compare HARU's counters with provider usage. Successful synthetic tests establish request behaviour, not live model answer quality or the owner's bill. A rigorous quality benchmark and observed cost per successful answer would be needed before claiming globally optimal API usage.
