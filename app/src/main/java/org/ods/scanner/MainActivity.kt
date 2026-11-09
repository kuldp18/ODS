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
import androidx.activity.enableEdgeToEdge
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

class MainActivity : ComponentActivity() {
    private val vm: ScannerViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable fun ScannerApp(vm: ScannerViewModel) {
    val documents by vm.documents.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(setOf<String>()) }
    var query by remember { mutableStateOf("") }
    var pageIndex by remember(vm.activeId) { mutableIntStateOf(0) }
    var edit by remember { mutableStateOf<Page?>(null) }
    var exportIds by remember { mutableStateOf(setOf<String>()) }
    var exportReturnScreen by remember { mutableStateOf("library") }
    var confirmDelete by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var showPages by remember { mutableStateOf(false) }
    var selectedPages by remember(vm.activeId) { mutableStateOf(setOf<String>()) }
    var exportPages by remember { mutableStateOf(setOf<String>()) }
    var sortByName by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val theme by vm.theme.collectAsStateWithLifecycle()
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.import(it) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.screen = "camera" else vm.error = "Camera permission is needed to scan. You can import images, or enable Camera in Android app settings."
    }
    fun scan() { if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) vm.screen = "camera" else permission.launch(Manifest.permission.CAMERA) }
    val document = documents.firstOrNull { it.document.id == vm.activeId }
    val pages = document?.ordered().orEmpty()
    LaunchedEffect(vm.reviewPageId, pages) {
        val index = pages.indexOfFirst { it.id == vm.reviewPageId }
        if (index >= 0) { pageIndex = index; vm.reviewedPage() }
    }
    val page = pages.getOrNull(pageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0)))
    val dark = theme == "Dark" || (theme == "System" && isSystemInDarkTheme())
    SideEffect {
        (context as? android.app.Activity)?.let { activity ->
            val controller = androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            controller.isAppearanceLightStatusBars = !dark && vm.screen != "camera"
            controller.isAppearanceLightNavigationBars = !dark && vm.screen != "camera"
        }
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, shapes = Shapes(medium = RoundedCornerShape(16.dp))) {
        Surface(Modifier.fillMaxSize()) {
            Column(if (vm.screen == "camera") Modifier.fillMaxSize() else Modifier.fillMaxSize().safeDrawingPadding()) {
                if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                vm.error?.let { message -> AlertDialog(onDismissRequest = { vm.error = null }, title = { Text("Could not complete") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { vm.error = null }) { Text("OK") } }) }
                BackHandler(vm.screen != "library") {
                    val destination = when(vm.screen) { "crop", "enhance" -> "document"; "export" -> exportReturnScreen; "camera" -> if (document != null) "document" else "library"; else -> "library" }
                    edit = null; vm.screen = destination
                }
                when (vm.screen) {
                    "camera" -> CameraScreen(vm)
                    "crop" -> edit?.let { CropScreen(vm, it, onApply = { crop, rotation, straighten -> vm.update(it.copy(crop = crop.encode(), rotation = rotation, straighten = straighten)); edit = null; vm.screen = "document" }, onBack = { edit = null; vm.screen = "document" }) }
                    "enhance" -> page?.let { EnhanceScreen(vm, it, pages) { vm.screen = "document" } }
                    "export" -> ExportScreen(vm, documents.filter { it.document.id in exportIds }.map { entry -> if (exportPages.isEmpty()) entry else entry.copy(pages = entry.pages.filter { it.id in exportPages }) }) { vm.screen = exportReturnScreen }
                    "settings" -> Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = { vm.screen = "library" }) { Text("Back") }; Text("Settings", style = MaterialTheme.typography.headlineLarge)
                        Text("Appearance")
                        Row { listOf("System", "Light", "Dark").forEach { value -> FilterChip(selected = theme == value, onClick = { vm.setTheme(value) }, label = { Text(value) }) } }
                        Text("Saved on this device", style = MaterialTheme.typography.titleMedium)
                        Text("Originals are always retained. Uninstalling ODS removes your local documents. Export important documents before uninstalling.")
                        Text("ODS 0.1.0 • Apache 2.0\nNo accounts, ads, analytics or internet permission.\nCameraX, AndroidX and OpenCV power scanning.")
                    }
                    "document" -> ReviewScreen(vm, document, pageIndex, selectedPages.size,
                        onPage = { pageIndex = it }, onBack = { vm.screen = "library" }, onRename = { rename = true },
                        onCrop = { edit = page; vm.screen = "crop" }, onEnhance = { vm.screen = "enhance" },
                        onPages = { showPages = true }, onScan = { vm.prepareCapture(); scan() },
                        onRetake = { page?.let { vm.prepareCapture(it.id); scan() } },
                        onImport = { importer.launch(arrayOf("image/*")) },
                        onShare = { exportReturnScreen = "document"; exportIds = setOfNotNull(vm.activeId); exportPages = selectedPages.intersect(pages.map { it.id }.toSet()); vm.exports = emptyList(); vm.screen = "export" })
                    else -> Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Column { Text("ODS", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium); Text("Documents", style = MaterialTheme.typography.headlineSmall) }; TextButton(onClick = { vm.screen = "settings" }) { Text("Settings") } }
                        OutlinedTextField(value = query, onValueChange = { query = it }, placeholder = { Text("Search documents") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Text("${documents.size} documents", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); TextButton(onClick = { sortByName = !sortByName }) { Text(if (sortByName) "Name" else "Recent") } }
                        if (vm.deleted.isNotEmpty()) Row { Text("${vm.deleted.size} deleted"); TextButton(onClick = vm::undoDelete, enabled = !vm.busy) { Text("Undo") } }
                        if (selected.isNotEmpty()) Row { TextButton(onClick = { selected = documents.map { it.document.id }.toSet() }) { Text("Select all") }; TextButton(onClick = { selected = emptySet() }) { Text("Clear (${selected.size})") }; TextButton(onClick = { confirmDelete = true }) { Text("Delete") } }
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (query.isNotBlank() && documents.none { it.document.title.contains(query, true) }) item { Text("No documents match your search") }
                            if (documents.isEmpty()) item { Text("Your paperwork, in one place", style = MaterialTheme.typography.titleLarge); Text("Scan a document or import images to get started. Everything stays on this device.", Modifier.padding(top = 12.dp)) }
                            items(documents.filter { it.document.title.contains(query, ignoreCase = true) }.let { if (sortByName) it.sortedBy { entry -> entry.document.title.lowercase() } else it }, key = { it.document.id }) { entry ->
                                Card(Modifier.fillMaxWidth().combinedClickable(onClick = { if (selected.isEmpty()) { vm.openDocument(entry.document.id); pageIndex = 0; vm.screen = "document" } else selected = if (entry.document.id in selected) selected - entry.document.id else selected + entry.document.id }, onLongClick = { selected = selected + entry.document.id })) {
                                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        entry.ordered().firstOrNull()?.let { PageImage(vm, it, Modifier.size(48.dp, 64.dp), maxEdge = 180) }
                                        if (selected.isNotEmpty()) Checkbox(checked = entry.document.id in selected, onCheckedChange = { checked -> selected = if (checked) selected + entry.document.id else selected - entry.document.id })
                                        Column { Text(entry.document.title, style = MaterialTheme.typography.titleMedium); Text("${entry.pages.size} pages • Saved locally") }
                                    }
                                }
                            }
                        }
                        if (selected.isNotEmpty()) {
                            if (selected.size > 1) OutlinedButton(onClick = { vm.merge(selected); selected = emptySet() }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("Merge selected documents") }
                            Button(modifier = Modifier.fillMaxWidth(), enabled = !vm.busy, onClick = { exportReturnScreen = "library"; exportIds = selected; exportPages = emptySet(); vm.exports = emptyList(); vm.screen = "export" }) { Text("Share ${selected.size} documents") }
                        }
                        else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(modifier = Modifier.weight(1f).heightIn(min = 52.dp), enabled = !vm.busy, onClick = { vm.openDocument(null); importer.launch(arrayOf("image/*")) }) { Text("Import") }
                            Button(modifier = Modifier.weight(2f).heightIn(min = 52.dp), enabled = !vm.busy, onClick = { vm.openDocument(null); scan() }) { Text("Scan document") }
                        }
                    }
                }
                if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete ${selected.size} documents?") }, text = { Text("Remove from your library? You can undo this deletion until the app closes or another deletion replaces it.") }, confirmButton = { TextButton(onClick = { vm.delete(selected); selected = emptySet(); confirmDelete = false }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
                if (showPages) ModalBottomSheet(onDismissRequest = { showPages = false }) {
                    var deleting by remember { mutableStateOf<Page?>(null) }
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text("Pages", style = MaterialTheme.typography.headlineSmall)
                        Text("Select pages to share, or change their order.")
                        TextButton(onClick = { selectedPages = emptySet() }) { Text("Share all pages") }
                        LazyColumn(Modifier.heightIn(max = 420.dp)) {
                            items(pages, key = { it.id }) { item ->
                                val index = pages.indexOf(item)
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Checkbox(item.id in selectedPages, { checked -> selectedPages = if (checked) selectedPages + item.id else selectedPages - item.id })
                                    Text("${index + 1}", Modifier.weight(1f))
                                    TextButton(enabled = index > 0 && !vm.busy, onClick = { val ordered = pages.toMutableList(); java.util.Collections.swap(ordered,index,index-1); vm.work { vm.repository.dao.reorder(ordered) }; pageIndex = index-1 }) { Text("Up") }
                                    TextButton(enabled = index < pages.lastIndex && !vm.busy, onClick = { val ordered = pages.toMutableList(); java.util.Collections.swap(ordered,index,index+1); vm.work { vm.repository.dao.reorder(ordered) }; pageIndex = index+1 }) { Text("Down") }
                                    TextButton(enabled = !vm.busy, onClick = { deleting = item }) { Text("Delete") }
                                }
                            }
                        }
                        Button(onClick = { showPages = false }, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) { Text("Done") }
                    }
                    deleting?.let { item -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete page?") }, text = { Text("This removes the page from this document.") }, confirmButton = { TextButton(onClick = { vm.work { vm.repository.dao.deletePage(item.id) }; selectedPages = selectedPages - item.id; deleting = null }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }) }
                }
                if (rename && document != null) {
                    var title by remember(document.document.id) { mutableStateOf(document.document.title) }
                    AlertDialog(onDismissRequest = { rename = false }, title = { Text("Rename document") }, text = { OutlinedTextField(title, { title = it.take(120) }, label = { Text("Name") }) }, confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { vm.work { vm.repository.dao.update(document.document.copy(title = title.trim())) }; rename = false }) { Text("Save") } })
                }
            }
        }
    }
}

