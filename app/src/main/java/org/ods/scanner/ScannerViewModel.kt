package org.ods.scanner

import android.app.Application
import android.net.Uri
import androidx.lifecycle.*
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

class ScannerViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    val repository = Repository(application)
    private val settings = SettingsStore(application)
    val theme = settings.theme.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "System")
    fun setTheme(value: String) { viewModelScope.launch { try { settings.theme(value) } catch (e: Exception) { error = e.message } } }
    val documents = repository.dao.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    var activeId by mutableStateOf<String?>(savedState["documentId"])
        private set
    fun openDocument(id: String?) { activeId = id; savedState["documentId"] = id }
    var screen by mutableStateOf("library")
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var exports by mutableStateOf<List<File>>(emptyList())
    private var job: Job? = null
    fun work(block: suspend () -> Unit) {
        if (busy) return
        job = viewModelScope.launch {
            busy = true; error = null
            try { block() } catch (e: CancellationException) { throw e } catch (e: OutOfMemoryError) { error = "Not enough memory. Try fewer pages or Small export quality." } catch (e: Exception) { error = e.message ?: "Operation failed. Please try again." } finally { busy = false }
        }
    }
    fun cancel() { job?.cancel() }
    private suspend fun destination(): Document {
        val existing = documents.value.firstOrNull { it.document.id == activeId }?.document
        if (existing != null) return existing
        return Document(title = "Scan ${java.text.SimpleDateFormat("dd MMM HH.mm", java.util.Locale.getDefault()).format(java.util.Date())}").also { repository.dao.insert(it); openDocument(it.id) }
    }
    fun import(uris: List<Uri>) = work {
        if (uris.isEmpty()) return@work
        val document = destination()
        val start = documents.value.firstOrNull { it.document.id == document.id }?.pages?.maxOfOrNull { it.position }?.plus(1) ?: 0
        uris.forEachIndexed { index, uri -> repository.import(uri, document, start + index) }
        screen = "document"
    }
    fun captured(file: File) = work {
        val document = destination()
        val position = documents.value.firstOrNull { it.document.id == document.id }?.pages?.maxOfOrNull { it.position }?.plus(1) ?: 0
        repository.dao.insert(Page(documentId = document.id, original = file.name, position = position))
    }
    fun update(page: Page) = work { repository.dao.update(page) }
    fun export(docs: List<DocumentPages>, format: Format, quality: Quality, name: String, merge: Boolean) = work {
        exports = Exporter(repository).generate(docs, format, quality, name, merge)
    }
}
