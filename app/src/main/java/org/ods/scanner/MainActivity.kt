package org.ods.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val vm: ScannerViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) accept(intent)
        setContent { ScannerApp(vm) }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); accept(intent) }
    @Suppress("DEPRECATION") private fun accept(intent: Intent) {
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
            Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            else -> emptyList()
        }
        if (uris.isNotEmpty()) { vm.openDocument(null); vm.import(uris) }
    }
}

private val Dark = darkColorScheme(primary = Color(0xFFD4F77D), onPrimary = Color(0xFF172017), background = Color(0xFF101315), surface = Color(0xFF1B2023), onBackground = Color(0xFFF4F6EF), onSurface = Color(0xFFF4F6EF))
private val Light = lightColorScheme(primary = Color(0xFF365B0A), background = Color(0xFFF7F8F2), surface = Color.White)

@OptIn(ExperimentalFoundationApi::class)
@Composable fun ScannerApp(vm: ScannerViewModel) {
    val documents by vm.documents.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(setOf<String>()) }
    var query by remember { mutableStateOf("") }
    var pageIndex by remember(vm.activeId) { mutableIntStateOf(0) }
    var edit by remember { mutableStateOf<Page?>(null) }
    var exportIds by remember { mutableStateOf(setOf<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val theme by vm.theme.collectAsStateWithLifecycle()
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.import(it) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.screen = "camera" else vm.error = "Camera permission is needed to scan. You can import images, or enable Camera in Android app settings."
    }
    fun scan() { if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) vm.screen = "camera" else permission.launch(Manifest.permission.CAMERA) }
    val document = documents.firstOrNull { it.document.id == vm.activeId }
    val pages = document?.ordered().orEmpty()
    val page = pages.getOrNull(pageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0)))
    MaterialTheme(colorScheme = if (theme == "Dark" || (theme == "System" && isSystemInDarkTheme())) Dark else Light, shapes = Shapes(medium = RoundedCornerShape(16.dp))) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding()) {
                if (vm.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); TextButton(onClick = vm::cancel) { Text("Cancel operation") } }
                vm.error?.let { message -> AlertDialog(onDismissRequest = { vm.error = null }, title = { Text("Could not complete") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { vm.error = null }) { Text("OK") } }) }
                BackHandler(vm.screen != "library") { edit = null; vm.screen = "library" }
                when (vm.screen) {
                    "camera" -> CameraScreen(vm)
                    "crop" -> edit?.let { CropScreen(vm, it, onApply = { crop -> vm.update(it.copy(crop = crop.encode())); edit = null; vm.screen = "document" }, onBack = { edit = null; vm.screen = "document" }) }
                    "enhance" -> page?.let { EnhanceScreen(vm, it, pages) { vm.screen = "document" } }
                    "export" -> ExportScreen(vm, documents.filter { it.document.id in exportIds }) { vm.screen = "library" }
                    "settings" -> Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        TextButton(onClick = { vm.screen = "library" }) { Text("Back") }; Text("Settings", style = MaterialTheme.typography.headlineLarge)
                        Text("Appearance")
                        Row { listOf("System", "Light", "Dark").forEach { value -> FilterChip(selected = theme == value, onClick = { vm.setTheme(value) }, label = { Text(value) }) } }
                        Text("Saved on this device", style = MaterialTheme.typography.titleMedium)
                        Text("Originals are always retained. Uninstalling ODS removes your local documents. Export important documents before uninstalling.")
                        Text("ODS 0.1.0 â€¢ Apache 2.0\nNo accounts, ads, analytics or internet permission.\nCameraX, AndroidX and OpenCV power scanning.")
                    }
                    "document" -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row { TextButton(onClick = { vm.screen = "library" }) { Text("Back") }; TextButton(onClick = { rename = true }, enabled = document != null) { Text("Rename") } }
                        Text(document?.document?.title ?: "New scan", style = MaterialTheme.typography.headlineMedium)
                        Text("${pages.size} pages â€¢ Saved locally")
                        if (page != null) PageImage(vm, page, Modifier.weight(1f).fillMaxWidth()) else Text("Capture or import your first page", Modifier.weight(1f))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { pages.forEachIndexed { index, _ -> FilterChip(selected = pageIndex == index, onClick = { pageIndex = index }, label = { Text("Page ${index + 1}") }) } }
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            TextButton(enabled = page != null, onClick = { edit = page; vm.screen = "crop" }) { Text("Crop") }
                            TextButton(enabled = page != null, onClick = { vm.screen = "enhance" }) { Text("Enhance") }
                            TextButton(enabled = page != null, onClick = { page?.let { vm.update(it.copy(rotation = (it.rotation + 90) % 360)) } }) { Text("Rotate") }
                            TextButton(enabled = pageIndex > 0, onClick = { val next = pages.toMutableList(); java.util.Collections.swap(next, pageIndex, pageIndex - 1); vm.work { vm.repository.dao.reorder(next) }; pageIndex-- }) { Text("Move up") }
                        }
                        Row { OutlinedButton(onClick = { scan() }) { Text("Add scan") }; TextButton(onClick = { importer.launch(arrayOf("image/*")) }) { Text("Import") } }
                        Button(enabled = pages.isNotEmpty() && !vm.busy, modifier = Modifier.fillMaxWidth(), onClick = { exportIds = setOfNotNull(vm.activeId); vm.exports = emptyList(); vm.screen = "export" }) { Text("Share / save") }
                    }
                    else -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("ODS", style = MaterialTheme.typography.titleLarge); TextButton(onClick = { vm.screen = "settings" }) { Text("Settings") } }
                        Text("Documents", style = MaterialTheme.typography.headlineLarge)
                        OutlinedTextField(value = query, onValueChange = { query = it }, placeholder = { Text("Search documents") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        if (selected.isNotEmpty()) Row { TextButton(onClick = { selected = documents.map { it.document.id }.toSet() }) { Text("Select all") }; TextButton(onClick = { selected = emptySet() }) { Text("Clear (${selected.size})") }; TextButton(onClick = { confirmDelete = true }) { Text("Delete") } }
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (documents.isEmpty()) item { Text("Your paperwork, in one place", style = MaterialTheme.typography.titleLarge); Text("Scan a document or import images to get started. Everything stays on this device.", Modifier.padding(top = 12.dp)) }
                            items(documents.filter { it.document.title.contains(query, ignoreCase = true) }, key = { it.document.id }) { entry ->
                                Card(Modifier.fillMaxWidth().combinedClickable(onClick = { if (selected.isEmpty()) { vm.openDocument(entry.document.id); pageIndex = 0; vm.screen = "document" } else selected = if (entry.document.id in selected) selected - entry.document.id else selected + entry.document.id }, onLongClick = { selected = selected + entry.document.id })) {
                                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        if (selected.isNotEmpty()) Checkbox(checked = entry.document.id in selected, onCheckedChange = { checked -> selected = if (checked) selected + entry.document.id else selected - entry.document.id })
                                        Column { Text(entry.document.title, style = MaterialTheme.typography.titleMedium); Text("${entry.pages.size} pages â€¢ Saved locally") }
                                    }
                                }
                            }
                        }
                        if (selected.isNotEmpty()) Button(modifier = Modifier.fillMaxWidth(), enabled = !vm.busy, onClick = { exportIds = selected; vm.exports = emptyList(); vm.screen = "export" }) { Text("Share ${selected.size} documents") }
                        else {
                            OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = !vm.busy, onClick = { vm.openDocument(null); importer.launch(arrayOf("image/*")) }) { Text("Import images") }
                            Button(modifier = Modifier.fillMaxWidth(), enabled = !vm.busy, onClick = { vm.openDocument(null); scan() }) { Text("Scan document") }
                        }
                        Text("Saved on this device", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete ${selected.size} documents?") }, text = { Text("Documents will be removed from the library. Original files remain retained for now.") }, confirmButton = { TextButton(onClick = { val ids = selected; vm.work { ids.forEach { vm.repository.dao.delete(it) } }; selected = emptySet(); confirmDelete = false }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
                if (rename && document != null) {
                    var title by remember(document.document.id) { mutableStateOf(document.document.title) }
                    AlertDialog(onDismissRequest = { rename = false }, title = { Text("Rename document") }, text = { OutlinedTextField(title, { title = it.take(120) }, label = { Text("Name") }) }, confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { vm.work { vm.repository.dao.update(document.document.copy(title = title.trim())) }; rename = false }) { Text("Save") } })
                }
            }
        }
    }
}

