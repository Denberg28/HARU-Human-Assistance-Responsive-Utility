# HARU flexible-answer review — v0.9.40

## Original versus current behaviour

The original Android cloud implementation (`f080d46`) attached Google Search to every Gemini request, enabled both search and URL tools for agents, and allowed 12,000 agent tokens. It returned available model text without requiring source metadata. v0.9.38 introduced a strict source check; v0.9.39 kept it. That improved visibility into current-information failures but discarded potentially useful explanations whenever the provider returned no metadata. Groq live questions also stopped before generation when no Gemini key was saved.

This update restores useful broad text answers without restoring unconditional search or the large agent budget. It cannot make a provider answer every possible question correctly or access tools the account does not have.

## Behaviour and economy

| Situation | Result | Added generation calls |
| --- | --- | --- |
| Auto, stable question | One direct bounded request; save ordinary reply in encrypted rolling memory | None |
| Auto, current question with sources | Existing grounded request and Sources dropdown | None |
| Auto, current question without sources | Keep returned text, prefix an unverified notice; save question plus safe verification note | None |
| Groq, current question, no Gemini key | One Groq request with instructions to answer general parts and acknowledge unavailable live facts | None beyond the selected direct request |
| General knowledge | Direct selected-provider request, no search or background agent; label detected changing facts | None |
| Translation/rewrite/fictional news | Ordinary direct request unless explicit online lookup is requested | None |
| “Without search” in a request | Direct knowledge answer for that turn; label detected changing facts | None |
| Failed supplied-page retrieval | Keep explicit paste-text guidance; do not claim an invented page summary | None |
| Provider quota/auth/network failure | Existing bounded error handling; no automatic quota retry or added fallback loop | None |

Auto remains the migration default. The answer-mode preference is stored with existing settings; no key, task or memory migration is needed. Only detected changing-fact replies without retrieval lose their model assertions from memory. The retained question and verification note support follow-ups without presenting old claims as facts. General knowledge still requires a working provider and generation quota.

## Security and reliability review

The request gate and scope-specific cooldowns remain active for both modes. Google and Antigravity still share the Google cooldown. There is no AI classifier call, automatic source-repair request, hidden background refresh, 429 retry or new network host. The existing single transient Gemini fallback remains as previously bounded. Source UI still uses provider metadata rather than trusting links generated in model prose. Python source validation now rejects insecure schemes, embedded credentials and control characters; source titles are bounded and stripped of controls. Failed URL-context retrieval still blocks an invented page summary. The general assistant prompt forbids claiming completed app actions without confirmation from the local app flow.

The model text under an unverified label has not been fact checked; the notice is a transparency control, not proof of safety or accuracy. Heuristic topic detection remains imperfect, especially for ambiguous and multilingual queries. Knowledge mode does not establish current news, and ordinary modes do not grant account access or arbitrary phone actions.

## Verification

Local Python/Node tests and compile/whitespace checks precede Android CI. Added Android regression cases check persisted mode selection, direct requests instead of agents in knowledge mode, no Gemini requirement for Groq background explanations, response preservation without sources, safe memory text, source clearing, news follow-up classification, and explicit per-turn knowledge instructions. Existing security, quota, concurrency, saved-task and memory regression suites remain included. Production keys are not used by these tests; real account access, observed costs and POCO UI acceptance remain pending.
