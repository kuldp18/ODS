package org.ods.scanner

import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable fun CameraScreen(vm: ScannerViewModel) {
    val context = LocalContext.current; val owner = LocalLifecycleOwner.current
    val preview = remember { PreviewView(context) }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var taking by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(false) }
    DisposableEffect(owner) {
        var disposed = false
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!disposed) runCatching {
                val camera = future.get(); provider = camera
                val useCase = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
                camera.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, useCase, capture)
            }.onFailure { vm.error = "Camera unavailable: ${it.message}" }
        }, ContextCompat.getMainExecutor(context))
        onDispose { disposed = true; provider?.unbindAll() }
    }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { vm.screen = "library" }) { Text("Close") }
            TextButton(onClick = { flash = !flash; capture.flashMode = if (flash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF }) { Text(if (flash) "Flash on" else "Flash off") }
        }
        AndroidView(factory = { preview }, modifier = Modifier.weight(1f).fillMaxWidth())
        Text("Manual capture • Keep the whole page in view", Modifier.padding(vertical = 16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(enabled = !taking && !vm.busy, onClick = {
                taking = true
                val file = vm.repository.captureFile()
                capture.targetRotation = preview.display?.rotation ?: 0
                capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(result: ImageCapture.OutputFileResults) { taking = false; vm.captured(file) }
                    override fun onError(exception: ImageCaptureException) { taking = false; file.delete(); vm.error = exception.message }
                })
            }, modifier = Modifier.weight(1f)) { Text("Capture page") }
            OutlinedButton(enabled = !taking && !vm.busy, onClick = { vm.screen = "document" }) { Text("Done") }
        }
    }
}
