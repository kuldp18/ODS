package org.ods.scanner

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable fun ReviewScreen(
    vm: ScannerViewModel, document: DocumentPages?, pageIndex: Int, selectedCount: Int,
    onPage: (Int) -> Unit, onBack: () -> Unit, onRename: () -> Unit,
    onCrop: () -> Unit, onEnhance: () -> Unit, onPages: () -> Unit,
    onScan: () -> Unit, onRetake: () -> Unit, onImport: () -> Unit, onShare: () -> Unit
) {
    val pages = document?.ordered().orEmpty()
    val index = pageIndex.coerceIn(0, (pages.size-1).coerceAtLeast(0))
    val page = pages.getOrNull(index)
    var more by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Column(Modifier.weight(1f).clickable(enabled = document != null && !vm.busy, onClick = onRename).padding(vertical = 8.dp)) {
                Text(document?.document?.title ?: "New scan", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (vm.busy) "Finishing page…" else "Saved on this device", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                TextButton(enabled = !vm.busy, onClick = { more = true }) { Text("More") }
                DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                    DropdownMenuItem(text = { Text("Rename document") }, onClick = { more = false; onRename() })
                    DropdownMenuItem(text = { Text("Import pages") }, onClick = { more = false; onImport() })
                    DropdownMenuItem(text = { Text("Retake this page") }, enabled = page != null, onClick = { more = false; onRetake() })
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .25f)), contentAlignment = Alignment.Center) {
            if (page != null) PageImage(vm,page,Modifier.fillMaxSize().padding(4.dp))
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (vm.busy) { CircularProgressIndicator(); Text("Saving your scan", Modifier.padding(top = 12.dp)) }
                else { Text("No pages yet"); TextButton(onClick = onScan) { Text("Scan a page") } }
            }
            if (pages.isNotEmpty()) Surface(Modifier.align(Alignment.BottomCenter).padding(8.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .92f)) {
                Text("${index+1} / ${pages.size}", Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
            }
        }
        if (pages.size > 1) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pages.forEachIndexed { i, item ->
                Box(Modifier.size(40.dp,52.dp).clip(RoundedCornerShape(4.dp)).border(if (i == index) 2.dp else 1.dp, if (i == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp)).clickable { onPage(i) }) {
                    PageImage(vm,item,Modifier.fillMaxSize().padding(2.dp),maxEdge = 120)
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(enabled = page != null && !vm.busy, onClick = onCrop) { Text("Crop") }
            TextButton(enabled = page != null && !vm.busy, onClick = onEnhance) { Text("Enhance") }
            TextButton(enabled = page != null && !vm.busy, onClick = { page?.let { vm.update(it.copy(rotation = (it.rotation+90)%360)) } }) { Text("Rotate") }
            TextButton(enabled = pages.isNotEmpty() && !vm.busy, onClick = onPages) { Text("Pages") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !vm.busy, onClick = onScan, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Scan another page") }
            OutlinedButton(enabled = page != null && !vm.busy, onClick = onRetake, modifier = Modifier.heightIn(min = 48.dp)) { Text("Retake") }
        }
        Button(enabled = pages.isNotEmpty() && !vm.busy, onClick = onShare, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).heightIn(min = 52.dp)) {
            Text(if (selectedCount > 0) "Share $selectedCount selected pages" else "Share document")
        }
    }
}
