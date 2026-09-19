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
    GEMINI_FLASH_LITE,
    OPENROUTER_FREE,
    LOCAL_ONLY,
}

data class OnlineAiSettings(
    val provider: OnlineProvider = OnlineProvider.ANTIGRAVITY,
    val model: String = "antigravity-preview-09-2026",
)

class AndroidOnlineAiManager(
    context: Context,
) {
    private val preferences =
        context.getSharedPreferences("haru_online_ai", Context.MODE_PRIVATE)
    private val credentials = SecureCredentialStore(context)

    fun settings(): OnlineAiSettings {
        val provider = runCatching {
            OnlineProvider.valueOf(
                preferences.getString("provider", OnlineProvider.ANTIGRAVITY.name)
                    ?: OnlineProvider.ANTIGRAVITY.name
            )
        }.getOrDefault(OnlineProvider.ANTIGRAVITY)

        return OnlineAiSettings(
            provider = provider,
            model = modelFor(provider),
        )
    }

    fun saveProvider(provider: OnlineProvider) {
        preferences.edit().putString("provider", provider.name).apply()
    }

    fun saveGeminiKey(value: String) {
        credentials.put("gemini", value)
    }

    fun saveOpenRouterKey(value: String) {
        credentials.put("openrouter", value)
    }

    fun hasGeminiKey(): Boolean = credentials.get("gemini").isNotBlank()

    fun hasOpenRouterKey(): Boolean = credentials.get("openrouter").isNotBlank()

    fun clearGeminiKey() = credentials.delete("gemini")

    fun clearOpenRouterKey() = credentials.delete("openrouter")

    suspend fun ask(
        provider: OnlineProvider,
        prompt: String,
        systemPrompt: String,
    ): String = withContext(Dispatchers.IO) {
        when (provider) {
            OnlineProvider.ANTIGRAVITY -> askAntigravity(prompt, systemPrompt)
            OnlineProvider.GEMINI_FLASH_LITE -> askGemini(prompt, systemPrompt)
            OnlineProvider.OPENROUTER_FREE -> askOpenRouter(prompt, systemPrompt)
            OnlineProvider.LOCAL_ONLY -> error("Local-only mode selected.")
        }
    }

    suspend fun test(provider: OnlineProvider): String =
        ask(
            provider,
            "Reply with exactly: HARU OK",
            "You are HARU's connection test. Reply very briefly.",
        )

    private fun askAntigravity(prompt: String, systemPrompt: String): String {
        val key = credentials.get("gemini")
        require(key.isNotBlank()) { "Gemini API key is required." }

        val input = if (systemPrompt.isBlank()) prompt else "$systemPrompt\n\nUser: $prompt"
        val payload = JSONObject()
            .put("agent", "antigravity-preview-09-2026")
            .put("input", input)
            .put("environment", "remote")
            .put(
                "agent_config",
                JSONObject()
                    .put("type", "antigravity")
                    .put("model", "gemini-3.5-flash-lite")
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
                    val text = item.optString("text").trim()
                    if (text.isNotBlank()) parts += text
                }
            }
        }

        return parts.joinToString("\n").ifBlank {
            error("Antigravity returned no final text.")
        }
    }

    private fun askGemini(prompt: String, systemPrompt: String): String {
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
                    JSONArray().put(JSONObject().put("text", systemPrompt))
                )
            )
        }

        val response = postJson(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent",
            payload,
            mapOf("x-goog-api-key" to key),
        )

        val candidate = response.optJSONArray("candidates")?.optJSONObject(0)
            ?: error("Gemini returned no candidate.")
        val parts = candidate.optJSONObject("content")
            ?.optJSONArray("parts")
            ?: JSONArray()

        val text = buildString {
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                val value = part.optString("text").trim()
                if (value.isNotBlank()) {
                    if (isNotEmpty()) append('\n')
                    append(value)
                }
            }
        }.trim()

        if (text.isBlank()) error("Gemini returned no text.")
        return text
    }

    private fun askOpenRouter(prompt: String, systemPrompt: String): String {
        val key = credentials.get("openrouter")
        require(key.isNotBlank()) { "OpenRouter API key is required." }

        val messages = JSONArray()
        if (systemPrompt.isNotBlank()) {
            messages.put(
                JSONObject()
                    .put("role", "system")
                    .put("content", systemPrompt)
            )
        }
        messages.put(
            JSONObject()
                .put("role", "user")
                .put("content", prompt)
        )

        val payload = JSONObject()
            .put("model", "openrouter/free")
            .put("messages", messages)
            .put("temperature", 0.3)

        val response = postJson(
            "https://openrouter.ai/api/v1/chat/completions",
            payload,
            mapOf(
                "Authorization" to "Bearer $key",
                "HTTP-Referer" to "https://github.com/Denberg28/HARU-Human-Assistance-Responsive-Utility",
                "X-Title" to "HARU",
            ),
        )

        val text = response.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?.trim()
            .orEmpty()

        if (text.isBlank()) error("OpenRouter returned no text.")
        return text
    }

    private fun postJson(
        url: String,
        payload: JSONObject,
        headers: Map<String, String>,
        timeoutMs: Int = 45_000,
    ): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "HARU-Android/0.1")
        headers.forEach { (name, value) ->
            connection.setRequestProperty(name, value)
        }

        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use {
                it.write(payload.toString())
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                reader.readText().take(4_000_000)
            }.orEmpty()

            if (code !in 200..299) {
                val detail = runCatching {
                    JSONObject(raw).optJSONObject("error")?.optString("message")
                }.getOrNull().orEmpty()
                error(
                    when (code) {
                        401, 403 -> "Authentication was rejected."
                        429 -> "Provider quota or rate limit reached."
                        in 500..599 -> "AI provider is temporarily unavailable."
                        else -> detail.ifBlank { "Provider request failed (HTTP $code)." }
                    }
                )
            }

            return if (raw.isBlank()) JSONObject() else JSONObject(raw)
        } finally {
            connection.disconnect()
        }
    }

    private fun modelFor(provider: OnlineProvider): String =
        when (provider) {
            OnlineProvider.ANTIGRAVITY -> "antigravity-preview-09-2026"
            OnlineProvider.GEMINI_FLASH_LITE -> "gemini-3.5-flash-lite"
            OnlineProvider.OPENROUTER_FREE -> "openrouter/free"
            OnlineProvider.LOCAL_ONLY -> "on-device"
        }
}
