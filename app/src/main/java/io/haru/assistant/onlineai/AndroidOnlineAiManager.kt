package io.haru.assistant.onlineai

import android.content.Context
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
    ): String = withContext(Dispatchers.IO) {
        require(prompt.isNotBlank()) { "Prompt is empty." }
        require(prompt.length <= MAX_PROMPT_CHARS) {
            "Prompt is too large."
        }
        require(systemPrompt.length <= MAX_SYSTEM_PROMPT_CHARS) {
            "System prompt is too large."
        }

        when (provider) {
            OnlineProvider.ANTIGRAVITY ->
                askAntigravity(prompt, systemPrompt)
            OnlineProvider.GEMINI ->
                askGemini(settings().geminiModel.id, prompt, systemPrompt)
        }
    }

    suspend fun test(provider: OnlineProvider): String =
        ask(
            provider,
            "Reply with exactly: HARU OK",
            "You are HARU's connection test. Reply very briefly.",
        )

    private fun askAntigravity(
        prompt: String,
        systemPrompt: String,
    ): String {
        val key = credentials.get("gemini")
        require(key.isNotBlank()) { "Gemini API key is required." }

        val input = if (systemPrompt.isBlank()) {
            prompt
        } else {
            "$systemPrompt\n\nUser: $prompt"
        }

        val payload = JSONObject()
            .put("agent", "antigravity-preview-09-2026")
            .put("input", input)
            .put("environment", "remote")
            .put(
                "agent_config",
                JSONObject()
                    .put("type", "antigravity")
                    .put("model", settings().geminiModel.id)
                    .put("max_total_tokens", 12000)
            )
            .put(
                "tools",
                JSONArray()
                    .put(JSONObject().put("type", "google_search"))
                    .put(JSONObject().put("type", "url_context"))
            )

        val response = postJson(
            "https://generativelanguage.googleapis.com/v1beta/interactions",
            payload,
            mapOf("x-goog-api-key" to key),
            timeoutMs = 180_000,
        )

        val direct = response.optString("output_text").trim()
        if (direct.isNotBlank()) return direct

        val parts = mutableListOf<String>()
        val steps = response.optJSONArray("steps") ?: JSONArray()
        for (i in 0 until steps.length()) {
            val step = steps.optJSONObject(i) ?: continue
            if (step.optString("type") != "model_output") continue
            val content = step.optJSONArray("content") ?: continue
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

        return parts.joinToString("\n").ifBlank {
            error("Antigravity returned no final text.")
        }
    }

    private fun askGemini(
        modelId: String,
        prompt: String,
        systemPrompt: String,
    ): String {
        require(SAFE_MODEL_ID.matches(modelId)) {
            "Invalid Gemini model identifier."
        }

        val key = credentials.get("gemini")
        require(key.isNotBlank()) { "Gemini API key is required." }

        val payload = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt))
                    )
                )
            )
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

                error(
                    when (code) {
                        401, 403 -> "Authentication was rejected."
                        429 -> "Provider quota or rate limit reached."
                        in 500..599 ->
                            "AI provider is temporarily unavailable."
                        else ->
                            detail.ifBlank {
                                "Provider request failed (HTTP $code)."
                            }
                    }
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
