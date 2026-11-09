package org.ods.scanner

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.tensorflow.lite.Interpreter
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.core.Core
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.*

/** Models are bundled by the build, never fetched from the running app. */
object ScanModels {
    private lateinit var context: Context
    fun configure(value: Context) { context = value }
    private val environment by lazy { OrtEnvironment.getEnvironment() }
    private val corners by lazy {
        OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(2)
            environment.createSession(context.assets.open("models/docaligner.onnx").use { it.readBytes() }, options)
        }
    }
    private val light by lazy {
        val bytes = context.assets.open("models/dcenet.tflite").use { it.readBytes() }
        val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply { put(bytes); rewind() }
        Interpreter(model, Interpreter.Options().setNumThreads(2)).apply {
            resizeInput(0, intArrayOf(1, 128, 128, 4)); allocateTensors()
            require(getOutputTensor(0).shape().contentEquals(intArrayOf(1,128,128,24))) { "Unsupported enhancement model" }
        }
    }
    @Synchronized fun detect(bitmap: Bitmap): Crop? {
        val small = Bitmap.createScaledBitmap(bitmap, 256, 256, true)
        val pixels = IntArray(256 * 256)
        try { small.getPixels(pixels, 0, 256, 0, 0, 256, 256) } finally { if (small !== bitmap) small.recycle() }
        val input = FloatArray(pixels.size * 3)
        // DocAligner's published preprocessing is BGR / 255 in NCHW order.
        pixels.forEachIndexed { index, pixel ->
            input[index] = (pixel and 255) / 255f
            input[pixels.size + index] = ((pixel shr 8) and 255) / 255f
            input[2 * pixels.size + index] = ((pixel shr 16) and 255) / 255f
        }
        val direct = ByteBuffer.allocateDirect(input.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(input); rewind() }
        OnnxTensor.createTensor(environment, direct, longArrayOf(1,3,256,256)).use { tensor ->
            corners.run(mapOf("img" to tensor)).use { result ->
                val confidence = (result.get("has_obj").get() as OnnxTensor).floatBuffer.get(0)
                if (!confidence.isFinite() || confidence < .65f) return null
                val buffer = (result.get("points").get() as OnnxTensor).floatBuffer
                val raw = List(4) { Corner(buffer.get(it * 2), buffer.get(it * 2 + 1)) }
                if (raw.any { !it.x.isFinite() || !it.y.isFinite() || it.x !in -.02f..1.02f || it.y !in -.02f..1.02f }) return null
                val cx = raw.map { it.x }.average(); val cy = raw.map { it.y }.average()
                val sorted = raw.sortedBy { atan2(it.y - cy, it.x - cx) }
                val first = sorted.indices.minBy { sorted[it].x + sorted[it].y }
                return Crop((0..3).map { sorted[(first + it) % 4].let { p -> Corner(p.x.coerceIn(0f,1f), p.y.coerceIn(0f,1f)) } }).takeIf { it.valid() && it.area() >= .08f }
            }
        }
    }
    /** Predict on a small image, interpolate bounded gain maps onto original detail. */
    @Synchronized fun enhance(rgb: Mat) {
        val small = Mat(); val gain = Mat(128, 128, CvType.CV_32FC3); val fullGain = Mat(); val full = Mat()
        try {
            Imgproc.resize(rgb, small, Size(128.0,128.0))
            val pixels = ByteArray(128 * 128 * 3); small.get(0,0,pixels)
            val input = ByteBuffer.allocateDirect(128 * 128 * 4 * 4).order(ByteOrder.nativeOrder())
            val guidance = (pixels.sumOf { (it.toInt() and 255).toLong() }.toFloat() / pixels.size / 255).coerceAtMost(.5f)
            for (i in 0 until 128 * 128) { for (c in 0..2) input.putFloat((pixels[i*3+c].toInt() and 255) / 255f); input.putFloat(guidance) }
            input.rewind()
            val output = ByteBuffer.allocateDirect(128 * 128 * 24 * 4).order(ByteOrder.nativeOrder())
            light.run(input, output); output.rewind(); val curves = output.asFloatBuffer()
            val gains = FloatArray(pixels.size)
            for (i in 0 until 128*128) for (c in 0..2) {
                val original = (pixels[i*3+c].toInt() and 255) / 255f; var value = original
                for (step in 0..7) { val r = curves.get(i*24 + step*3 + c); require(r.isFinite()); value += r.coerceIn(-1f,1f) * (value*value-value) }
                gains[i*3+c] = 1f + .65f * ((value / original.coerceAtLeast(.03f)).coerceIn(.8f,2f) - 1f)
            }
            gain.put(0,0,gains); Imgproc.resize(gain, fullGain, rgb.size()); rgb.convertTo(full, CvType.CV_32FC3)
            Core.multiply(full, fullGain, full); full.convertTo(rgb, CvType.CV_8UC3)
        } finally { small.release(); gain.release(); fullGain.release(); full.release() }
    }
}
