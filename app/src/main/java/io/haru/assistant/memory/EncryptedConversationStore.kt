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
    fun load(): List<ConversationExchange> {
        val packed =
            preferences.getString(DATA_KEY, "").orEmpty()

        if (packed.isBlank()) return emptyList()
        if (packed.length > MAX_PACKED_CHARS) {
            clearData()
            return emptyList()
        }

        return runCatching {
            val raw = decrypt(packed)
            val array = JSONArray(raw)
            val memory = ConversationMemory()

            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                memory.append(
                    user = item.optString("user"),
                    assistant = item.optString("assistant"),
                )
            }

            memory.snapshot()
        }.getOrElse {
            clearData()
            emptyList()
        }
    }

    @Synchronized
    fun appendCompleted(
        user: String,
        assistant: String,
    ): List<ConversationExchange> {
        val memory = ConversationMemory(load())
        val updated = memory.append(user, assistant)
        save(updated)
        return updated
    }

    @Synchronized
    fun clear() {
        clearData()
    }

    private fun save(
        exchanges: List<ConversationExchange>,
    ) {
        if (exchanges.isEmpty()) {
            clearData()
            return
        }

        val array = JSONArray()
        exchanges.forEach { exchange ->
            array.put(
                JSONObject()
                    .put("user", exchange.user)
                    .put("assistant", exchange.assistant)
            )
        }

        val packed = encrypt(array.toString())
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
    }
}
