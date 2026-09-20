package io.haru.assistant.onlineai

import android.content.Context
import io.haru.assistant.memory.AntigravitySession
import io.haru.assistant.memory.ConversationExchange
import io.haru.assistant.memory.ConversationMemoryPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class OnlineProvider {
    ANTIGRAVITY,
    GEMINI,
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
    private val preferences =
        context.getSharedPreferences("haru_online_ai", Context.MODE_PRIVATE)
    private val credentials = SecureCredentialStore(context)

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

        return runCatching {
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
            .ifEmpty { listOf(FALLBACK_GEMINI_MODEL) }
    }

    suspend fun refreshGeminiModels(): List<GeminiModel> =
        withContext(Dispatchers.IO) {
            val key = credentials.get("gemini")
            require(key.isNotBlank()) {
                "Save a Gemini API key before refreshing models."
            }

            val connection = URL(MODELS_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Cache-Control", "no-cache")
            connection.setRequestProperty("x-goog-api-key", key)
            connection.setRequestProperty("User-Agent", "HARU-Android/0.3")

            try {
                val code = connection.responseCode
                val raw = (if (code in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                })?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText().take(MAX_CATALOG_RESPONSE_CHARS) }
                    .orEmpty()

                if (code !in 200..299) {
                    val detail = runCatching {
                        JSONObject(raw)
                            .optJSONObject("error")
                            ?.optString("message")
                    }.getOrNull().orEmpty()
                    error(
                        detail.ifBlank {
                            "Gemini model refresh failed (HTTP $code)."
                        }
                    )
                }

                val models = JSONObject(raw).optJSONArray("models") ?: JSONArray()
                val refreshed = buildList {
                    for (i in 0 until models.length()) {
                        val item = models.optJSONObject(i) ?: continue
                        val id = item.optString("name")
                            .removePrefix("models/")
                            .trim()

                        if (!SAFE_MODEL_ID.matches(id)) continue
                        if (
                            id.contains("embedding", true) ||
                            id.contains("image", true) ||
                            id.contains("veo", true) ||
                            id.contains("deprecated", true) ||
                            id.contains("legacy", true)
                        ) {
                            continue
                        }

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
                    .sortedWith(
                        compareByDescending<GeminiModel> { versionScore(it.id) }
                            .thenBy { it.label }
                    )

                require(refreshed.isNotEmpty()) {
                    "Google returned no current Gemini text models."
                }

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
            } finally {
                connection.disconnect()
            }
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
                askAntigravity(
                    prompt = prompt,
                    systemPrompt = systemPrompt,
                    history = recentHistory,
                    summary = cleanSummary,
                    session = antigravitySession,
                )
            OnlineProvider.GEMINI ->
                OnlineAiReply(
                    text =
                        askGemini(
                            modelId = settings().geminiModel.id,
                            prompt = prompt,
                            systemPrompt = systemPrompt,
                            history = recentHistory,
                            summary = cleanSummary,
                        )
                )
        }
    }

    suspend fun test(provider: OnlineProvider): String =
        ask(
            provider = provider,
            prompt = "Reply with exactly: HARU OK",
            systemPrompt = "You are HARU's connection test. Reply very briefly.",
        ).text

    private fun askAntigravity(
        prompt: String,
        systemPrompt: String,
        history: List<ConversationExchange>,
        summary: String,
        session: AntigravitySession?,
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
                    .put(
                        "tools",
                        antigravityTools(),
                    )

            try {
                return parseAntigravityReply(
                    postJson(
                        INTERACTIONS_URL,
                        continuationPayload,
                        mapOf("x-goog-api-key" to key),
                        timeoutMs = 180_000,
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
                .put(
                    "tools",
                    antigravityTools(),
                )

        return parseAntigravityReply(
            postJson(
                INTERACTIONS_URL,
                payload,
                mapOf("x-goog-api-key" to key),
                timeoutMs = 180_000,
            )
        )
    }

    private fun antigravityTools(): JSONArray =
        JSONArray()
            .put(JSONObject().put("type", "google_search"))
            .put(JSONObject().put("type", "url_context"))

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

    private fun askGemini(
        modelId: String,
        prompt: String,
        systemPrompt: String,
        history: List<ConversationExchange>,
        summary: String,
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

        val payload = JSONObject()
            .put("contents", contents)
            .put(
                "tools",
                JSONArray().put(JSONObject().put("google_search", JSONObject()))
            )

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
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "HARU-Android/0.3")
        headers.forEach { (name, value) ->
            connection.setRequestProperty(name, value)
        }

        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use {
                it.write(payload.toString())
            }

            val code = connection.responseCode
            val raw = (if (code in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            })?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText().take(MAX_AI_RESPONSE_CHARS) }
                .orEmpty()

            if (code !in 200..299) {
                val detail = runCatching {
                    JSONObject(raw)
                        .optJSONObject("error")
                        ?.optString("message")
                }.getOrNull().orEmpty()

                throw ProviderHttpException(
                    statusCode = code,
                    message =
                        when (code) {
                            401, 403 -> "Authentication was rejected."
                            429 -> "Provider quota or rate limit reached."
                            in 500..599 ->
                                "AI provider is temporarily unavailable."
                            else ->
                                detail.ifBlank {
                                    "Provider request failed (HTTP $code)."
                                }
                        },
                )
            }

            return if (raw.isBlank()) JSONObject() else JSONObject(raw)
        } finally {
            connection.disconnect()
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
        private const val MAX_INTERACTION_ID_CHARS = 512
        private const val ANTIGRAVITY_TOKEN_BUDGET = 12_000
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

        val FALLBACK_GEMINI_MODEL =
            GeminiModel(
                "gemini-3.5-flash-lite",
                "Gemini 3.5 Flash-Lite",
            )
    }
}
