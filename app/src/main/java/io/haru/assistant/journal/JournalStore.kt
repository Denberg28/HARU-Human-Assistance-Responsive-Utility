package io.haru.assistant.journal

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.time.LocalDate
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** One row per local calendar date. Transactions commit body and star together.
 * Journal text is encrypted at rest; failures never overwrite unreadable content.
 */
class JournalStore(context: Context, private val encryption: JournalEncryption = JournalEncryption()) : SQLiteOpenHelper(context, "haru_journal.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE journal (date TEXT PRIMARY KEY, body TEXT NOT NULL, memorable INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun load(): List<JournalEntry> = readableDatabase.query(
        "journal", null, null, null, null, null, "date DESC",
    ).use { cursor -> buildList {
        while (cursor.moveToNext()) {
            val date = LocalDate.parse(cursor.getString(cursor.getColumnIndexOrThrow("date")))
            add(JournalEntry(date,
                encryption.decrypt(cursor.getString(cursor.getColumnIndexOrThrow("body")), date),
                cursor.getInt(cursor.getColumnIndexOrThrow("memorable")) != 0,
                cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"))))
        }
    } }

    fun save(changes: Map<LocalDate, JournalEntry?>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            changes.forEach { (date, entry) ->
                if (entry == null) db.delete("journal", "date = ?", arrayOf(date.toString()))
                else db.insertWithOnConflict("journal", null, ContentValues().apply {
                    put("date", date.toString()); put("body", encryption.encrypt(entry.text, date))
                    put("memorable", if (entry.memorable) 1 else 0); put("updated_at", entry.updatedAt)
                }, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

}

class JournalEncryption(private val key: () -> SecretKey = ::androidJournalKey) {
    fun encrypt(text: String, date: LocalDate): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(date.toString().toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    fun decrypt(packed: String, date: LocalDate): String {
        val bytes = Base64.decode(packed, Base64.NO_WRAP)
        require(bytes.size >= 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(date.toString().toByteArray(Charsets.UTF_8))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }
    companion object { private const val KEY_ALIAS = "haru_journal_v1" }
}

private fun androidJournalKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("haru_journal_v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("haru_journal_v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }
