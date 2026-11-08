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

@Composable fun CropScreen(vm: ScannerViewModel, page: Page, onApply: (Crop) -> Unit, onBack: () -> Unit) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var crop by remember(page.id) { mutableStateOf(Crop.decode(page.crop)) }
    val history = remember { mutableStateListOf<Crop>() }
    val future = remember { mutableStateListOf<Crop>() }
    LaunchedEffect(page.original) {
        try { bitmap = withContext(Dispatchers.IO) { ImageProcessor.decode(vm.repository.file(page), 1600) } }
        catch (e: Exception) { vm.error = e.message }
    }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row { TextButton(onClick = onBack) { Text("Back") }; Text("Adjust crop", style = MaterialTheme.typography.headlineSmall) }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            bitmap?.let { image ->
                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val ratio = image.width.toFloat() / image.height
                    val width = minOf(maxWidth, maxHeight * ratio)
                    Box(Modifier.width(width).aspectRatio(ratio)) {
                        Image(image.asImageBitmap(), "Original document with draggable crop corners", Modifier.fillMaxSize())
                        Canvas(Modifier.fillMaxSize().pointerInput(image) {
                            var handle = -1
                            detectDragGestures(onDragStart = { touch ->
                                handle = crop.points.indices.minByOrNull { index -> val p = crop.points[index]; hypot(touch.x - p.x * size.width, touch.y - p.y * size.height) } ?: -1
                                if (handle >= 0) {
                                    val p = crop.points[handle]
                                    if (hypot(touch.x - p.x * size.width, touch.y - p.y * size.height) > 48.dp.toPx()) handle = -1
                                    else { history.add(crop); future.clear() }
                                }
                            }, onDragEnd = { handle = -1 }, onDragCancel = { handle = -1 }) { change, delta ->
                                if (handle >= 0) {
                                    change.consume()
                                    val points = crop.points.toMutableList(); val old = points[handle]
                                    points[handle] = Corner((old.x + delta.x / size.width).coerceIn(0f, 1f), (old.y + delta.y / size.height).coerceIn(0f, 1f))
                                    val candidate = Crop(points); if (candidate.valid()) crop = candidate
                                }
                            }
                        }) {
                            val points = crop.points.map { Offset(it.x * size.width, it.y * size.height) }
                            points.indices.forEach { index -> drawLine(Color(0xFFD4F77D), points[index], points[(index + 1) % 4], 2.dp.toPx()); drawCircle(Color(0xFFD4F77D), 9.dp.toPx(), points[index]) }
                        }
                    }
                }
            } ?: CircularProgressIndicator()
        }
        Text("Drag the four corners to fit the page. Edits preserve the original.")
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            TextButton(onClick = { history.add(crop); future.clear(); crop = Crop.Full }) { Text("Full image") }
            TextButton(enabled = history.isNotEmpty(), onClick = { future.add(crop); crop = history.removeAt(history.lastIndex) }) { Text("Undo") }
            TextButton(enabled = future.isNotEmpty(), onClick = { history.add(crop); crop = future.removeAt(future.lastIndex) }) { Text("Redo") }
        }
        Button(enabled = bitmap != null && !vm.busy, onClick = { onApply(crop) }, modifier = Modifier.fillMaxWidth()) { Text("Apply crop") }
    }
}

@Composable fun EnhanceScreen(vm: ScannerViewModel, page: Page, pages: List<Page>, onBack: () -> Unit) {
    var recipe by remember(page.id) { mutableStateOf(page) }
    var all by remember { mutableStateOf(false) }
    var compare by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row { TextButton(onClick = onBack) { Text("Back") }; Text("Enhance", style = MaterialTheme.typography.headlineMedium) }
        PageImage(vm, if (compare) page.copy(crop = Crop.Full.encode(), filter = Filter.Original.name, contrast = 1f, rotation = 0) else recipe, Modifier.weight(1f).fillMaxWidth())
        TextButton(onClick = { compare = !compare }) { Text(if (compare) "Show edited" else "Compare original") }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Filter.entries.forEach { filter -> FilterChip(selected = recipe.filter == filter.name, onClick = { recipe = recipe.copy(filter = filter.name) }, label = { Text(if (filter == Filter.BW) "B&W" else filter.name) }) }
        }
        Text("Contrast ${"%.2f".format(recipe.contrast)}")
        Slider(value = recipe.contrast, onValueChange = { recipe = recipe.copy(contrast = it) }, valueRange = .5f..1.5f)
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(all, { all = it }); Text("Apply filter to all ${pages.size} pages") }
        TextButton(onClick = { recipe = page.copy(filter = Filter.Original.name, contrast = 1f) }) { Text("Reset adjustments") }
        Button(enabled = !vm.busy, modifier = Modifier.fillMaxWidth(), onClick = {
            vm.work { if (all) pages.forEach { vm.repository.dao.update(it.copy(filter = recipe.filter, contrast = recipe.contrast)) } else vm.repository.dao.update(recipe) }
            onBack()
        }) { Text("Apply") }
    }
}
