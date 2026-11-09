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
    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Share documents", style = MaterialTheme.typography.headlineLarge)
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
        if (format == Format.PDF || (format == Format.ZIP && archive == Format.PDF)) {
            Text("Page size")
            Row { PaperSize.entries.forEach { value -> FilterChip(selected = paper == value, enabled = !vm.busy, onClick = { paper = value; vm.exports = emptyList() }, label = { Text(value.name) }) } }
        }
        if (vm.exports.isEmpty()) {
            Button(enabled = !vm.busy && documents.any { it.pages.isNotEmpty() }, modifier = Modifier.fillMaxWidth(), onClick = { vm.export(documents, format, quality, name, merge, paper, archive) }) { Text("Generate export") }
            Text("Final file size appears after generation.")
        } else {
            Text("${vm.exports.size} files • ${"%.2f".format(vm.exports.sumOf { it.length() } / 1048576.0)} MB")
            Button(enabled = !vm.busy, modifier = Modifier.fillMaxWidth(), onClick = { runCatching { Exporter(vm.repository).share(vm.exports, format) }.onFailure { vm.error = it.message } }) { Text("Continue to share") }
            OutlinedButton(enabled = !vm.busy, modifier = Modifier.fillMaxWidth(), onClick = { if (vm.exports.size == 1) saveSingle.launch(vm.exports.single().name) else save.launch(null) }) { Text("Save to device") }
            Text("If a receiving app cannot accept several files, use a merged PDF or ZIP.")
        }
    }
}
