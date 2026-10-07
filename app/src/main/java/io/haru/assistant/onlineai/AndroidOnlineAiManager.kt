package io.haru.assistant.onlineai

import io.haru.assistant.util.readBoundedText
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.haru.assistant.memory.AntigravitySession
import io.haru.assistant.memory.ConversationExchange
import io.haru.assistant.memory.ConversationMemoryPolicy
import android.os.SystemClock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

enum class OnlineProvider {
    ANTIGRAVITY,
    GEMINI,
    GROQ,
}

data class GeminiModel(
    val id: String,
    val label: String,
)

data class OnlineAiSettings(
    val provider: OnlineProvider = OnlineProvider.ANTIGRAVITY,
    val geminiModel: GeminiModel = AndroidOnlineAiManager.FALLBACK_GEMINI_MODEL,
)

data class OnlineAiReply(
    val text: String,
    val antigravitySession: AntigravitySession? = null,
)

private class ProviderHttpException(
    val statusCode: Int,
    message: String,
) : IllegalStateException(message)

class AndroidOnlineAiManager internal constructor(
    context: Context,
    private val testTransport: (suspend (Request, Int, Int) -> AiHttpResponse)?,
    private val credentialRead: ((String) -> String)?,
    elapsedMs: () -> Long,
) {
    constructor(context: Context) : this(context, null, null, { SystemClock.elapsedRealtime() })
    private val appContext = context.applicationContext
    private val preferences =
        appContext.getSharedPreferences("haru_online_ai", Context.MODE_PRIVATE)
    private val credentials by lazy { SecureCredentialStore(appContext) }
    private fun credential(name: String): String = credentialRead?.invoke(name) ?: credentials.get(name)
    private val httpClient =
        OkHttpClient.Builder()
            .dns(HaruResilientDns.create())
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

    private val requestGate = AiRequestGate(elapsedMs)
    private val stats = AiRequestStats()
    private var activeScope = "gemini"

    fun usageDiagnostics(): String = stats.description()

    private suspend fun <T> guarded(scope: String, block: suspend () -> T): T =
        requestGate.run(scope) {
            activeScope = scope
            val remaining = preferences.getLong("quota_until_$scope", 0L) - System.currentTimeMillis()
            check(remaining <= 0L) { AiRequestPolicy.waitMessage(scope, remaining) }
            block()
        }

    init {
        if (credentialRead == null) credentials.delete("openrouter")
        if (preferences.getString(KEY_PROVIDER, "") == "LOCAL_ONLY") {
            preferences.edit()
                .putString(KEY_PROVIDER, OnlineProvider.ANTIGRAVITY.name)
                .apply()
        }
    }

    fun settings(): OnlineAiSettings {
        val provider = when (preferences.getString(KEY_PROVIDER, "")) {
            OnlineProvider.GEMINI.name,
            "GEMINI_FLASH_LITE" -> OnlineProvider.GEMINI
            OnlineProvider.GROQ.name -> OnlineProvider.GROQ
            else -> OnlineProvider.ANTIGRAVITY
        }

        val catalog = geminiModels()
        val storedId = preferences
            .getString(KEY_GEMINI_MODEL, FALLBACK_GEMINI_MODEL.id)
            .orEmpty()
        val selected = catalog.firstOrNull { it.id == storedId }
            ?: catalog.first()

        return OnlineAiSettings(provider, selected)
    }

    fun geminiModels(): List<GeminiModel> {
        val raw = preferences.getString(KEY_GEMINI_CATALOG, "").orEmpty()
        if (raw.isBlank()) return listOf(FALLBACK_GEMINI_MODEL)

        val cached =
            runCatching {
                val array = JSONArray(raw)
                buildList {
                    for (i in 0 until array.length()) {
                        val item = array.optJSONObject(i) ?: continue
                        val id = item.optString("id").trim()
                        val label = item.optString("label").trim()
                        if (
                            SAFE_MODEL_ID.matches(id) &&
                            label.isNotBlank() &&
                            label.length <= MAX_MODEL_LABEL_CHARS
                        ) {
                            add(
                                GeminiModel(
                                    id = id,
                                    label = label,
                                )
                            )
                        }
                    }
                }
            }.getOrDefault(emptyList())

        return GeminiAgentCatalogPolicy.newestSupportedAgents(cached)
            .ifEmpty { listOf(FALLBACK_GEMINI_MODEL) }
    }

    suspend fun refreshGeminiModels(): List<GeminiModel> =
        withContext(Dispatchers.IO) { guarded("gemini") {
            val key = credential("gemini")
            require(key.isNotBlank()) {
                "Save a Gemini API key before refreshing models."
            }

            val response =
                executeHttp(
                    request =
                        Request.Builder()
                            .url(MODELS_URL)
                            .get()
                            .header("Accept", "application/json")
                            .header("Cache-Control", "no-cache")
                            .header("x-goog-api-key", key)
                            .header("User-Agent", "HARU-Android/0.9.37")
                            .build(),
                    timeoutMs = 20_000,
                    maxChars = MAX_CATALOG_RESPONSE_CHARS,
                )

            val raw = checkedBody(response)

            val models = parseProviderJson(raw).optJSONArray("models") ?: JSONArray()
                val listed = buildList {
                    for (i in 0 until models.length()) {
                        val item = models.optJSONObject(i) ?: continue
                        val id = item.optString("name")
                            .removePrefix("models/")
                            .trim()

                        if (!GeminiAgentCatalogPolicy.isEligibleAgentId(id)) continue

                        val methods =
                            item.optJSONArray("supportedGenerationMethods") ?: JSONArray()
                        var supportsGenerate = false
                        for (j in 0 until methods.length()) {
                            if (methods.optString(j) == "generateContent") {
                                supportsGenerate = true
                                break
                            }
                        }
                        if (!supportsGenerate) continue

                        val label = item.optString("displayName")
                            .trim()
                            .take(MAX_MODEL_LABEL_CHARS)
                            .ifBlank { humanize(id) }

                        add(GeminiModel(id, label))
                    }
                }
                    .distinctBy { it.id }

                val candidates = GeminiAgentCatalogPolicy.newestSupportedAgents(listed)
                require(candidates.isNotEmpty()) {
                    "Google returned no supported current Gemini agents."
                }

                val refreshed = candidates

                val array = JSONArray()
                refreshed.forEach { model ->
                    array.put(
                        JSONObject()
                            .put("id", model.id)
                            .put("label", model.label)
                    )
                }
                preferences.edit()
                    .putString(KEY_GEMINI_CATALOG, array.toString())
                    .apply()

                val selectedId = preferences
                    .getString(KEY_GEMINI_MODEL, "")
                    .orEmpty()
                if (refreshed.none { it.id == selectedId }) {
                    preferences.edit()
                        .putString(KEY_GEMINI_MODEL, preferred(refreshed).id)
                        .apply()
                }

            refreshed
        } }

    fun saveProvider(provider: OnlineProvider) {
        preferences.edit().putString(KEY_PROVIDER, provider.name).apply()
    }

    fun saveGeminiModel(model: GeminiModel) {
        require(geminiModels().any { it.id == model.id }) {
            "Refresh Gemini models before selecting this model."
        }
        preferences.edit().putString(KEY_GEMINI_MODEL, model.id).apply()
    }

    fun saveGeminiKey(value: String) {
        credentials.put("gemini", value)
    }

    fun hasGeminiKey(): Boolean =
        credential("gemini").isNotBlank()

    fun saveGroqKey(value: String) {
        credentials.put("groq", value)
    }

    fun hasGroqKey(): Boolean =
        credential("groq").isNotBlank()

    suspend fun ask(
        provider: OnlineProvider,
        prompt: String,
        systemPrompt: String,
        history: List<ConversationExchange> = emptyList(),
        summary: String = "",
        antigravitySession: AntigravitySession? = null,
    ): OnlineAiReply = withContext(Dispatchers.IO) { guarded(if (provider == OnlineProvider.GROQ) "groq" else "gemini") {
        require(prompt.isNotBlank()) { "Prompt is empty." }
        require(prompt.length <= MAX_PROMPT_CHARS) {
            "Prompt is too large."
        }
        require(systemPrompt.length <= MAX_SYSTEM_PROMPT_CHARS) {
            "System prompt is too large."
        }

        val recentHistory = AiRequestPolicy.boundedHistory(history)
        val cleanSummary = ConversationMemoryPolicy.sanitizeSummary(summary).takeLast(AiRequestPolicy.SUMMARY_CHARS)

        when (provider) {
            OnlineProvider.ANTIGRAVITY ->
                when (HaruAiRoutingPolicy.routeForAntigravity(prompt)) {
                    HaruAiRoute.FAST_CHAT ->
                        OnlineAiReply(
                            text =
                                askGeminiWithTransientFallback(
                                    modelId = settings().geminiModel.id,
                                    prompt = prompt,
                                    systemPrompt = systemPrompt,
                                    history = recentHistory,
                                    summary = cleanSummary,
                                    enableSearch = HaruAiRoutingPolicy.needsWebSearch(prompt),
                                ),
                            antigravitySession =
                                null,
                        )

                    HaruAiRoute.ANTIGRAVITY_AGENT ->
                        askAntigravity(
                            prompt = prompt,
                            systemPrompt = systemPrompt,
                            history = recentHistory,
                            summary = cleanSummary,
                            session = antigravitySession,
                            enableWebTools =
                                HaruAiRoutingPolicy.needsWebSearch(prompt),
                        )
                }

            OnlineProvider.GEMINI ->
                OnlineAiReply(
                    text =
                        askGeminiWithTransientFallback(
                            modelId = settings().geminiModel.id,
                            prompt = prompt,
                            systemPrompt = systemPrompt,
                            history = recentHistory,
                            summary = cleanSummary,
                            enableSearch =
                                HaruAiRoutingPolicy.needsWebSearch(prompt),
                        )
                )
            OnlineProvider.GROQ ->
                OnlineAiReply(
                    text =
                        askGroq(
                            prompt = prompt,
                            systemPrompt = systemPrompt,
                            history = recentHistory,
                            summary = cleanSummary,
                        )
                )
        }
    } }

    suspend fun test(provider: OnlineProvider): String =
        when (provider) {
            OnlineProvider.ANTIGRAVITY -> testFastGemini()
            OnlineProvider.GEMINI -> testFastGemini()
            OnlineProvider.GROQ ->
                ask(
                    provider = provider,
                    prompt = TEST_PROMPT,
                    systemPrompt = TEST_SYSTEM_PROMPT,
                ).text
        }

    suspend fun testFastGemini(): String =
        withContext(Dispatchers.IO) { guarded("gemini") {
            askGeminiWithTransientFallback(
                modelId = settings().geminiModel.id,
                prompt = TEST_PROMPT,
                systemPrompt = TEST_SYSTEM_PROMPT,
                history = emptyList(),
                summary = "",
                enableSearch = false,
            )
        } }

    suspend fun testAntigravityAgent(): String =
        withContext(Dispatchers.IO) { guarded("gemini") {
            askAntigravity(
                prompt = TEST_PROMPT,
                systemPrompt = TEST_SYSTEM_PROMPT,
                history = emptyList(),
                summary = "",
                session = null,
                enableWebTools = false,
            ).text
        } }

    private suspend fun askAntigravity(
        prompt: String,
        systemPrompt: String,
        history: List<ConversationExchange>,
        summary: String,
        @Suppress("UNUSED_PARAMETER") session: AntigravitySession?,
        enableWebTools: Boolean,
    ): OnlineAiReply {
        val key = credential("gemini")
        require(key.isNotBlank()) { "Gemini API key is required." }
        require(prompt.length <= 6_000 && prompt.length + systemPrompt.length <= 6_500) {
            "Agent task is too large. Shorten it to 6,000 characters or select Gemini for a direct answer."
        }
        // Bounded local memory replaces unbounded previous_interaction_id context.
        val input = buildString {
            append(systemPrompt)
            append("\nKeep the task focused and produce a concise final answer within the token budget.\n")
            if (summary.isNotBlank()) append("Earlier conversation memory: " + summary.takeLast(500) + "\n")
            history.takeLast(1).forEach {
                append("User: " + it.user.take(500) + "\nHARU: " + it.assistant.take(500) + "\n")
            }
            append("User: $prompt")
        }
        val payload = JSONObject()
            .put("agent", ANTIGRAVITY_AGENT)
            .put("input", input)
            .put("environment", "remote")
            .put("background", true)
            .put("agent_config", JSONObject().put("type", "antigravity")
                .put("model", settings().geminiModel.id)
                .put("max_total_tokens", if (prompt == TEST_PROMPT) 512 else ANTIGRAVITY_TOKEN_BUDGET))
            // Explicit lists prevent code execution and other default agent tools.
            .put("tools", if (enableWebTools) JSONArray()
                .put(JSONObject().put("type", "google_search"))
                .put(JSONObject().put("type", "url_context")) else JSONArray())
        val headers = mapOf("x-goog-api-key" to key)
        val response = AntigravityRunner(
            create = { postJson(INTERACTIONS_URL, payload, headers, timeoutMs = 20_000) },
            read = { id ->
                val result = executeHttp(Request.Builder()
                    .url("$INTERACTIONS_URL/$id?include_input=false")
                    .header("x-goog-api-key", key).header("Accept", "application/json")
                    .get().build(), 20_000, MAX_AI_RESPONSE_CHARS)
                parseProviderJson(checkedBody(result))
            },
            cancel = { id -> postJson("$INTERACTIONS_URL/$id/cancel", JSONObject(), headers, timeoutMs = 5_000); Unit },
        ).run()
        stats.recordUsage(response)
        if (response.optString("status") == "failed") {
            val errorCode = response.optJSONObject("error")?.let { it.optString("code", it.optString("status")) }.orEmpty().uppercase()
            if (errorCode in setOf("8", "429", "RESOURCE_EXHAUSTED", "RATE_LIMIT_EXCEEDED", "QUOTA_EXCEEDED")) {
                checkedBody(AiHttpResponse(429, response.toString()))
            }
        }
        return parseAntigravityReply(response)
    }

    private fun parseAntigravityReply(
        response: JSONObject,
    ): OnlineAiReply {
        when (response.optString("status")) {
            "completed" -> Unit
            "incomplete" -> error("Agent stopped at HARU's token budget. Narrow the task; no automatic continuation was sent.")
            "requires_action" -> error("Agent needs an unsupported tool action. No automatic continuation was sent.")
            "cancelled" -> error("Agent request was cancelled.")
            "failed" -> error("Agent execution failed. Check project quota and agent access in Google AI Studio.")
            else -> error("Agent returned an unexpected execution state.")
        }
        val direct = response.optString("output_text").trim()
        val text =
            direct.ifBlank {
                val parts = mutableListOf<String>()
                val steps =
                    response.optJSONArray("steps") ?: JSONArray()

                for (i in steps.length() - 1 downTo 0) {
                    val step = steps.optJSONObject(i) ?: continue
                    if (step.optString("type") != "model_output") continue

                    val content =
                        step.optJSONArray("content") ?: continue
                    for (j in 0 until content.length()) {
                        val item = content.optJSONObject(j) ?: continue
                        if (item.optString("type") == "text") {
                            item.optString("text")
                                .trim()
                                .takeIf { it.isNotBlank() }
                                ?.let(parts::add)
                        }
                    }
                    if (parts.isNotEmpty()) break
                }

                parts.joinToString("\n")
            }

        if (text.isBlank()) {
            error("Antigravity returned no final text.")
        }

        return OnlineAiReply(text = text)
    }

    private suspend fun askGroq(
        prompt: String,
        systemPrompt: String,
        history: List<ConversationExchange>,
        summary: String,
    ): String {
        val key = credential("groq")
        require(key.isNotBlank()) { "Groq API key is required." }

        val messages = JSONArray()

        if (systemPrompt.isNotBlank()) {
            messages.put(
                JSONObject()
                    .put("role", "system")
                    .put("content", systemPrompt)
            )
        }

        if (summary.isNotBlank()) {
            messages.put(
                JSONObject()
                    .put("role", "system")
                    .put(
                        "content",
                        "Earlier conversation memory:\n$summary",
                    )
            )
        }

        history.forEach { exchange ->
            messages.put(
                JSONObject()
                    .put("role", "user")
                    .put("content", exchange.user)
            )
            messages.put(
                JSONObject()
                    .put("role", "assistant")
                    .put("content", exchange.assistant)
            )
        }

        messages.put(
            JSONObject()
                .put("role", "user")
                .put("content", prompt)
        )

        val response = postJson(
            GROQ_CHAT_URL,
            JSONObject()
                .put("model", GROQ_DEFAULT_MODEL)
                .put("messages", messages)
                .put("max_completion_tokens", if (prompt == TEST_PROMPT) 256 else FAST_CHAT_MAX_OUTPUT_TOKENS),
            mapOf("Authorization" to "Bearer $key"),
            timeoutMs = 45_000,
        )

        stats.tokens += (response.optJSONObject("usage")?.optLong("total_tokens", 0L) ?: 0L).coerceAtLeast(0L)
        val text =
            response
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.trim()
                .orEmpty()

        if (text.isBlank()) error("Groq returned no text.")
        return text
    }

    private suspend fun askGeminiWithTransientFallback(
        modelId: String,
        prompt: String,
        systemPrompt: String,
        history: List<ConversationExchange>,
        summary: String,
        enableSearch: Boolean,
    ): String {
        try {
            return askGemini(
                modelId = modelId,
                prompt = prompt,
                systemPrompt = systemPrompt,
                history = history,
                summary = summary,
                enableSearch = enableSearch,
            )
        } catch (exc: ProviderHttpException) {
            val fallbackId = FALLBACK_GEMINI_MODEL.id
            if (
                !GeminiResiliencePolicy.shouldFallback(
                    statusCode = exc.statusCode,
                    primaryModelId = modelId,
                    fallbackModelId = fallbackId,
                )
            ) {
                throw exc
            }

            return askGemini(
                modelId = fallbackId,
                prompt = prompt,
                systemPrompt = systemPrompt,
                history = history,
                summary = summary,
                enableSearch = enableSearch,
            )
        }
    }

    private suspend fun askGemini(
        modelId: String,
        prompt: String,
        systemPrompt: String,
        history: List<ConversationExchange>,
        summary: String,
        enableSearch: Boolean,
    ): String {
        require(SAFE_MODEL_ID.matches(modelId)) {
            "Invalid Gemini model identifier."
        }

        val key = credential("gemini")
        require(key.isNotBlank()) { "Gemini API key is required." }

        val contents = JSONArray()

        history.forEach { exchange ->
            contents.put(
                JSONObject()
                    .put("role", "user")
                    .put(
                        "parts",
                        JSONArray().put(
                            JSONObject().put("text", exchange.user)
                        )
                    )
            )
            contents.put(
                JSONObject()
                    .put("role", "model")
                    .put(
                        "parts",
                        JSONArray().put(
                            JSONObject().put("text", exchange.assistant)
                        )
                    )
            )
        }
        contents.put(
            JSONObject()
                .put("role", "user")
                .put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", prompt))
                )
        )

        val payload =
            JSONObject()
                .put("contents", contents)
                .put(
                    "generationConfig",
                    JSONObject()
                        .put(
                            "maxOutputTokens",
                            if (prompt == TEST_PROMPT) 256 else FAST_CHAT_MAX_OUTPUT_TOKENS,
                        )
                )
                .apply {
                    if (enableSearch) {
                        put(
                            "tools",
                            JSONArray().put(
                                JSONObject().put(
                                    "google_search",
                                    JSONObject(),
                                )
                            ),
                        )
                    }
                }

        AiRequestPolicy.thinkingLevel(modelId)?.let { level ->
            payload.getJSONObject("generationConfig").put("thinkingConfig", JSONObject().put("thinkingLevel", level))
        }

        val instruction = listOf(systemPrompt, if (summary.isNotBlank()) "Earlier conversation memory:\n$summary" else "")
            .filter { it.isNotBlank() }.joinToString("\n\n")
        if (instruction.isNotBlank()) {
            payload.put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(
                        JSONObject().put("text", instruction)
                    )
                )
            )
        }

        val response = postJson(
            "https://generativelanguage.googleapis.com/v1beta/models/" +
                modelId + ":generateContent",
            payload,
            mapOf("x-goog-api-key" to key),
            timeoutMs = 45_000,
        )

        stats.recordUsage(response)
        val candidate = response
            .optJSONArray("candidates")
            ?.optJSONObject(0)
            ?: error("Gemini returned no candidate.")

        val parts = candidate
            .optJSONObject("content")
            ?.optJSONArray("parts")
            ?: JSONArray()

        val text = buildString {
            for (i in 0 until parts.length()) {
                if (parts.optJSONObject(i)?.optBoolean("thought", false) == true) continue
                val value = parts
                    .optJSONObject(i)
                    ?.optString("text")
                    ?.trim()
                    .orEmpty()
                if (value.isNotBlank()) {
                    if (isNotEmpty()) append('\n')
                    append(value)
                }
            }
        }.trim()

        if (text.isBlank()) {
            if (candidate.optString("finishReason") == "MAX_TOKENS") {
                error("Gemini reached HARU's response budget before answering. Shorten the question; no automatic retry was sent.")
            }
            error("Gemini returned no final text. Check model access and provider restrictions.")
        }
        return if (candidate.optString("finishReason") == "MAX_TOKENS") {
            "$text\n\nAnswer stopped at HARU's response budget."
        } else text
    }

    private suspend fun postJson(
        url: String,
        payload: JSONObject,
        headers: Map<String, String>,
        timeoutMs: Int,
    ): JSONObject {
        val body =
            payload
                .toString()
                .toRequestBody(JSON_MEDIA_TYPE)

        val builder =
            Request.Builder()
                .url(url)
                .post(body)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "HARU-Android/0.9.37")

        headers.forEach { (name, value) ->
            builder.header(name, value)
        }

        val response = executeHttp(builder.build(), timeoutMs, MAX_AI_RESPONSE_CHARS)
        val raw = checkedBody(response)
        return if (raw.isBlank()) JSONObject() else parseProviderJson(raw)
    }

    private fun parseProviderJson(raw: String): JSONObject =
        try { JSONObject(raw) }
        catch (_: org.json.JSONException) { error("Provider returned an unreadable JSON response.") }

    private fun checkedBody(response: AiHttpResponse): String {
        val code = response.code
        if (code in 200..299) return response.body
        val message = when (code) {
            401, 403 -> "Authentication or access was rejected. Check the saved key, project restrictions and model access."
            429 -> {
                stats.quotaErrors += 1
                val wait = AiRequestPolicy.quotaCooldownMs(response.body, response.retryAfter, System.currentTimeMillis())
                preferences.edit().putLong("quota_until_$activeScope", System.currentTimeMillis() + wait).apply()
                AiRequestPolicy.waitMessage(activeScope, wait)
            }
            400 -> "Provider rejected the request configuration (HTTP 400). Check model and search/tool support."
            404 -> "Model or agent is unavailable (HTTP 404). Refresh the models or select a supported model."
            in 500..599 -> "Provider HTTP $code · AI provider is temporarily unavailable."
            else -> "Provider request failed (HTTP $code)."
        }
        throw ProviderHttpException(code, message)
    }

    private suspend fun executeHttp(
        request: Request,
        timeoutMs: Int,
        maxChars: Int,
    ): AiHttpResponse {
        if (request.method == "POST") {
            if (request.url.encodedPath.endsWith("/cancel")) stats.cancellations += 1
            else stats.generations += 1
        } else if (request.url.encodedPath.contains("/interactions/")) stats.polls += 1
        testTransport?.let { return it(request, timeoutMs, maxChars) }
        val client = httpClient.newBuilder()
            .connectTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .callTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS).build()
        try {
            return suspendCancellableCoroutine { continuation ->
                val call = client.newCall(request)
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(e)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        try {
                            val result = response.use {
                                AiHttpResponse(it.code, it.body?.charStream()?.use { reader ->
                                    reader.readBoundedText(maxChars)
                                }.orEmpty(), it.header("Retry-After"))
                            }
                            continuation.resume(result)
                        } catch (e: Exception) { continuation.resumeWithException(e) }
                    }
                })
            }
        } catch (_: UnknownHostException) {
            throw IllegalStateException(networkFailureMessage())
        } catch (_: java.net.SocketTimeoutException) {
            throw IllegalStateException("AI request timed out. No automatic network retry was sent.")
        } catch (_: IOException) {
            throw IllegalStateException("AI connection failed. Check your internet connection before trying again.")
        }
    }

    private fun networkFailureMessage(): String {
        val manager =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager
        val active = manager.activeNetwork
        val capabilities =
            active?.let(manager::getNetworkCapabilities)

        val validated =
            capabilities?.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED
            ) == true

        return if (!validated) {
            "No validated internet connection. HARU could not reach the AI provider."
        } else {
            "DNS lookup failed. HARU tried Android DNS twice and secure DNS fallback, but the provider hostname still could not be resolved."
        }
    }

    private fun preferred(models: List<GeminiModel>): GeminiModel =
        models.firstOrNull {
            it.id.contains("flash-lite", ignoreCase = true)
        }
            ?: models.firstOrNull {
                it.id.contains("flash", ignoreCase = true)
            }
            ?: models.first()

    private fun versionScore(id: String): Long =
        Regex("\\d+")
            .findAll(id)
            .mapNotNull { it.value.toLongOrNull() }
            .take(3)
            .fold(0L) { acc, value ->
                acc * 1000L + value.coerceAtMost(999L)
            }

    private fun humanize(id: String): String =
        "Gemini " +
            id.removePrefix("gemini-")
                .split('-')
                .joinToString(" ") { part ->
                    if (part.toDoubleOrNull() != null) {
                        part
                    } else {
                        part.replaceFirstChar { it.uppercase() }
                    }
                }

    companion object {
        private const val MAX_PROMPT_CHARS = 16_000
        private const val MAX_SYSTEM_PROMPT_CHARS = 4_000
        private const val MAX_MODEL_LABEL_CHARS = 100
        private const val MAX_CATALOG_RESPONSE_CHARS = 1_000_000
        private const val MAX_AI_RESPONSE_CHARS = 1_000_000
        private const val ANTIGRAVITY_TOKEN_BUDGET = 4_096
        private const val FAST_CHAT_MAX_OUTPUT_TOKENS = 1_200
        private val JSON_MEDIA_TYPE =
            "application/json; charset=utf-8".toMediaType()
        private const val TEST_PROMPT = "Reply with exactly: HARU OK"
        private const val TEST_SYSTEM_PROMPT =
            "You are HARU's connection test. Reply very briefly."
        private const val ANTIGRAVITY_AGENT =
            "antigravity-preview-09-2026"
        private const val INTERACTIONS_URL =
            "https://generativelanguage.googleapis.com/v1beta/interactions"

        private val SAFE_MODEL_ID =
            Regex("^gemini-[A-Za-z0-9._-]{1,80}$")

        private const val KEY_PROVIDER = "provider"
        private const val KEY_GEMINI_MODEL = "gemini_model"
        private const val KEY_GEMINI_CATALOG = "gemini_catalog"
        private const val MODELS_URL =
            "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000"
        private const val GROQ_CHAT_URL =
            "https://api.groq.com/openai/v1/chat/completions"

        const val GROQ_DEFAULT_MODEL =
            "qwen/qwen3.8-27b"

        val FALLBACK_GEMINI_MODEL =
            GeminiModel(
                "gemini-3.5-flash-lite",
                "Gemini 3.5 Flash-Lite",
            )
    }
}
