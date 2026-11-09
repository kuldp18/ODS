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

@Composable fun PageImage(vm: ScannerViewModel, page: Page, modifier: Modifier = Modifier) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(page) {
        delay(120)
        failure = null
        try { bitmap = withContext(Dispatchers.Default) { ImageProcessor.render(vm.repository.file(page), page, 1200) } }
        catch (e: CancellationException) { throw e }
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
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row { TextButton(onClick = onBack) { Text("Back") }; Text("Enhance", style = MaterialTheme.typography.headlineMedium) }
        PageImage(vm, if (compare) recipe.copy(filter = Filter.Original.name, contrast = 1f, brightness = 0f) else recipe, Modifier.weight(1f).fillMaxWidth())
        TextButton(onClick = { compare = !compare }) { Text(if (compare) "Show edited" else "Compare original") }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Filter.entries.forEach { filter -> FilterChip(selected = recipe.filter == filter.name, onClick = { recipe = recipe.copy(filter = filter.name) }, label = { Text(when(filter) { Filter.BW -> "B&W"; Filter.LowLight -> "Low light"; else -> filter.name }) }) }
        }
        Text("Contrast ${"%.2f".format(recipe.contrast)}")
        Slider(value = recipe.contrast, onValueChange = { recipe = recipe.copy(contrast = it) }, valueRange = .5f..1.5f)
        Text("Brightness ${recipe.brightness.toInt()}")
        Slider(value = recipe.brightness, onValueChange = { recipe = recipe.copy(brightness = it) }, valueRange = -64f..64f)
        if (recipe.filter == Filter.BW.name) {
            Text("B&W threshold ${recipe.threshold.toInt()}")
            Slider(value = recipe.threshold, onValueChange = { recipe = recipe.copy(threshold = it) }, valueRange = -10f..30f)
        }
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(all, { all = it }); Text("Apply filter to all ${pages.size} pages") }
        TextButton(onClick = { recipe = page.copy(filter = Filter.Original.name, contrast = 1f, brightness = 0f, threshold = 12f) }) { Text("Reset adjustments") }
        Button(enabled = !vm.busy, modifier = Modifier.fillMaxWidth(), onClick = {
            vm.work { if (all) pages.forEach { vm.repository.dao.update(it.copy(filter = recipe.filter, contrast = recipe.contrast, brightness = recipe.brightness, threshold = recipe.threshold)) } else vm.repository.dao.update(recipe) }
            onBack()
        }) { Text("Apply") }
    }
}

