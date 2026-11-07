package io.haru.assistant.finance

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FinanceViewModel(application: Application) : AndroidViewModel(application) {
    private val store = FinanceStore(application)
    var entries by mutableStateOf(emptyList<FinanceEntry>())
        private set
    var ready by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init { reload() }

    fun reload() {
        if (busy || ready) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                entries = withContext(Dispatchers.IO) { store.load() }
                ready = true
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = "Could not open finances. Your saved entries are kept. Retry to open them." }
            finally { busy = false }
        }
    }

    // Reflect only committed writes in totals; keep the editor draft on any failure.
    fun save(entry: FinanceEntry, onSaved: () -> Unit) = mutate(onSaved) {
        withContext(Dispatchers.IO) { store.save(entry) }
        entries = entries.filterNot { it.id == entry.id } + entry
    }
    fun delete(id: String, onDeleted: () -> Unit) = mutate(onDeleted) {
        withContext(Dispatchers.IO) { store.delete(id) }
        entries = entries.filterNot { it.id == id }
    }
    private fun mutate(onSuccess: () -> Unit, action: suspend () -> Unit) {
        if (!ready || busy) return
        busy = true
        error = null
        viewModelScope.launch {
            try { action(); onSuccess() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = "Could not save. Your draft is kept here. Please retry." }
            finally { busy = false }
        }
    }
}
