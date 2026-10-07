# HARU v0.9.38

Fixes live-news requests returning generic claims of no internet access while the phone is online.

- Recognizes natural current-event questions, Filipino news requests and bounded follow-ups; routine lookups remain a single direct Gemini generation.
- Corrects the Android system instruction that referred to unavailable local news tools. Search-enabled requests include device date/time and explicit instructions to use Search and distinguish event dates from publication dates.
- Requires provider-supplied HTTPS sources for live answers. Missing search evidence is reported as a search-result failure, without diagnosing the phone as offline or automatically retrying. Failed answers are not saved to conversation memory.
- Shows a compact Sources dropdown and provider Search Suggestions below grounded answers. Sources open on tap. Suggestions disable JavaScript, local-file/content access and mixed content, constrain embedded resources, and release their WebView on disposal.
- Groq retains ordinary chat; live requests use Gemini Search when a saved Gemini key is present. Settings identifies this route. Without that key, HARU gives setup guidance before sending an API request.
- Keeps Google quota cooldowns shared across Gemini, Antigravity and Groq's Google search route. No additional background generation, polling or retry loops.
- Network transport errors distinguish provider reachability and DNS validation from proven loss of internet.

Install over the existing HARU app to preserve tasks and settings. Device acceptance remains necessary with the owner's saved keys and current Google project quota. Internet connectivity alone does not guarantee Search results, model access, or available provider quota.
