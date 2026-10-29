package io.haru.assistant.journal

import java.time.LocalDate
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class JournalStoreTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
    private fun store() = JournalStore(context, JournalEncryption { key })
    @Test fun encryptedTextDateAndStarSurviveEditReopenAndDelete() {
        val date = LocalDate.of(2026, 10, 29)
        val entry = JournalEntry(date, "A memorable day 🐱\nSecond line", true, 42)
        store().use { db ->
            db.save(mapOf(date to entry))
            db.readableDatabase.rawQuery("SELECT body FROM journal", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertFalse(cursor.getString(0).contains("memorable"))
            }
        }
        store().use { db ->
            assertEquals(listOf(entry), db.load())
            val edit = entry.copy(text = "Changed", memorable = false)
            db.save(mapOf(date to edit))
            assertEquals(listOf(edit), db.load())
            db.save(mapOf(date to null))
            assertTrue(db.load().isEmpty())
        }
    }
    @Test fun wrongKeyOrDateFailsWithoutDeletingSavedEntry() {
        val date = LocalDate.of(2024, 2, 29)
        val entry = JournalEntry(date, "Private", true)
        store().use { it.save(mapOf(date to entry)) }
        val wrong = SecretKeySpec(ByteArray(32) { 7 }, "AES")
        JournalStore(context, JournalEncryption { wrong }).use { db ->
            assertThrows(Exception::class.java) { db.load() }
        }
        store().use { assertEquals(listOf(entry), it.load()) }
        val encryption = JournalEncryption { key }
        val packed = encryption.encrypt("Bound to date", date)
        assertThrows(Exception::class.java) { encryption.decrypt(packed, date.plusDays(1)) }
    }
    @Test fun failedBatchRollsBackEarlierRowsAndKeepsOriginalRecord() {
        val first = LocalDate.of(2026, 1, 1)
        val second = first.plusDays(1)
        val original = JournalEntry(first, "Original", true)
        store().use { it.save(mapOf(first to original)) }
        var calls = 0
        JournalStore(context, JournalEncryption {
            calls++
            check(calls < 2) { "Simulated key access failure" }
            key
        }).use { db ->
            assertThrows(Exception::class.java) {
                db.save(linkedMapOf(first to original.copy(text = "Changed"), second to JournalEntry(second, "New")))
            }
        }
        store().use { assertEquals(listOf(original), it.load()) }
    }

}
