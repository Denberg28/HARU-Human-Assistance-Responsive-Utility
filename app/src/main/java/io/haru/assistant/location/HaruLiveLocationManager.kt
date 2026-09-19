package io.haru.assistant.location

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class LiveLocationSession(
    val sessionId: String,
    val readToken: String,
    val writeToken: String,
    val name: String,
    val expiresAt: Long,
    val seq: Long,
)

data class LiveMonitorSession(
    val sessionId: String,
    val readToken: String,
)

data class LiveLocationSnapshot(
    val sessionId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Double?,
    val capturedAt: Long,
    val expiresAt: Long,
    val seq: Long,
)

data class LiveLocationShareBundle(
    val session: LiveLocationSession,
    val liveCode: String,
    val googleMapsUrl: String,
    val shareText: String,
)

class HaruLiveLocationManager {
    suspend fun create(
        name: String,
        latitude: Double,
        longitude: Double,
        accuracyM: Double?,
        expiresMinutes: Int,
    ): LiveLocationShareBundle = withContext(Dispatchers.IO) {
        validateCoordinates(latitude, longitude)

        val sessionId = UUID.randomUUID().toString()
        val readToken = randomToken()
        val writeToken = randomToken()
        val cleanName = cleanName(name)
        val duration = expiresMinutes.coerceIn(15, 120)
        val capturedAt = System.currentTimeMillis()

        val encrypted = encryptLocation(
            sessionId = sessionId,
            readToken = readToken,
            name = cleanName,
            latitude = latitude,
            longitude = longitude,
            accuracyM = accuracyM,
            capturedAt = capturedAt,
        )

        val response = post(
            JSONObject()
                .put("action", "create")
                .put("session_id", sessionId)
                .put("read_token", readToken)
                .put("write_token", writeToken)
                .put("payload", encrypted)
                .put("ttl_minutes", duration)
        )

        val expiresAt = parseIsoMillis(response.getString("expires_at"))
        val seq = response.optLong("seq", 1L)
        val liveCode = "HARU-LIVE:v1:$sessionId:$readToken"
        val mapUrl = googleMapsUrl(latitude, longitude)

        LiveLocationShareBundle(
            session = LiveLocationSession(
                sessionId = sessionId,
                readToken = readToken,
                writeToken = writeToken,
                name = cleanName,
                expiresAt = expiresAt,
                seq = seq,
            ),
            liveCode = liveCode,
            googleMapsUrl = mapUrl,
            shareText = buildString {
                append(cleanName)
                append(" is sharing a live location with HARU.\n")
                append("Open HARU > Map and paste the HARU-LIVE code below.\n\n")
                append(liveCode)
                append("\n\nCurrent snapshot:\n")
                append(mapUrl)
                append("\n\nLive updates expire automatically. ")
                append("HARU shares only while the sender app is active.")
            },
        )
    }

    suspend fun update(
        session: LiveLocationSession,
        latitude: Double,
        longitude: Double,
        accuracyM: Double?,
        expectedSeq: Long,
    ): Long = withContext(Dispatchers.IO) {
        validateCoordinates(latitude, longitude)
        require(System.currentTimeMillis() < session.expiresAt) {
            "Live share expired."
        }

        val encrypted = encryptLocation(
            sessionId = session.sessionId,
            readToken = session.readToken,
            name = session.name,
            latitude = latitude,
            longitude = longitude,
            accuracyM = accuracyM,
            capturedAt = System.currentTimeMillis(),
        )

        val response = post(
            JSONObject()
                .put("action", "update")
                .put("session_id", session.sessionId)
                .put("write_token", session.writeToken)
                .put("payload", encrypted)
                .put("expected_seq", expectedSeq)
        )

        response.getLong("seq")
    }

    suspend fun read(
        monitor: LiveMonitorSession,
    ): LiveLocationSnapshot = withContext(Dispatchers.IO) {
        val response = post(
            JSONObject()
                .put("action", "read")
                .put("session_id", monitor.sessionId)
                .put("read_token", monitor.readToken)
        )

        val seq = response.getLong("seq")
        val expiresAt = parseIsoMillis(response.getString("expires_at"))
        val payload = decryptLocation(
            sessionId = monitor.sessionId,
            readToken = monitor.readToken,
            packed = response.getString("payload"),
        )

        val latitude = payload.getDouble("lat")
        val longitude = payload.getDouble("lon")
        validateCoordinates(latitude, longitude)

        LiveLocationSnapshot(
            sessionId = monitor.sessionId,
            name = cleanName(payload.optString("name", "Live location")),
            latitude = latitude,
            longitude = longitude,
            accuracyM = if (payload.isNull("acc")) {
                null
            } else {
                payload.optDouble("acc")
                    .takeIf { it in 0.0..100_000.0 }
            },
            capturedAt = payload.getLong("captured_at"),
            expiresAt = expiresAt,
            seq = seq,
        )
    }

    suspend fun stop(
        session: LiveLocationSession,
    ) = withContext(Dispatchers.IO) {
        runCatching {
            post(
                JSONObject()
                    .put("action", "stop")
                    .put("session_id", session.sessionId)
                    .put("write_token", session.writeToken)
            )
        }
        Unit
    }

