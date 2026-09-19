package io.haru.assistant.location

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.UUID

data class TrustedLocation(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Double?,
    val expiresAt: Long,
)

class TrustedLocationManager(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val preferences =
        appContext.getSharedPreferences("haru_trusted_locations", Context.MODE_PRIVATE)

    fun createShareCode(
        name: String,
        latitude: Double,
        longitude: Double,
        accuracyM: Double?,
        expiresMinutes: Int = 60,
    ): String {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0) {
            "Location coordinates are invalid."
        }

        val now = System.currentTimeMillis()
        val duration = expiresMinutes.coerceIn(15, 24 * 60)
        val payload = JSONObject()
            .put("v", 1)
            .put("name", cleanName(name))
            .put("lat", latitude)
            .put("lon", longitude)
            .put("acc", accuracyM)
            .put("iat", now)
            .put("exp", now + duration * 60_000L)
            .put("nonce", UUID.randomUUID().toString())

        val raw = payload.toString().toByteArray(Charsets.UTF_8)
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(getOrCreateKeyPair().private)
            update(raw)
            sign()
        }
        val publicKey = getOrCreateKeyPair().public.encoded

        return listOf(
            encode(raw),
            encode(publicKey),
            encode(signature),
        ).joinToString(".")
    }

    fun importShareCode(code: String): TrustedLocation {
        val clean = code.replace(Regex("\\s+"), "")
        require(clean.length in 20..4000) { "Invalid location share code." }

        val parts = clean.split(".")
        require(parts.size == 3) { "Invalid location share code." }

        val raw = decode(parts[0])
        val publicBytes = decode(parts[1])
        val signatureBytes = decode(parts[2])

        val publicKey = KeyFactory.getInstance("EC").generatePublic(
            X509EncodedKeySpec(publicBytes)
        )
        val valid = Signature.getInstance("SHA256withECDSA").run {
            initVerify(publicKey)
            update(raw)
            verify(signatureBytes)
        }
        require(valid) { "Location share code was altered." }

        val payload = JSONObject(String(raw, Charsets.UTF_8))
        require(payload.optInt("v") == 1) { "Unsupported location share code." }

        val lat = payload.getDouble("lat")
        val lon = payload.getDouble("lon")
        require(lat in -90.0..90.0 && lon in -180.0..180.0) {
            "Location coordinates are invalid."
        }

        val expires = payload.getLong("exp")
        require(expires > System.currentTimeMillis()) {
            "This location share has expired."
        }

        val issued = payload.optLong("iat", 0L)
        require(
            issued > 0L &&
                expires - issued <= 24L * 60L * 60L * 1000L + 60_000L
        ) {
            "Location share duration is invalid."
        }

        val accuracy = if (payload.isNull("acc")) {
            null
        } else {
            payload.optDouble("acc").takeIf { it in 0.0..100_000.0 }
        }

        val item = TrustedLocation(
            id = UUID.randomUUID().toString(),
            name = cleanName(payload.optString("name", "Loved one")),
            latitude = lat,
            longitude = lon,
            accuracyM = accuracy,
            expiresAt = expires,
        )
        save((load() + item).takeLast(20))
        return item
    }

    fun load(): List<TrustedLocation> {
        val now = System.currentTimeMillis()
        val raw = preferences.getString("items", "[]").orEmpty()
        val items = runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val lat = item.optDouble("lat", Double.NaN)
                    val lon = item.optDouble("lon", Double.NaN)
                    val exp = item.optLong("exp", 0L)
                    if (
                        lat in -90.0..90.0 &&
                        lon in -180.0..180.0 &&
                        exp > now
                    ) {
                        add(
                            TrustedLocation(
                                id = item.optString("id").ifBlank {
                                    UUID.randomUUID().toString()
                                },
                                name = cleanName(item.optString("name", "Loved one")),
                                latitude = lat,
                                longitude = lon,
                                accuracyM = if (item.isNull("acc")) null else
                                    item.optDouble("acc").takeIf { it in 0.0..100_000.0 },
                                expiresAt = exp,
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())

        save(items)
        return items
    }

    fun clear() {
        preferences.edit().remove("items").apply()
    }

    private fun save(items: List<TrustedLocation>) {
        val array = JSONArray()
        items.takeLast(20).forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("lat", item.latitude)
                    .put("lon", item.longitude)
                    .put("acc", item.accuracyM)
                    .put("exp", item.expiresAt)
            )
        }
        preferences.edit().putString("items", array.toString()).apply()
    }

    private fun cleanName(value: String): String =
        value.trim().replace(Regex("\\s+"), " ").take(40).ifBlank { "Loved one" }

    private fun getOrCreateKeyPair(): java.security.KeyPair {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
        if (entry != null) {
            return java.security.KeyPair(entry.certificate.publicKey, entry.privateKey)
        }

        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            "AndroidKeyStore",
        )
        generator.initialize(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
            )
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        return generator.generateKeyPair()
    }

    private fun encode(bytes: ByteArray): String =
        Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )

    private fun decode(value: String): ByteArray =
        Base64.decode(
            value,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )

    companion object {
        private const val KEY_ALIAS = "haru_location_signing_v1"
    }
}
