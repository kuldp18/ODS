package org.ods.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import java.io.File
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.cos

object ImageProcessor {
    private val lock = Any()
    private val nativeReady by lazy { OpenCVLoader.initLocal() }
    fun decode(file: File, maxEdge: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "This file is not a supported image" }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxEdge * 2) sample *= 2
        val bitmap = requireNotNull(BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }))
        val orientation = ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix().apply {
            when (orientation) {
                2 -> setScale(-1f, 1f); 3 -> setRotate(180f); 4 -> setScale(1f, -1f)
                5 -> { setRotate(90f); postScale(-1f, 1f) }; 6 -> setRotate(90f)
                7 -> { setRotate(270f); postScale(-1f, 1f) }; 8 -> setRotate(270f)
            }
        }
        var result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (result !== bitmap) bitmap.recycle()
        if (max(result.width, result.height) > maxEdge) {
            val scale = maxEdge.toFloat() / max(result.width, result.height)
            val small = Bitmap.createScaledBitmap(result, max(1, (result.width * scale).roundToInt()), max(1, (result.height * scale).roundToInt()), true)
            if (small !== result) result.recycle(); result = small
        }
        return result
    }
    /** One native job at a time bounds simultaneous full resolution Mats. */
    fun render(file: File, page: Page, maxEdge: Int): Bitmap = synchronized(lock) {
        check(nativeReady) { "Image processing could not initialize" }
        val bitmap = decode(file, maxEdge)
        val source = Mat(); val warped = Mat(); val rgb = Mat(); val adjusted = Mat()
        val corners = Crop.decode(page.crop).points.map { Point((it.x * (bitmap.width - 1)).toDouble(), (it.y * (bitmap.height - 1)).toDouble()) }
        fun distance(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)
        val w = max(2, max(distance(corners[0], corners[1]), distance(corners[3], corners[2])).roundToInt() + 1)
        val h = max(2, max(distance(corners[0], corners[3]), distance(corners[1], corners[2])).roundToInt() + 1)
        val src = MatOfPoint2f(*corners.toTypedArray()); val dst = MatOfPoint2f(Point(0.0, 0.0), Point(w - 1.0, 0.0), Point(w - 1.0, h - 1.0), Point(0.0, h - 1.0))
        var transform: Mat? = null
        try {
            Utils.bitmapToMat(bitmap, source); bitmap.recycle()
            transform = Imgproc.getPerspectiveTransform(src, dst)
            Imgproc.warpPerspective(source, warped, transform, Size(w.toDouble(), h.toDouble()), Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE)
            Imgproc.cvtColor(warped, rgb, Imgproc.COLOR_RGBA2RGB)
            when (Filter.valueOf(page.filter)) {
                Filter.BW -> { Imgproc.cvtColor(rgb, rgb, Imgproc.COLOR_RGB2GRAY); Imgproc.adaptiveThreshold(rgb, rgb, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 31, page.threshold.toDouble()) }
                Filter.Gray -> Imgproc.cvtColor(rgb, rgb, Imgproc.COLOR_RGB2GRAY)
                Filter.Auto -> {
                    val lab = Mat(); val channels = ArrayList<Mat>(); val clahe = Imgproc.createCLAHE(2.0, Size(8.0,8.0))
                    try { Imgproc.cvtColor(rgb, lab, Imgproc.COLOR_RGB2Lab); Core.split(lab, channels); clahe.apply(channels[0], channels[0]); Core.merge(channels, lab); Imgproc.cvtColor(lab, rgb, Imgproc.COLOR_Lab2RGB) }
                    finally { channels.forEach { it.release() }; lab.release(); clahe.collectGarbage() }
                }
                Filter.LowLight -> ScanModels.enhance(rgb)
                Filter.Color -> rgb.convertTo(rgb, -1, 1.03, 0.0)
                Filter.Original -> Unit
            }
            rgb.convertTo(adjusted, -1, page.contrast.toDouble(), 128.0 * (1 - page.contrast) + page.brightness)
            if (page.straighten != 0f) {
                val angle = page.straighten.coerceIn(-10f,10f).toDouble()
                val radians = Math.toRadians(angle)
                val width = adjusted.cols(); val height = adjusted.rows()
                val newWidth = kotlin.math.ceil(width*abs(cos(radians))+height*abs(sin(radians))).toInt()
                val newHeight = kotlin.math.ceil(height*abs(cos(radians))+width*abs(sin(radians))).toInt()
                val matrix = Imgproc.getRotationMatrix2D(Point(width/2.0,height/2.0),angle,1.0)
                val straight = Mat()
                try {
                    matrix.put(0,2,matrix.get(0,2)[0]+(newWidth-width)/2.0)
                    matrix.put(1,2,matrix.get(1,2)[0]+(newHeight-height)/2.0)
                    Imgproc.warpAffine(adjusted,straight,matrix,Size(newWidth.toDouble(),newHeight.toDouble()),Imgproc.INTER_LINEAR,Core.BORDER_CONSTANT,Scalar.all(255.0))
                    straight.copyTo(adjusted)
                } finally { matrix.release(); straight.release() }
            }
            if (page.rotation != 0) Core.rotate(adjusted, adjusted, when (page.rotation) { 90 -> Core.ROTATE_90_CLOCKWISE; 180 -> Core.ROTATE_180; else -> Core.ROTATE_90_COUNTERCLOCKWISE })
            Bitmap.createBitmap(adjusted.cols(), adjusted.rows(), Bitmap.Config.ARGB_8888).also { Utils.matToBitmap(adjusted, it) }
        } finally { if (!bitmap.isRecycled) bitmap.recycle(); source.release(); warped.release(); rgb.release(); adjusted.release(); src.release(); dst.release(); transform?.release() }
    }
}
