package io.haru.assistant.location

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class LocationSharingTest {
    @Test fun encryptedShareRoundTripAndStopErrorsAreVisible() = runBlocking {
        var packed = ""
        val expires = Instant.now().plusSeconds(3600).toString()
        val manager = HaruLiveLocationManager { request ->
            when (request.getString("action")) {
                "create" -> {
                    packed = request.getString("payload")
                    JSONObject().put("expires_at", expires).put("seq", 1L)
                }
                "read" -> JSONObject().put("expires_at", expires).put("seq", 1L).put("payload", packed)
                else -> throw LiveLocationException(503, "Test outage")
            }
        }
        val bundle = manager.create("Test share", 14.0, 123.0, 5.0, 60)
        val monitor = manager.parseLiveCode(bundle.shareText)!!
        val snapshot = manager.read(monitor)
        assertEquals(14.0, snapshot.latitude, 0.0)
        assertEquals("Test share", snapshot.name)
        assertFalse(packed.contains("123.0"))
        try {
            manager.stop(bundle.session)
            fail("Revocation outage must be reported")
        } catch (error: LiveLocationException) {
            assertEquals(503, error.statusCode)
            assertFalse(error.terminal)
        }
    }

    @Test fun lostUploadResponseRecoversSequenceAndRetriesOnlyOnce() = runBlocking {
        var packed = ""
        var attempts = 0
        val expires = Instant.now().plusSeconds(3600).toString()
        val manager = HaruLiveLocationManager { request ->
            when (request.getString("action")) {
                "create" -> {
                    packed = request.getString("payload")
                    JSONObject().put("expires_at", expires).put("seq", 1L)
                }
                "read" -> JSONObject().put("expires_at", expires).put("seq", 2L).put("payload", packed)
                "update" -> {
                    attempts += 1
                    if (attempts == 1) throw LiveLocationException(409, "Sequence changed")
                    assertEquals(2L, request.getLong("expected_seq"))
                    JSONObject().put("seq", 3L)
                }
                else -> error("Unexpected request")
            }
        }
        val bundle = manager.create("Test", 14.0, 123.0, 5.0, 60)
        assertEquals(3L, manager.update(bundle.session, 14.1, 123.1, 5.0, 1L))
        assertEquals(2, attempts)
    }

    @Test fun malformedLiveCodesCannotFallBackToSnapshotLinks() {
        val manager = HaruLiveLocationManager()
        val id = "12345678-1234-4123-8123-123456789abc"
        val token = "a".repeat(32)
        assertNotNull(manager.parseLiveCode("HARU-LIVE:v1:$id:$token"))
        listOf("HARU-LIVE:v2:$id:$token", "HARU-LIVE:v1:${"-".repeat(36)}:$token",
            "HARU-LIVE:v1:$id:${"a".repeat(97)}").forEach {
            try { manager.parseLiveCode(it); fail("Malformed live code accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun refreshingSameSnapshotReplacesItsOldExpiryAndBadSignedCodesRejectFallback() {
        val context = RuntimeEnvironment.getApplication()
        val preferences = context.getSharedPreferences("haru_trusted_locations", 0)
        val now = System.currentTimeMillis()
        preferences.edit().putString("items", """[{"id":"old","name":"old","lat":14.0,"lon":123.0,"exp":${now + 10000L}}]""").commit()
        val manager = TrustedLocationManager(context)
        val url = manager.googleMapsUrl(14.0, 123.0)
        val imported = manager.importShareCode(url)
        val saved = manager.load()
        assertEquals(1, saved.size)
        assertEquals(imported.id, saved.single().id)
        assertTrue(saved.single().expiresAt > now + 10000L)
        try { manager.importShareCode("HARU-CODE:broken\n$url"); fail("Malformed code fell back to URL") }
        catch (_: IllegalArgumentException) { }
        manager.clear()
    }
}
