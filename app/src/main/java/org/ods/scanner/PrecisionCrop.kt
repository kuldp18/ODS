package org.ods.scanner

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*
import kotlin.math.*

@Composable fun CropScreen(vm: ScannerViewModel, page: Page, onApply: (Crop) -> Unit, onBack: () -> Unit) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var crop by remember(page.id) { mutableStateOf(Crop.decode(page.crop)) }
    var selected by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var zoomMode by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var detecting by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf("Drag corners or edge midpoints to fit the page") }
    val scope = rememberCoroutineScope()
    val history = remember { mutableStateListOf<Crop>() }; val future = remember { mutableStateListOf<Crop>() }
    fun checkpoint() { if (history.size >= 50) history.removeAt(0); history.add(crop); future.clear() }
    LaunchedEffect(page.original) {
        try { bitmap = withContext(Dispatchers.IO) { ImageProcessor.decode(vm.repository.file(page), 1600) } }
        catch (e: CancellationException) { throw e } catch (e: Exception) { vm.error = e.message }
    }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Adjust crop", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { checkpoint(); crop = Crop.decode(page.crop); zoom = 1f; pan = Offset.Zero }) { Text("Reset") }
        }
        Box(Modifier.weight(1f).fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Color(0xFF101315)), contentAlignment = Alignment.Center) {
            bitmap?.let { image ->
                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val ratio = image.width.toFloat() / image.height
                    val width = minOf(maxWidth - 24.dp, (maxHeight - 24.dp) * ratio)
                    Box(Modifier.width(width).aspectRatio(ratio).graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y }) {
                        Image(image.asImageBitmap(), "Original page", Modifier.fillMaxSize())
                        Canvas(Modifier.fillMaxSize().pointerInput(image, zoomMode) {
                            if (zoomMode) detectTransformGestures { _, delta, scale, _ ->
                                zoom = (zoom * scale).coerceIn(1f, 4f)
                                pan = if (zoom == 1f) Offset.Zero else Offset((pan.x + delta.x).coerceIn(-size.width * (zoom - 1) / 2, size.width * (zoom - 1) / 2), (pan.y + delta.y).coerceIn(-size.height * (zoom - 1) / 2, size.height * (zoom - 1) / 2))
                            } else {
                                var active = -1
                                detectDragGestures(onDragStart = { touch ->
                                    val handles = crop.handles()
                                    active = handles.indices.minBy { hypot(touch.x - handles[it].x * size.width, touch.y - handles[it].y * size.height) }
                                    val p = handles[active]
                                    if (hypot(touch.x - p.x * size.width, touch.y - p.y * size.height) > 32.dp.toPx() / zoom) active = -1
                                    else { selected = active; checkpoint(); dragging = true }
                                }, onDragEnd = { active = -1; dragging = false }, onDragCancel = { active = -1; dragging = false }) { change, delta ->
                                    if (active >= 0) { change.consume(); crop = crop.move(active, delta.x / size.width, delta.y / size.height) }
                                }
                            }
                        }) {
                            val p = crop.points.map { Offset(it.x * size.width, it.y * size.height) }
                            val outside = Path().apply { fillType = PathFillType.EvenOdd; addRect(Rect(Offset.Zero, size)); moveTo(p[0].x, p[0].y); p.drop(1).forEach { lineTo(it.x, it.y) }; close() }
                            drawPath(outside, Color.Black.copy(alpha = .55f))
                            fun mix(a: Offset, b: Offset, t: Float) = a + (b - a) * t
                            listOf(1f / 3, 2f / 3).forEach { t ->
                                drawLine(Color.White.copy(alpha = .35f), mix(p[0], p[1], t), mix(p[3], p[2], t), 1.dp.toPx() / zoom)
                                drawLine(Color.White.copy(alpha = .35f), mix(p[0], p[3], t), mix(p[1], p[2], t), 1.dp.toPx() / zoom)
                            }
                            p.indices.forEach { drawLine(Color(0xFFD4F77D), p[it], p[(it + 1) % 4], 2.dp.toPx() / zoom) }
                            crop.handles().forEachIndexed { index, handle ->
                                val center = Offset(handle.x * size.width, handle.y * size.height)
                                drawCircle(Color(0xFFD4F77D), (if (index < 4) 8 else 6).dp.toPx() / zoom, center)
                                if (index == selected) drawCircle(Color.White, 12.dp.toPx() / zoom, center, style = Stroke(2.dp.toPx() / zoom))
                            }
                        }
                    }
                }
                if (dragging || !zoomMode) Canvas(Modifier.align(Alignment.TopEnd).padding(12.dp).size(96.dp).clip(CircleShape).border(2.dp, Color(0xFFD4F77D), CircleShape)) {
                    val handle = crop.handles()[selected]; val side = min(image.width, image.height).coerceAtMost(160)
                    val x = (handle.x * image.width - side / 2).toInt().coerceIn(0, image.width - side)
                    val y = (handle.y * image.height - side / 2).toInt().coerceIn(0, image.height - side)
                    drawImage(image.asImageBitmap(), srcOffset = IntOffset(x, y), srcSize = IntSize(side, side), dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                    val center = Offset((handle.x * image.width - x) / side * size.width, (handle.y * image.height - y) / side * size.height)
                    drawLine(Color(0xFFD4F77D), center - Offset(10f, 0f), center + Offset(10f, 0f), 2f)
                    drawLine(Color(0xFFD4F77D), center - Offset(0f, 10f), center + Offset(0f, 10f), 2f)
                }
            } ?: CircularProgressIndicator()
        }
        Text(hint, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = bitmap != null && !detecting, onClick = { scope.launch {
                detecting = true
                try { val result = withContext(Dispatchers.Default) { DocumentDetector.detect(bitmap!!) }; if (result != null) { checkpoint(); crop = result; hint = "Edges detected. Adjust any handle before applying." } else hint = "No clear page boundary. Adjust the handles manually." }
                catch (e: CancellationException) { throw e } catch (e: Exception) { vm.error = e.message } finally { detecting = false }
            } }) { Text(if (detecting) "Detecting…" else "Auto-detect") }
            OutlinedButton(onClick = { checkpoint(); crop = Crop.Full }) { Text("Full image") }
            FilterChip(selected = zoomMode, onClick = { zoomMode = !zoomMode }, label = { Text(if (zoomMode) "Zoom / pan" else "Edit handles") })
            TextButton(onClick = { zoom = 1f; pan = Offset.Zero }) { Text("Fit") }
            TextButton(enabled = history.isNotEmpty(), onClick = { future.add(crop); crop = history.removeAt(history.lastIndex) }) { Text("Undo") }
            TextButton(enabled = future.isNotEmpty(), onClick = { history.add(crop); crop = future.removeAt(future.lastIndex) }) { Text("Redo") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            Text("Fine adjust ${selected + 1}")
            listOf("←" to Corner(-1f, 0f), "↑" to Corner(0f, -1f), "↓" to Corner(0f, 1f), "→" to Corner(1f, 0f)).forEach { (label, direction) ->
                TextButton(enabled = bitmap != null, onClick = { bitmap?.let { checkpoint(); crop = crop.move(selected, direction.x / it.width, direction.y / it.height) } }) { Text(label) }
            }
        }
        Button(enabled = bitmap != null && !vm.busy && !detecting, onClick = { onApply(crop) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Apply crop") }
    }
}
