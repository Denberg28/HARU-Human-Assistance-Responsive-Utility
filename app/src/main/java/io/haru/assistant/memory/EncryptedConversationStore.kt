package io.haru.assistant.memory

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class EncryptedConversationStore(
    context: Context,
) {
    private val preferences =
        context.getSharedPreferences(
            "haru_conversation_memory",
            Context.MODE_PRIVATE,
        )

    private val keyStore =
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }

    @Synchronized
    fun load(): List<ConversationExchange> =
        loadState().exchanges

    @Synchronized
    fun loadState(): ConversationMemoryState {
        val packed =
            preferences.getString(DATA_KEY, "").orEmpty()

        if (packed.isBlank()) return ConversationMemoryState()
        if (packed.length > MAX_PACKED_CHARS) {
            clearData()
            return ConversationMemoryState()
        }

        return runCatching {
            val raw = decrypt(packed)
            parseState(raw)
        }.getOrElse {
            clearData()
            ConversationMemoryState()
        }
    }

    @Synchronized
    fun appendCompleted(
        user: String,
        assistant: String,
        antigravitySession: AntigravitySession? = null,
    ): ConversationMemoryState {
        val current = loadState()
        val memory =
            ConversationMemory(
                initial = current.exchanges,
                initialSummary = current.summary,
            )
        memory.append(user, assistant)

        val updated =
            memory.state(
                antigravitySession =
                    antigravitySession ?: current.antigravitySession,
            )
        saveState(updated)
        return updated
    }

    @Synchronized
    fun saveAntigravitySession(
        session: AntigravitySession?,
    ): ConversationMemoryState {
        val current = loadState()
        val updated =
            current.copy(
                antigravitySession = session,
            )
        saveState(updated)
        return updated
    }

    @Synchronized
    fun clear() {
        clearData()
    }

    private fun parseState(
        raw: String,
    ): ConversationMemoryState {
        val trimmed = raw.trim()

        // Backward compatibility with HARU v0.4.0, which stored only
        // an encrypted JSON array of exchanges.
        if (trimmed.startsWith("[")) {
            return ConversationMemoryState(
                exchanges = parseExchanges(JSONArray(trimmed)),
            )
        }

        val root = JSONObject(trimmed)
        val memory =
            ConversationMemory(
                initial =
                    parseExchanges(
                        root.optJSONArray("exchanges") ?: JSONArray()
                    ),
                initialSummary =
                    ConversationMemoryPolicy.sanitizeSummary(
                        root.optString("summary")
                    ),
            )

        val sessionObject =
            root.optJSONObject("antigravity_session")
        val session =
            sessionObject?.let {
                val interactionId =
                    it.optString("interaction_id")
                        .trim()
                        .take(MAX_SESSION_ID_CHARS)
                val environmentId =
                    it.optString("environment_id")
                        .trim()
                        .take(MAX_SESSION_ID_CHARS)
                val updatedAtMs =
                    it.optLong("updated_at_ms", 0L)

                AntigravitySession(
                    interactionId = interactionId,
                    environmentId = environmentId,
                    updatedAtMs = updatedAtMs,
                ).takeIf {
                    sessionValue ->
                        sessionValue.interactionId.isNotBlank() &&
                            sessionValue.environmentId.isNotBlank() &&
                            sessionValue.updatedAtMs > 0L
                }
            }

        return memory.state(session)
    }

    private fun parseExchanges(
        array: JSONArray,
    ): List<ConversationExchange> {
        val memory = ConversationMemory()

        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            memory.append(
                user = item.optString("user"),
                assistant = item.optString("assistant"),
            )
        }

        return memory.snapshot()
    }

    private fun saveState(
        state: ConversationMemoryState,
    ) {
        if (
            state.exchanges.isEmpty() &&
            state.summary.isBlank() &&
            state.antigravitySession == null
        ) {
            clearData()
            return
        }

        val exchanges = JSONArray()
        state.exchanges.forEach { exchange ->
            exchanges.put(
                JSONObject()
                    .put("user", exchange.user)
                    .put("assistant", exchange.assistant)
            )
        }

        val root =
            JSONObject()
                .put("version", 2)
                .put("summary", state.summary)
                .put("exchanges", exchanges)

        state.antigravitySession?.let { session ->
            root.put(
                "antigravity_session",
                JSONObject()
                    .put(
                        "interaction_id",
                        session.interactionId.take(MAX_SESSION_ID_CHARS),
                    )
                    .put(
                        "environment_id",
                        session.environmentId.take(MAX_SESSION_ID_CHARS),
                    )
                    .put(
                        "updated_at_ms",
                        session.updatedAtMs,
                    )
            )
        }

        val packed = encrypt(root.toString())
        require(packed.length <= MAX_PACKED_CHARS) {
            "Conversation memory exceeded its storage limit."
        }

        preferences.edit()
            .putString(DATA_KEY, packed)
            .apply()
    }

    private fun encrypt(value: String): String {
        val cipher =
            Cipher.getInstance(TRANSFORMATION)

        cipher.init(
            Cipher.ENCRYPT_MODE,
            getOrCreateKey(),
        )

        val encrypted =
            cipher.doFinal(
                value.toByteArray(Charsets.UTF_8)
            )

        return Base64.encodeToString(
            cipher.iv,
            Base64.NO_WRAP,
        ) +
            "." +
            Base64.encodeToString(
                encrypted,
                Base64.NO_WRAP,
            )
    }

    private fun decrypt(packed: String): String {
        val pieces =
            packed.split(".", limit = 2)
        require(pieces.size == 2)

        val iv =
            Base64.decode(
                pieces[0],
                Base64.NO_WRAP,
            )
        val encrypted =
            Base64.decode(
                pieces[1],
                Base64.NO_WRAP,
            )

        require(iv.size == GCM_IV_BYTES)
        require(encrypted.isNotEmpty())

        val cipher =
            Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, iv),
        )

        return String(
            cipher.doFinal(encrypted),
            Charsets.UTF_8,
        )
    }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)
            ?.let { return it }

        val generator =
            KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore",
            )

        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or
                    KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(
                    KeyProperties.BLOCK_MODE_GCM,
                )
                .setEncryptionPaddings(
                    KeyProperties.ENCRYPTION_PADDING_NONE,
                )
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )

        return generator.generateKey()
    }

    private fun clearData() {
        preferences.edit()
            .remove(DATA_KEY)
            .apply()
    }

    companion object {
        private const val DATA_KEY = "conversation_v1"
        private const val KEY_ALIAS =
            "haru_conversation_memory_key_v1"
        private const val TRANSFORMATION =
            "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
        private const val MAX_PACKED_CHARS = 512_000
        private const val MAX_SESSION_ID_CHARS = 512
    }
}
