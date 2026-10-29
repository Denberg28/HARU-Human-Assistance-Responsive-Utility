package io.haru.assistant

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.haru.assistant.journal.JournalEntry
import io.haru.assistant.journal.JournalStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class HaruViewModel(application: Application) : AndroidViewModel(application) {
    private val store = JournalStore(application)
    private val pending = linkedMapOf<LocalDate, JournalEntry?>()
    private val saves = Channel<Boolean>(Channel.CONFLATED)
    var entries by mutableStateOf(emptyList<JournalEntry>())
        private set
    var ready by mutableStateOf(false)
        private set
    var status by mutableStateOf("Opening journal…")
        private set
    var loadFailed by mutableStateOf(false)
        private set

    init {
        reload()
        viewModelScope.launch {
            for (immediate in saves) {
                if (!immediate) delay(300)
                val batch = pending.toMap()
                if (batch.isEmpty()) continue
                try {
                    withContext(Dispatchers.IO) { store.save(batch) }
                    batch.forEach { (date, value) -> if (pending[date] == value) pending.remove(date) }
                    status = if (pending.isEmpty()) "Saved on this phone" else "Saving…"
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    status = "Could not save. Your draft is still here. Tap Save to retry."
                }
            }
        }
    }

    fun reload() {
        if (ready) return
        viewModelScope.launch {
            loadFailed = false
            try {
                entries = withContext(Dispatchers.IO) { store.load() }
                ready = true
                status = "Saved on this phone"
            } catch (e: Exception) {
                loadFailed = true
                status = "Could not open the journal. Existing entries have been kept. Tap Retry."
            }
        }
    }

    fun update(date: LocalDate, text: String, memorable: Boolean) {
        if (!ready || text.length > MAX_TEXT_CHARS) return
        val entry = JournalEntry(date, text, memorable)
        // A blank unstarred day is an empty date, not an accumulating empty record.
        val value = entry.takeIf { text.isNotEmpty() || memorable }
        pending[date] = value
        entries = (entries.filterNot { it.date == date } + listOfNotNull(value)).sortedByDescending { it.date }
        status = "Saving…"
        saves.trySend(false)
    }
    fun delete(date: LocalDate) = update(date, "", false)
    fun flush() { saves.trySend(true) }
    companion object { const val MAX_TEXT_CHARS = 50_000 }
}