    fun parseLiveCode(value: String): LiveMonitorSession? {
        val match = LIVE_CODE_PATTERN.find(value.trim()) ?: return null
        val sessionId = match.groupValues[1]
        val token = match.groupValues[2]
        return LiveMonitorSession(
            sessionId = sessionId,
            readToken = token,
        )
    }

    fun googleMapsUrl(
        latitude: Double,
        longitude: Double,
    ): String {
        validateCoordinates(latitude, longitude)
        return "https://www.google.com/maps/search/?api=1&query=" +
            String.format(
                java.util.Locale.US,
                "%.6f,%.6f",
                latitude,
                longitude,
            )
    }

    private fun encryptLocation(
        sessionId: String,
        readToken: String,
        name: String,
        latitude: Double,
        longitude: Double,
        accuracyM: Double?,
        capturedAt: Long,
    ): String {
        val payload = JSONObject()
            .put("v", 1)
            .put("name", cleanName(name))
            .put("lat", latitude)
            .put("lon", longitude)
            .put("acc", accuracyM)
            .put("captured_at", capturedAt)
            .toString()
            .toByteArray(Charsets.UTF_8)

        require(payload.size <= MAX_PLAINTEXT_BYTES) {
            "Location payload is too large."
        }

        val iv = ByteArray(GCM_IV_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(deriveKey(readToken), "AES"),
            GCMParameterSpec(128, iv),
        )
        cipher.updateAAD(sessionId.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(payload)

        return encode(iv) + "." + encode(encrypted)
    }

    private fun decryptLocation(
        sessionId: String,
        readToken: String,
        packed: String,
    ): JSONObject {
        require(packed.length in 16..MAX_PACKED_CHARS) {
            "Invalid live location payload."
        }
        val parts = packed.split(".", limit = 2)
        require(parts.size == 2) {
            "Invalid live location payload."
        }

        val iv = decode(parts[0])
        val encrypted = decode(parts[1])
        require(iv.size == GCM_IV_BYTES) {
            "Invalid live location payload."
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(deriveKey(readToken), "AES"),
            GCMParameterSpec(128, iv),
        )
        cipher.updateAAD(sessionId.toByteArray(Charsets.UTF_8))
        val raw = cipher.doFinal(encrypted)
        require(raw.size <= MAX_PLAINTEXT_BYTES) {
            "Live location payload is too large."
        }

        val json = JSONObject(String(raw, Charsets.UTF_8))
        require(json.optInt("v") == 1) {
            "Unsupported live location payload."
        }
        return json
    }

    private fun deriveKey(token: String): ByteArray =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))

    private fun randomToken(): String =
        encode(ByteArray(24).also(secureRandom::nextBytes))

    private fun cleanName(value: String): String =
        value.trim()
            .replace(Regex("\\s+"), " ")
            .take(40)
            .ifBlank { "Live location" }

    private fun validateCoordinates(
        latitude: Double,
        longitude: Double,
    ) {
        require(
            latitude in -90.0..90.0 &&
                longitude in -180.0..180.0
        ) {
            "Location coordinates are invalid."
        }
    }

    private fun post(payload: JSONObject): JSONObject {
        val connection =
            URL(ENDPOINT).openConnection() as HttpURLConnection

        connection.requestMethod = "POST"
        connection.connectTimeout = 10_000
        connection.readTimeout = 12_000
        connection.useCaches = false
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Cache-Control", "no-store")
        connection.setRequestProperty("User-Agent", "HARU-Android/0.3")

        try {
            connection.outputStream
                .bufferedWriter(Charsets.UTF_8)
                .use { writer ->
                    writer.write(payload.toString())
                }

            val code = connection.responseCode
            val raw = (
                if (code in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
            )
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText().take(MAX_RESPONSE_CHARS) }
                .orEmpty()

            if (code !in 200..299) {
                val error = runCatching {
                    JSONObject(raw).optString("error")
                }.getOrNull().orEmpty()

                throw IllegalStateException(
                    when (code) {
                        403 -> "Live share token was rejected."
                        404 -> "Live share was not found."
                        410 -> "Live share has expired."
                        429 -> "Live location is updating too quickly."
                        in 500..599 ->
                            "Live location service is temporarily unavailable."
                        else ->
                            error.ifBlank {
                                "Live location request failed (HTTP $code)."
                            }
                    }
                )
            }

            return if (raw.isBlank()) {
                JSONObject()
            } else {
                JSONObject(raw)
            }
        } finally {
            connection.disconnect()
        }
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

    private fun parseIsoMillis(value: String): Long =
        java.time.Instant.parse(value).toEpochMilli()

    companion object {
        private const val ENDPOINT =
            "https://ufnhmxnlxmhaynmkaynz.supabase.co/functions/v1/" +
                "haru-live-location"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
        private const val MAX_PLAINTEXT_BYTES = 1024
        private const val MAX_PACKED_CHARS = 4096
        private const val MAX_RESPONSE_CHARS = 8192

        private val LIVE_CODE_PATTERN =
            Regex(
                "HARU-LIVE:v1:" +
                    "([0-9a-fA-F-]{36}):" +
                    "([A-Za-z0-9_-]{32,96})"
            )

        private val secureRandom = SecureRandom()
    }
}
