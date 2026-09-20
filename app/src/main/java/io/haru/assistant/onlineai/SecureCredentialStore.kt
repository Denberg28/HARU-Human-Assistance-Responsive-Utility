package io.haru.assistant.onlineai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small Android Keystore-backed credential store.
 *
 * Only explicitly supported credential names are accepted so callers cannot
 * accidentally turn this into a generic plaintext/preferences channel.
 */
class SecureCredentialStore(
    context: Context,
) {
    private val preferences =
        context.getSharedPreferences(
            "haru_secure_credentials",
            Context.MODE_PRIVATE,
        )

    private val keyStore =
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }

    fun put(name: String, value: String) {
        requireValidName(name)

        val clean = value.trim()
        if (clean.isEmpty()) {
            delete(name)
            return
        }
        require(clean.length <= MAX_SECRET_CHARS) {
            "Credential is unexpectedly large."
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            getOrCreateKey(),
        )
        val encrypted =
            cipher.doFinal(clean.toByteArray(Charsets.UTF_8))

        val packed =
            Base64.encodeToString(
                cipher.iv,
                Base64.NO_WRAP,
            ) +
                "." +
                Base64.encodeToString(
                    encrypted,
                    Base64.NO_WRAP,
                )

        preferences.edit()
            .putString(name, packed)
            .apply()
    }

    fun get(name: String): String {
        requireValidName(name)

        val packed =
            preferences.getString(name, "").orEmpty()
        if (packed.isBlank()) return ""
        if (packed.length > MAX_PACKED_CHARS) {
            delete(name)
            return ""
        }

        return runCatching {
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

            String(
                cipher.doFinal(encrypted),
                Charsets.UTF_8,
            ).trim()
        }.getOrElse {
            delete(name)
            ""
        }
    }

    fun delete(name: String) {
        requireValidName(name)
        preferences.edit()
            .remove(name)
            .apply()
    }

    private fun requireValidName(name: String) {
        require(name in ALLOWED_NAMES) {
            "Unsupported credential name."
        }
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

    companion object {
        private const val KEY_ALIAS =
            "haru_credentials_v1"
        private const val TRANSFORMATION =
            "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
        private const val MAX_SECRET_CHARS = 1024
        private const val MAX_PACKED_CHARS = 4096

        private val ALLOWED_NAMES =
            setOf(
                "gemini",
                "groq",
                // Kept only so old installs can remove it safely.
                "openrouter",
            )
    }
}
