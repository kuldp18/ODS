package org.ods.scanner

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.*
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
import kotlin.math.min

@Composable fun CameraScreen(vm: ScannerViewModel) {
    val context = LocalContext.current; val owner = LocalLifecycleOwner.current
    val preview = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
    val capture = remember { ImageCapture.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var taking by remember { mutableStateOf(false) }
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
        val file = vm.repository.captureFile()
        capture.targetRotation = preview.display?.rotation ?: 0
        capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), main, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) { taking = false; vm.captured(file) }
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
    Column(Modifier.fillMaxSize().background(Color(0xFF101315)).padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { vm.screen = "library" }) { Text("Close", color = Color(0xFFD4F77D)) }
            TextButton(onClick = { flash = !flash; capture.flashMode = if (flash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF }) { Text(if (flash) "Flash on" else "Flash off", color = Color(0xFFD4F77D)) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory = { preview }, modifier = Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                detected?.let { crop ->
                    val w = min(size.width, size.height*frameRatio); val h = w/frameRatio
                    val x = (size.width-w)/2; val y = (size.height-h)/2
                    val points = crop.points.map { Offset(x+it.x*w,y+it.y*h) }
                    points.indices.forEach { drawLine(Color(0xFFD4F77D),points[it],points[(it+1)%4],2.dp.toPx()) }
                }
            }
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Switch(auto, { auto = it }); Text(if (auto) "Auto capture • Hold steady" else "Manual capture", color = Color.White) }
        Text(if (detected != null) "Page detected" else "Keep the whole page in view", Modifier.padding(vertical = 8.dp), color = Color.White)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(enabled = !taking && !vm.busy, onClick = { shutter() }, modifier = Modifier.weight(1f)) { Text("Capture page") }
            OutlinedButton(enabled = !taking && !vm.busy, onClick = { vm.screen = "document" }) { Text("Done ($count)", color = Color(0xFFD4F77D)) }
        }
    }
}
