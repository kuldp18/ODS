package org.ods.scanner

import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun ExportScreen(vm: ScannerViewModel, documents: List<DocumentPages>, onBack: () -> Unit) {
    var format by remember { mutableStateOf(Format.PDF) }
    var quality by remember { mutableStateOf(Quality.Balanced) }
    var merge by remember { mutableStateOf(false) }
    var paper by remember { mutableStateOf(PaperSize.Auto) }
    var archive by remember { mutableStateOf(Format.PDF) }
    var advanced by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(documents.singleOrNull()?.document?.title ?: "Documents") }
    val context = LocalContext.current
    val saveSingle = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(format.mime)) { uri ->
        val file = vm.exports.singleOrNull()
        if (uri != null && file != null) vm.work { withContext(Dispatchers.IO) {
            try { requireNotNull(context.contentResolver.openOutputStream(uri)).use { output -> file.inputStream().use { it.copyTo(output) } } }
            catch (e: Exception) { runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }; throw e }
        } }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) vm.work {
            withContext(Dispatchers.IO) {
                val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                for (file in vm.exports) {
                    val uri = requireNotNull(DocumentsContract.createDocument(context.contentResolver, root, format.mime, file.name)) { "Cannot create destination file" }
                    try { requireNotNull(context.contentResolver.openOutputStream(uri)).use { output -> file.inputStream().use { it.copyTo(output) } } }
                    catch (e: Exception) { runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }; throw e }
                }
            }
        }
    }
    fun generate(onReady: () -> Unit) {
        if (vm.exports.isNotEmpty()) runCatching(onReady).onFailure { vm.error = it.message }
        else vm.export(documents, format, quality, name, merge, paper, archive, onReady)
    }
    fun saveFiles() { if (vm.exports.size == 1) saveSingle.launch(vm.exports.single().name) else save.launch(null) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Share document", style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.weight(1f).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${documents.size} documents • ${documents.sumOf { it.pages.size }} pages")
        Text("Format")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Format.entries.forEach { value -> FilterChip(selected = format == value, enabled = !vm.busy, onClick = { format = value; vm.exports = emptyList() }, label = { Text(value.name) }) } }
        if (documents.size > 1 && (format == Format.PDF || format == Format.ZIP)) {
            Row { Checkbox(merge, { merge = it; vm.exports = emptyList() }, enabled = !vm.busy); Text("Merge documents into one PDF") }
        }
        if (format == Format.ZIP) {
            Text("Archive contents")
            Row { listOf(Format.PDF,Format.JPEG,Format.PNG).forEach { value -> FilterChip(selected = archive == value, enabled = !vm.busy, onClick = { archive = value; vm.exports = emptyList() }, label = { Text(value.name) }) } }
        }
        OutlinedTextField(value = name, onValueChange = { name = it; vm.exports = emptyList() }, label = { Text("Filename") }, enabled = !vm.busy, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Quality")
        Row { Quality.entries.forEach { value -> FilterChip(selected = quality == value, enabled = !vm.busy, onClick = { quality = value; vm.exports = emptyList() }, label = { Text(value.name) }) } }
        TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Hide advanced options" else "Advanced options") }
        if (advanced && (format == Format.PDF || (format == Format.ZIP && archive == Format.PDF))) {
            Text("Page size")
            Row { PaperSize.entries.forEach { value -> FilterChip(selected = paper == value, enabled = !vm.busy, onClick = { paper = value; vm.exports = emptyList() }, label = { Text(value.name) }) } }
        }
        if (vm.exports.isNotEmpty()) {
            Text("${vm.exports.size} files • ${"%.2f".format(vm.exports.sumOf { it.length() } / 1048576.0)} MB")
        }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (vm.busy) TextButton(onClick = vm::cancel) { Text("Cancel") }
            OutlinedButton(enabled = !vm.busy && documents.any { it.pages.isNotEmpty() }, onClick = { generate { saveFiles() } }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Save to device") }
            Button(enabled = !vm.busy && documents.any { it.pages.isNotEmpty() }, onClick = { generate { Exporter(vm.repository).share(vm.exports, format) } }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (vm.busy) "Preparing…" else "Share") }
        }
    }
}
