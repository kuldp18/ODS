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
    var deleted by mutableStateOf<List<DocumentPages>>(emptyList())
        private set
    fun delete(ids: Set<String>) = work {
        val snapshot = documents.value.filter { it.document.id in ids }
        snapshot.forEach { repository.dao.delete(it.document.id) }; deleted = snapshot
    }
    fun undoDelete() = work { repository.dao.restore(deleted); deleted = emptyList() }
    fun merge(ids: Set<String>) = work {
        openDocument(repository.dao.merge(documents.value.filter { it.document.id in ids }, "Merged document")); screen = "document"
    }
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
        val existing = activeId?.let { repository.dao.document(it) }
        if (existing != null) return existing
        return Document(title = "Scan ${java.text.SimpleDateFormat("dd MMM HH.mm", java.util.Locale.getDefault()).format(java.util.Date())}").also { repository.dao.insert(it); openDocument(it.id) }
    }
    fun import(uris: List<Uri>) = work {
        if (uris.isEmpty()) return@work
        val document = destination()
        val start = repository.dao.nextPosition(document.id)
        uris.forEachIndexed { index, uri -> repository.import(uri, document, start + index) }
        screen = "document"
    }
    fun captured(file: File) = work {
        val document = destination()
        val position = repository.dao.nextPosition(document.id)
        val page = Page(documentId = document.id, original = file.name, position = position)
        repository.dao.insert(page)
        val crop = withContext(Dispatchers.Default) {
            val preview = ImageProcessor.decode(file, 640)
            try { DocumentDetector.detect(preview) ?: Crop.Full } finally { preview.recycle() }
        }
        repository.dao.update(page.copy(crop = crop.encode()))
    }
    fun update(page: Page) = work { repository.dao.update(page) }
    fun export(docs: List<DocumentPages>, format: Format, quality: Quality, name: String, merge: Boolean, paper: PaperSize = PaperSize.Auto, archive: Format = Format.PDF) = work {
        exports = Exporter(repository).generate(docs, format, quality, name, merge, paper, archive)
    }
}
