package org.ods.scanner

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import android.graphics.Bitmap
import kotlin.math.hypot

@Composable fun PageImage(vm: ScannerViewModel, page: Page, modifier: Modifier = Modifier, maxEdge: Int = 1200) {
    var bitmap by remember(page.id, page.original) { mutableStateOf<Bitmap?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(page, maxEdge) {
        delay(120)
        failure = null
        try { bitmap = withContext(Dispatchers.Default) { PreviewCache.get(vm.repository, page, maxEdge) } }
        catch (e: CancellationException) { throw e }
        catch (e: OutOfMemoryError) { failure = "Not enough memory for a preview. Close and reopen this document." }
        catch (e: LinkageError) { failure = "This device cannot load the image processing engine." }
        catch (e: Exception) { failure = e.message }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), "Document page preview", Modifier.fillMaxSize()) }
        if (bitmap == null && failure == null) CircularProgressIndicator()
        failure?.let { Text("Preview unavailable: $it") }
    }
}

@Composable fun EnhanceScreen(vm: ScannerViewModel, page: Page, pages: List<Page>, onBack: () -> Unit) {
    var recipe by remember(page.id) { mutableStateOf(page) }
    var all by remember { mutableStateOf(false) }
    var compare by remember { mutableStateOf(false) }
    var adjustment by remember { mutableStateOf("Contrast") }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Enhance", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { recipe = page }) { Text("Reset") }
        }
        PageImage(vm, if (compare) recipe.copy(filter = Filter.Original.name, contrast = 1f, brightness = 0f) else recipe, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 4.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { compare = !compare }) { Text(if (compare) "Show edited" else "Compare original") }
            Spacer(Modifier.weight(1f))
            if (pages.size > 1) { Checkbox(all, { all = it }); Text("All pages", style = MaterialTheme.typography.bodySmall) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Filter.entries.forEach { filter ->
                Column(Modifier.width(60.dp).clickable { recipe = recipe.copy(filter = filter.name) }.border(if (recipe.filter == filter.name) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    PageImage(vm, recipe.copy(filter = filter.name), Modifier.height(52.dp).fillMaxWidth(), maxEdge = 120)
                    Text(when(filter) { Filter.BW -> "B&W"; Filter.LowLight -> "Low light"; else -> filter.name }, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf("Contrast", "Brightness") + if (recipe.filter == Filter.BW.name) listOf("Threshold") else emptyList()).forEach { value ->
                FilterChip(selected = adjustment == value, onClick = { adjustment = value }, label = { Text(value) })
            }
        }
        val mode = if (adjustment == "Threshold" && recipe.filter != Filter.BW.name) "Contrast" else adjustment
        Slider(modifier = Modifier.padding(horizontal = 16.dp), value = when(mode) { "Brightness" -> recipe.brightness; "Threshold" -> recipe.threshold; else -> recipe.contrast },
            valueRange = when(mode) { "Brightness" -> -64f..64f; "Threshold" -> -10f..30f; else -> .5f..1.5f },
            onValueChange = { recipe = when(mode) { "Brightness" -> recipe.copy(brightness = it); "Threshold" -> recipe.copy(threshold = it); else -> recipe.copy(contrast = it) } })
        Button(enabled = !vm.busy, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).heightIn(min = 52.dp), onClick = {
            vm.work {
                if (all) pages.forEach { vm.repository.dao.update(it.copy(filter = recipe.filter, contrast = recipe.contrast, brightness = recipe.brightness, threshold = recipe.threshold)) }
                else vm.repository.dao.update(recipe)
                onBack()
            }
        }) { Text(if (all && pages.size > 1) "Apply to all ${pages.size} pages" else "Apply changes") }
    }
}
