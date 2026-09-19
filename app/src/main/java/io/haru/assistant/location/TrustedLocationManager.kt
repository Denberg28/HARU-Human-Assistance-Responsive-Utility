package io.haru.assistant.location

import android.content.Context
import android.net.Uri
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
import java.util.Locale
import java.util.UUID

data class TrustedLocation(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Double?,
    val expiresAt: Long,
)

data class LocationShareBundle(
    val secureCode: String,
    val googleMapsUrl: String,
    val shareText: String,
)

class TrustedLocationManager(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val preferences =
        appContext.getSharedPreferences(
            "haru_trusted_locations",
            Context.MODE_PRIVATE,
        )

    fun createShareBundle(
        name: String,
        latitude: Double,
        longitude: Double,
        accuracyM: Double?,
        expiresMinutes: Int = 60,
    ): LocationShareBundle {
        val cleanName = cleanName(name)
        val duration = expiresMinutes.coerceIn(15, 24 * 60)
        val code = createShareCode(
            name = cleanName,
            latitude = latitude,
            longitude = longitude,
            accuracyM = accuracyM,
            expiresMinutes = duration,
        )
        val mapUrl = googleMapsUrl(latitude, longitude)

        val shareText = buildString {
            append(cleanName)
            append(" shared a HARU location snapshot:\n")
            append(mapUrl)
            append("\n\n")
            append("HARU-CODE:")
            append(code)
            append("\n")
            append("Valid inside HARU for ")
            append(duration)
            append(" minutes. This is a location snapshot, not live tracking.")
        }

        return LocationShareBundle(
            secureCode = code,
            googleMapsUrl = mapUrl,
            shareText = shareText,
        )
    }

    fun googleMapsUrl(
        latitude: Double,
        longitude: Double,
    ): String {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0) {
            "Location coordinates are invalid."
        }

        val coordinates = String.format(
            Locale.US,
            "%.6f,%.6f",
            latitude,
            longitude,
        )
        return "https://www.google.com/maps/search/?api=1&query=$coordinates"
    }

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
        val keyPair = getOrCreateKeyPair()
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(raw)
            sign()
        }
        val publicKey = keyPair.public.encoded

        return listOf(
            encode(raw),
            encode(publicKey),
            encode(signature),
        ).joinToString(".")
    }

    fun importShareCode(value: String): TrustedLocation {
        val input = value.trim()
        require(input.length in 10..MAX_IMPORT_CHARS) {
            "Invalid location share."
        }

        val signedCode = extractSignedCode(input)
        return if (signedCode != null) {
            importSignedCode(signedCode)
        } else {
            importGoogleMapsLink(input)
        }
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
                                name = cleanName(
                                    item.optString(
                                        "name",
                                        "Shared location",
                                    )
                                ),
                                latitude = lat,
                                longitude = lon,
                                accuracyM = if (item.isNull("acc")) {
                                    null
                                } else {
                                    item.optDouble("acc")
                                        .takeIf {
                                            it in 0.0..100_000.0
                                        }
                                },
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

    private fun importSignedCode(code: String): TrustedLocation {
        val clean = code.replace(Regex("\\s+"), "")
        require(clean.length in 20..4000) {
            "Invalid HARU location code."
        }

        val parts = clean.split(".")
        require(parts.size == 3) {
            "Invalid HARU location code."
        }

        val raw = decode(parts[0])
        val publicBytes = decode(parts[1])
        val signatureBytes = decode(parts[2])

        require(raw.size <= MAX_PAYLOAD_BYTES) {
            "HARU location payload is too large."
        }
        require(publicBytes.size <= MAX_PUBLIC_KEY_BYTES) {
            "HARU location key is too large."
        }
        require(signatureBytes.size <= MAX_SIGNATURE_BYTES) {
            "HARU location signature is too large."
        }

        val publicKey = KeyFactory
            .getInstance("EC")
            .generatePublic(
                X509EncodedKeySpec(publicBytes)
            )

        val valid = Signature
            .getInstance("SHA256withECDSA")
            .run {
                initVerify(publicKey)
                update(raw)
                verify(signatureBytes)
            }

        require(valid) {
            "HARU location code was altered."
        }

        val payload =
            JSONObject(String(raw, Charsets.UTF_8))

        require(payload.optInt("v") == 1) {
            "Unsupported HARU location code."
        }

        val lat = payload.getDouble("lat")
        val lon = payload.getDouble("lon")
        require(
            lat in -90.0..90.0 &&
                lon in -180.0..180.0
        ) {
            "Location coordinates are invalid."
        }

        val expires = payload.getLong("exp")
        require(expires > System.currentTimeMillis()) {
            "This HARU location share has expired."
        }

        val issued = payload.optLong("iat", 0L)
        require(
            issued > 0L &&
                expires - issued <=
                    24L * 60L * 60L * 1000L + 60_000L
        ) {
            "Location share duration is invalid."
        }

        val accuracy = if (payload.isNull("acc")) {
            null
        } else {
            payload.optDouble("acc")
                .takeIf { it in 0.0..100_000.0 }
        }

        return storeImportedLocation(
            name = cleanName(
                payload.optString(
                    "name",
                    "Shared location",
                )
            ),
            latitude = lat,
            longitude = lon,
            accuracyM = accuracy,
            expiresAt = expires,
        )
    }

    private fun importGoogleMapsLink(
        input: String,
    ): TrustedLocation {
        val url = URL_PATTERN
            .find(input)
            ?.value
            ?.trimEnd('.', ',', ';', ')', ']')
            ?: input

        val uri = runCatching {
            Uri.parse(url)
        }.getOrNull()
            ?: error("Paste a HARU share or Google Maps location link.")

        require(
            uri.scheme.equals("https", ignoreCase = true)
        ) {
            "Only HTTPS location links are supported."
        }

        val host = uri.host?.lowercase().orEmpty()
        require(
            host == "google.com" ||
                host == "www.google.com" ||
                host == "maps.google.com"
        ) {
            "Paste a HARU share or Google Maps location link."
        }

        val query = uri
            .getQueryParameter("query")
            ?.trim()
            .orEmpty()

        val match = COORDINATE_PATTERN
            .matchEntire(query)
            ?: error(
                "This Google Maps link does not contain readable coordinates."
            )

        val lat =
            match.groupValues[1].toDoubleOrNull()
                ?: error("Invalid latitude.")
        val lon =
            match.groupValues[2].toDoubleOrNull()
                ?: error("Invalid longitude.")

        require(
            lat in -90.0..90.0 &&
                lon in -180.0..180.0
        ) {
            "Location coordinates are invalid."
        }

        val now = System.currentTimeMillis()
        return storeImportedLocation(
            name = "Shared Google Maps location",
            latitude = lat,
            longitude = lon,
            accuracyM = null,
            expiresAt = now + DEFAULT_LINK_IMPORT_MINUTES * 60_000L,
        )
    }

    private fun storeImportedLocation(
        name: String,
        latitude: Double,
        longitude: Double,
        accuracyM: Double?,
        expiresAt: Long,
    ): TrustedLocation {
        val item = TrustedLocation(
            id = UUID.randomUUID().toString(),
            name = cleanName(name),
            latitude = latitude,
            longitude = longitude,
            accuracyM = accuracyM,
            expiresAt = expiresAt,
        )

        save(
            (load() + item)
                .distinctBy {
                    String.format(
                        Locale.US,
                        "%.6f,%.6f",
                        it.latitude,
                        it.longitude,
                    )
                }
                .takeLast(20)
        )
        return item
    }

    private fun extractSignedCode(
        input: String,
    ): String? {
        HARU_CODE_PATTERN
            .find(input)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { return it }

        val compact =
            input.replace(Regex("\\s+"), "")

        return if (
            compact.count { it == '.' } == 2 &&
            DIRECT_CODE_PATTERN.matches(compact)
        ) {
            compact
        } else {
            null
        }
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

        preferences.edit()
            .putString("items", array.toString())
            .apply()
    }

    private fun cleanName(value: String): String =
        value.trim()
            .replace(Regex("\\s+"), " ")
            .take(40)
            .ifBlank { "Shared location" }

    private fun getOrCreateKeyPair(): java.security.KeyPair {
        val keyStore =
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
            }

        val entry =
            keyStore.getEntry(
                KEY_ALIAS,
                null,
            ) as? KeyStore.PrivateKeyEntry

        if (entry != null) {
            return java.security.KeyPair(
                entry.certificate.publicKey,
                entry.privateKey,
            )
        }

        val generator =
            KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                "AndroidKeyStore",
            )

        generator.initialize(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or
                    KeyProperties.PURPOSE_VERIFY,
            )
                .setDigests(
                    KeyProperties.DIGEST_SHA256,
                )
                .build()
        )

        return generator.generateKeyPair()
    }

    private fun encode(bytes: ByteArray): String =
        Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or
                Base64.NO_WRAP or
                Base64.NO_PADDING,
        )

    private fun decode(value: String): ByteArray =
        Base64.decode(
            value,
            Base64.URL_SAFE or
                Base64.NO_WRAP or
                Base64.NO_PADDING,
        )

    companion object {
        private const val KEY_ALIAS =
            "haru_location_signing_v1"
        private const val MAX_IMPORT_CHARS = 8_000
        private const val MAX_PAYLOAD_BYTES = 2_048
        private const val MAX_PUBLIC_KEY_BYTES = 512
        private const val MAX_SIGNATURE_BYTES = 256
        private const val DEFAULT_LINK_IMPORT_MINUTES = 60

        private val HARU_CODE_PATTERN =
            Regex(
                "HARU-CODE:\\s*" +
                    "([A-Za-z0-9_-]+\\." +
                    "[A-Za-z0-9_-]+\\." +
                    "[A-Za-z0-9_-]+)",
                RegexOption.IGNORE_CASE,
            )

        private val DIRECT_CODE_PATTERN =
            Regex(
                "^[A-Za-z0-9_-]+\\." +
                    "[A-Za-z0-9_-]+\\." +
                    "[A-Za-z0-9_-]+$"
            )

        private val URL_PATTERN =
            Regex("https://[^\\s]+", RegexOption.IGNORE_CASE)

        private val COORDINATE_PATTERN =
            Regex(
                "^\\s*(-?\\d{1,2}(?:\\.\\d+)?)" +
                    "\\s*,\\s*" +
                    "(-?\\d{1,3}(?:\\.\\d+)?)\\s*$"
            )
    }
}
