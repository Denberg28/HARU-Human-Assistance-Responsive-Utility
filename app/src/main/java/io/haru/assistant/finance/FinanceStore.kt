package io.haru.assistant.finance

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import java.time.LocalDate
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Each transaction is atomic. All financial fields are encrypted, bound to their row ID. */
class FinanceStore(context: Context, private val encryption: FinanceEncryption = FinanceEncryption()) :
    SQLiteOpenHelper(context, "haru_finance.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE entries (id TEXT PRIMARY KEY, payload TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun load(): List<FinanceEntry> = readableDatabase.query("entries", null, null, null, null, null, null).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                val id = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                val json = JSONObject(encryption.decrypt(cursor.getString(cursor.getColumnIndexOrThrow("payload")), id))
                add(FinanceEntry(id, LocalDate.parse(json.getString("date")), FinanceType.valueOf(json.getString("type")),
                    json.getLong("cents"), json.getString("category"), json.getString("note"), json.getLong("createdAt")))
            }
        }
    }

    fun save(entry: FinanceEntry) {
        val payload = JSONObject().put("date", entry.date.toString()).put("type", entry.type.name)
            .put("cents", entry.cents).put("category", entry.category).put("note", entry.note).put("createdAt", entry.createdAt).toString()
        val values = ContentValues().apply {
            put("id", entry.id)
            put("payload", encryption.encrypt(payload, entry.id))
        }
        check(writableDatabase.insertWithOnConflict("entries", null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L)
    }

    fun delete(id: String) { writableDatabase.delete("entries", "id = ?", arrayOf(id)) }
}

class FinanceEncryption(private val key: () -> SecretKey = ::financeKey) {
    fun encrypt(text: String, id: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(id.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    fun decrypt(packed: String, id: String): String {
        val bytes = Base64.decode(packed, Base64.NO_WRAP)
        require(bytes.size >= 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(id.toByteArray(Charsets.UTF_8))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }
}

private fun financeKey(): SecretKey {
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (store.getKey("haru_finance_v1", null) as? SecretKey)?.let { return it }
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
        init(KeyGenParameterSpec.Builder("haru_finance_v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
    }.generateKey()
}
