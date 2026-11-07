package io.haru.assistant.finance

import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 36])
class FinanceStoreTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
    private fun store() = FinanceStore(context, FinanceEncryption { key })
    // SQLiteOpenHelper gained AutoCloseable after API 26. Close explicitly so
    // the same persistence checks run against the app's actual minimum SDK.
    private fun <T> FinanceStore.withDatabase(block: (FinanceStore) -> T): T =
        try { block(this) } finally { close() }

    @Test fun encryptedEntriesSurviveReopenEditAndDeleteWithoutDuplicates() {
        val entry = FinanceEntry(cents = 12550, category = "Projects", note = "Motor 🛠")
        store().withDatabase { db ->
            db.save(entry)
            db.readableDatabase.rawQuery("SELECT payload FROM entries", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertFalse(cursor.getString(0).contains("Projects"))
                assertFalse(cursor.getString(0).contains("12550"))
            }
        }
        store().withDatabase { db ->
            assertEquals(listOf(entry), db.load())
            val edit = entry.copy(cents = 20000, type = FinanceType.INCOME, category = "Refund")
            db.save(edit)
            assertEquals(listOf(edit), db.load())
            db.delete(entry.id)
            assertTrue(db.load().isEmpty())
        }
    }

    @Test fun keyFailureOrTamperingCannotClearOrOverwriteSavedEntries() {
        val entry = FinanceEntry(cents = 101, category = "Food")
        store().withDatabase { it.save(entry) }
        val wrongKey = SecretKeySpec(ByteArray(32) { 7 }, "AES")
        FinanceStore(context, FinanceEncryption { wrongKey }).withDatabase { db ->
            assertThrows(Exception::class.java) { db.load() }
        }
        FinanceStore(context, FinanceEncryption { error("Key unavailable") }).withDatabase { db ->
            assertThrows(Exception::class.java) { db.save(entry.copy(cents = 500)) }
        }
        store().withDatabase { assertEquals(listOf(entry), it.load()) }
        val encryption = FinanceEncryption { key }
        val packed = encryption.encrypt("Private", entry.id)
        assertThrows(Exception::class.java) { encryption.decrypt(packed, "different-row") }
    }
}
