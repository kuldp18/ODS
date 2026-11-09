package org.ods.scanner

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.*
import androidx.activity.compose.BackHandler
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors
import kotlin.math.max
import androidx.compose.ui.Alignment

@Composable fun CameraScreen(vm: ScannerViewModel) {
    val context = LocalContext.current; val owner = LocalLifecycleOwner.current
    val preview = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val capture = remember { ImageCapture.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var taking by remember { mutableStateOf(false) }
    BackHandler(taking) { /* Finish writing the captured photo before leaving. */ }
    var flash by remember { mutableStateOf(false) }
    var auto by remember { mutableStateOf(false) }
    val currentAuto by rememberUpdatedState(auto)
    var detected by remember { mutableStateOf<Crop?>(null) }
    var frameRatio by remember { mutableFloatStateOf(3f/4f) }
    val gate = remember { AutoCaptureGate() }
    val main = remember { ContextCompat.getMainExecutor(context) }
    val docs by vm.documents.collectAsState()
    val count = docs.firstOrNull { it.document.id == vm.activeId }?.pages?.size ?: 0
    fun shutter() {
        if (taking || vm.busy) return
        taking = true
        auto = false
        val file = vm.repository.captureFile()
        capture.targetRotation = preview.display?.rotation ?: 0
        capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), main, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                provider?.unbindAll()
                vm.screen = "document"
                vm.captured(file)
            }
            override fun onError(exception: ImageCaptureException) { taking = false; file.delete(); vm.error = exception.message }
        })
    }
    val currentShutter by rememberUpdatedState({ shutter() })
    DisposableEffect(owner) {
        var disposed = false
        val executor = Executors.newSingleThreadExecutor()
        val analysis = ImageAnalysis.Builder().setTargetResolution(android.util.Size(640,480)).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build()
        var last = 0L
        analysis.setAnalyzer(executor) { frame ->
            try {
                val now = SystemClock.elapsedRealtime()
                if (!disposed && now-last >= 200) {
                    last = now
                    val plane = frame.planes[0]
                    val packed = java.nio.ByteBuffer.allocateDirect(frame.width*frame.height*4)
                    val buffer = plane.buffer.duplicate()
                    for (row in 0 until frame.height) {
                        val offset = row*plane.rowStride
                        if (plane.pixelStride == 4) {
                            val line = buffer.duplicate(); line.position(offset); line.limit(offset+frame.width*4); packed.put(line)
                        } else for (column in 0 until frame.width) { val offsetPixel = offset+column*plane.pixelStride; for (channel in 0..3) packed.put(buffer.get(offsetPixel+channel)) }
                    }
                    packed.rewind()
                    val cropped = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
                    cropped.copyPixelsFromBuffer(packed)
                    val oriented = Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, Matrix().apply { postRotate(frame.imageInfo.rotationDegrees.toFloat()) }, true)
                    if (oriented !== cropped) cropped.recycle()
                    try {
                        val crop = DocumentDetector.detect(oriented)
                        val ratio = oriented.width.toFloat()/oriented.height
                        // A simple gradient-energy gate prevents stable but obviously blurred frames.
                        val pixels = IntArray(oriented.width * oriented.height)
                        oriented.getPixels(pixels, 0, oriented.width, 0, 0, oriented.width, oriented.height)
                        var energy = 0L; var samples = 0
                        for (y in 1 until oriented.height step 4) for (x in 1 until oriented.width step 4) {
                            val i = y*oriented.width+x
                            energy += kotlin.math.abs(((pixels[i] shr 8) and 255) - ((pixels[i-1] shr 8) and 255)); samples++
                        }
                        val sharp = energy.toFloat()/samples.coerceAtLeast(1) > 3f
                        val trigger = if (currentAuto) gate.update(crop, sharp, now) else { gate.update(null, false, now); false }
                        main.execute { if (!disposed) { detected = crop; frameRatio = ratio; if (trigger) currentShutter() } }
                    } finally { oriented.recycle() }
                }
            } catch (_: Exception) { /* Keep manual shutter usable when analysis fails. */ }
            finally { frame.close() }
        }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!disposed) runCatching {
                val camera = future.get(); provider = camera
                val useCase = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build().also { it.setSurfaceProvider(preview.surfaceProvider) }
                camera.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, useCase, capture, analysis)
            }.onFailure { vm.error = "Camera unavailable: ${it.message}" }
        }, main)
        onDispose { disposed = true; analysis.clearAnalyzer(); provider?.unbindAll(); executor.shutdown() }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { preview }, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            detected?.let { crop ->
                val scale = max(size.width/frameRatio,size.height)
                val w = scale*frameRatio; val h = scale
                val x = (size.width-w)/2; val y = (size.height-h)/2
                val points = crop.points.map { Offset(x+it.x*w,y+it.y*h) }
                points.indices.forEach { drawLine(Color(0xFFD4F77D),points[it],points[(it+1)%4],2.dp.toPx()) }
            }
        }
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color.Black.copy(alpha = .45f)).statusBarsPadding().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(enabled = !taking, onClick = { vm.screen = if (count > 0) "document" else "library" }) { Text("Back", color = Color.White) }
            Text("Scan a page", color = Color.White, style = MaterialTheme.typography.titleMedium)
            TextButton(enabled = !taking, onClick = { flash = !flash; capture.flashMode = if (flash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF }) { Text(if (flash) "Flash on" else "Flash off", color = Color.White) }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = .75f)).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (taking) "Saving page?" else if (detected != null && auto) "Hold steady" else if (detected != null) "Page detected" else "Fit the page inside the camera", color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(auto, { auto = it }, enabled = !taking); Text("Auto", color = Color.White, modifier = Modifier.padding(start = 8.dp)) }
                Text("${count+1} / new page", color = Color(0xFFA8B1AD), style = MaterialTheme.typography.labelSmall)
            }
            Button(enabled = !taking && !vm.busy, onClick = { shutter() }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (taking) "Saving?" else "Capture page") }
        }
    }
}
