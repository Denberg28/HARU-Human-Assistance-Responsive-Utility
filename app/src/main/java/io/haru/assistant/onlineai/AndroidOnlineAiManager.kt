package io.haru.assistant.onlineai

import io.haru.assistant.util.readBoundedText
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.haru.assistant.memory.AntigravitySession
import io.haru.assistant.memory.ConversationExchange
import io.haru.assistant.memory.ConversationMemoryPolicy
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

class AndroidOnlineAiManager(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val preferences =
        appContext.getSharedPreferences("haru_online_ai", Context.MODE_PRIVATE)
    private val credentials = SecureCredentialStore(appContext)
    private val httpClient =
        OkHttpClient.Builder()
            .dns(HaruResilientDns.create())
            .retryOnConnectionFailure(true)
            .build()

    init {
        credentials.delete("openrouter")
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
        withContext(Dispatchers.IO) {
            val key = credentials.get("gemini")
            require(key.isNotBlank()) {
                "Save a Gemini API key before refreshing models."
            }

            val (code, raw) =
                executeHttp(
                    request =
                        Request.Builder()
                            .url(MODELS_URL)
                            .get()
                            .header("Accept", "application/json")
                            .header("Cache-Control", "no-cache")
                            .header("x-goog-api-key", key)
                            .header("User-Agent", "HARU-Android/0.9.30")
                            .build(),
                    timeoutMs = 20_000,
                    maxChars = MAX_CATALOG_RESPONSE_CHARS,
                )

            if (code !in 200..299) {
                val detail = providerErrorDetail(raw)
                error(
                    detail.ifBlank {
                        "Gemini model refresh failed (HTTP $code)."
                    }
                )
            }

            val models = JSONObject(raw).optJSONArray("models") ?: JSONArray()
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
        }

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
        credentials.get("gemini").isNotBlank()

    fun saveGroqKey(value: String) {
        credentials.put("groq", value)
    }

    fun hasGroqKey(): Boolean =
        credentials.get("groq").isNotBlank()

    suspend fun ask(
        provider: OnlineProvider,
        prompt: String,
        systemPrompt: String,
        history: List<ConversationExchange> = emptyList(),
        summary: String = "",
        antigravitySession: AntigravitySession? = null,
    ): OnlineAiReply = withContext(Dispatchers.IO) {
        require(prompt.isNotBlank()) { "Prompt is empty." }
        require(prompt.length <= MAX_PROMPT_CHARS) {
            "Prompt is too large."
        }
        require(systemPrompt.length <= MAX_SYSTEM_PROMPT_CHARS) {
            "System prompt is too large."
        }

        val recentHistory =
            history
                .takeLast(ConversationMemoryPolicy.PROVIDER_RECENT_EXCHANGES)
                .mapNotNull { exchange ->
                    val user =
                        ConversationMemoryPolicy.sanitizeUser(exchange.user)
                    val assistant =
                        ConversationMemoryPolicy.sanitizeAssistant(exchange.assistant)
                    if (user.isBlank() || assistant.isBlank()) {
                        null
                    } else {
                        ConversationExchange(user, assistant)
                    }
                }

        val cleanSummary =
            ConversationMemoryPolicy.sanitizeSummary(summary)

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
                                antigravitySession?.takeIf { it.isFresh() },
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
    }

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
        withContext(Dispatchers.IO) {
            askGeminiWithTransientFallback(
                modelId = settings().geminiModel.id,
                prompt = TEST_PROMPT,
                systemPrompt = TEST_SYSTEM_PROMPT,
                history = emptyList(),
                summary = "",
                enableSearch = false,
            )
        }

    suspend fun testAntigravityAgent(): String =
        withContext(Dispatchers.IO) {
            askAntigravity(
                prompt = TEST_PROMPT,
                systemPrompt = TEST_SYSTEM_PROMPT,
                history = emptyList(),
                summary = "",
                session = null,
                enableWebTools = false,
            ).text
        }

    private fun askAntigravity(
        prompt: String,
        systemPrompt: String,
        history: List<ConversationExchange>,
        summary: String,
        session: AntigravitySession?,
        enableWebTools: Boolean,
    ): OnlineAiReply {
        val key = credentials.get("gemini")
        require(key.isNotBlank()) { "Gemini API key is required." }

        val freshSession =
            session?.takeIf { it.isFresh() }

        if (freshSession != null) {
            val continuationPayload =
                JSONObject()
                    .put("agent", ANTIGRAVITY_AGENT)
                    .put("input", prompt)
                    .put(
                        "previous_interaction_id",
                        freshSession.interactionId,
                    )
                    .put(
                        "environment",
                        freshSession.environmentId,
                    )
                    .put(
                        "agent_config",
                        JSONObject()
                            .put("type", "antigravity")
                            .put("model", settings().geminiModel.id)
                            .put("max_total_tokens", ANTIGRAVITY_TOKEN_BUDGET)
                    )
                    .apply {
                        if (enableWebTools) {
                            put(
                                "tools",
                                antigravityTools(),
                            )
                        }
                    }

            try {
                return parseAntigravityReply(
                    postJson(
                        INTERACTIONS_URL,
                        continuationPayload,
                        mapOf("x-goog-api-key" to key),
                        timeoutMs = ANTIGRAVITY_TIMEOUT_MS,
                    )
                )
            } catch (exc: ProviderHttpException) {
                if (
                    exc.statusCode !in setOf(
                        400,
                        404,
                        409,
                        412,
                    )
                ) {
                    throw exc
                }
            }
        }

        val input = buildString {
            if (systemPrompt.isNotBlank()) {
                append(systemPrompt)
                append("\n\n")
            }
            if (summary.isNotBlank()) {
                append("Compact memory from earlier conversation:\n")
                append(summary)
                append("\n\n")
            }
            if (history.isNotEmpty()) {
                append("Recent conversation context (oldest to newest):\n")
                history.forEach { exchange ->
                    append("User: ")
                    append(exchange.user)
                    append("\nHARU: ")
                    append(exchange.assistant)
                    append("\n")
                }
                append("\n")
            }
            append("User: ")
            append(prompt)
        }

        val payload =
            JSONObject()
                .put("agent", ANTIGRAVITY_AGENT)
                .put("input", input)
                .put("environment", "remote")
                .put(
                    "agent_config",
                    JSONObject()
                        .put("type", "antigravity")
                        .put("model", settings().geminiModel.id)
                        .put("max_total_tokens", ANTIGRAVITY_TOKEN_BUDGET)
                )
                .apply {
                    if (enableWebTools) {
                        put(
                            "tools",
                            antigravityTools(),
                        )
                    }
                }

        return parseAntigravityReply(
            postJson(
                INTERACTIONS_URL,
                payload,
                mapOf("x-goog-api-key" to key),
                timeoutMs = ANTIGRAVITY_TIMEOUT_MS,
            )
        )
    }

    private fun antigravityTools(): JSONArray =
        JSONArray().put(JSONObject().put("type", "google_search"))

    private fun parseAntigravityReply(
        response: JSONObject,
    ): OnlineAiReply {
        val direct = response.optString("output_text").trim()
        val text =
            direct.ifBlank {
                val parts = mutableListOf<String>()
                val steps =
                    response.optJSONArray("steps") ?: JSONArray()

                for (i in 0 until steps.length()) {
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
                }

                parts.joinToString("\n")
            }

        if (text.isBlank()) {
            error("Antigravity returned no final text.")
        }

        val interactionId =
            response.optString("id")
                .trim()
                .take(MAX_INTERACTION_ID_CHARS)
        val environmentId =
            response.optString("environment_id")
                .trim()
                .take(MAX_INTERACTION_ID_CHARS)

        val session =
            if (
                interactionId.isNotBlank() &&
                environmentId.isNotBlank()
            ) {
                AntigravitySession(
                    interactionId = interactionId,
                    environmentId = environmentId,
                    updatedAtMs = System.currentTimeMillis(),
                )
            } else {
                null
            }

        return OnlineAiReply(
            text = text,
            antigravitySession = session,
        )
    }

    private fun askGroq(
        prompt: String,
        systemPrompt: String,
        history: List<ConversationExchange>,
        summary: String,
    ): String {
        val key = credentials.get("groq")
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
                .put("messages", messages),
            mapOf("Authorization" to "Bearer $key"),
            timeoutMs = 45_000,
        )

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

    private fun askGeminiWithTransientFallback(
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

    private fun askGemini(
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

        val key = credentials.get("gemini")
        require(key.isNotBlank()) { "Gemini API key is required." }

        val contents = JSONArray()

        if (summary.isNotBlank()) {
            contents.put(
                JSONObject()
                    .put("role", "user")
                    .put(
                        "parts",
                        JSONArray().put(
                            JSONObject().put(
                                "text",
                                "Earlier conversation memory:\n$summary",
                            )
                        )
                    )
            )
            contents.put(
                JSONObject()
                    .put("role", "model")
                    .put(
                        "parts",
                        JSONArray().put(
                            JSONObject().put(
                                "text",
                                "Understood. I will use that compact memory as context.",
                            )
                        )
                    )
            )
        }

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
                            FAST_CHAT_MAX_OUTPUT_TOKENS,
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

        if (systemPrompt.isNotBlank()) {
            payload.put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(
                        JSONObject().put("text", systemPrompt)
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

        if (text.isBlank()) error("Gemini returned no text.")
        return text
    }

    private fun postJson(
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
                .header("User-Agent", "HARU-Android/0.9.30")

        headers.forEach { (name, value) ->
            builder.header(name, value)
        }

        val (code, raw) =
            executeHttp(
                request = builder.build(),
                timeoutMs = timeoutMs,
                maxChars = MAX_AI_RESPONSE_CHARS,
            )

        if (code !in 200..299) {
            val detail = providerErrorDetail(raw)

            throw ProviderHttpException(
                statusCode = code,
                message =
                    when (code) {
                        401, 403 -> "Authentication was rejected."
                        429 ->
                            detail
                                .take(MAX_PROVIDER_ERROR_CHARS)
                                .ifBlank {
                                    "Provider quota or rate limit reached."
                                }
                        in 500..599 ->
                            "Provider HTTP " +
                                code +
                                " · " +
                                detail
                                    .take(MAX_PROVIDER_ERROR_CHARS)
                                    .ifBlank {
                                        "AI provider is temporarily unavailable."
                                    }
                        else ->
                            detail.ifBlank {
                                "Provider request failed (HTTP $code)."
                            }
                    },
            )
        }

        return if (raw.isBlank()) JSONObject() else JSONObject(raw)
    }

    private fun executeHttp(
        request: Request,
        timeoutMs: Int,
        maxChars: Int,
    ): Pair<Int, String> {
        val client =
            httpClient
                .newBuilder()
                .connectTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                .callTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                .build()

        try {
            client.newCall(request).execute().use { response ->
                val raw =
                    response.body
                        ?.charStream()
                        ?.use { it.readBoundedText(maxChars) }
                        .orEmpty()

                return response.code to raw
            }
        } catch (exc: UnknownHostException) {
            throw IllegalStateException(networkFailureMessage(), exc)
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

    private fun providerErrorDetail(raw: String): String =
        runCatching {
            JSONObject(raw)
                .optJSONObject("error")
                ?.optString("message")
        }.getOrNull().orEmpty()

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
        private const val MAX_INTERACTION_ID_CHARS = 512
        private const val ANTIGRAVITY_TOKEN_BUDGET = 1_500
        private const val ANTIGRAVITY_TIMEOUT_MS = 75_000
        private const val FAST_CHAT_MAX_OUTPUT_TOKENS = 1_200
        private const val MAX_PROVIDER_ERROR_CHARS = 320
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
