package org.ods.scanner

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.*

object DocumentDetector {
    private val ready by lazy { OpenCVLoader.initLocal() }
    fun detect(bitmap: Bitmap): Crop? = synchronized(this) {
        if (!ready) return null
        val src = Mat(); val gray = Mat(); val edges = Mat(); val hierarchy = Mat()
        val contours = ArrayList<MatOfPoint>()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        try {
            Utils.bitmapToMat(bitmap, src)
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(gray, edges, 40.0, 120.0)
            Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, kernel)
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            var best: Crop? = null; var bestScore = 0.0
            for (contour in contours.sortedByDescending { Imgproc.contourArea(it) }.take(30)) {
                val curve = MatOfPoint2f(*contour.toArray()); val polygon = MatOfPoint2f()
                try {
                    Imgproc.approxPolyDP(curve, polygon, .02 * Imgproc.arcLength(curve, true), true)
                    val raw = polygon.toArray(); if (raw.size != 4) continue
                    val cx = raw.map { it.x }.average(); val cy = raw.map { it.y }.average()
                    val ordered = raw.sortedBy { atan2(it.y - cy, it.x - cx) }
                    val start = ordered.indices.minBy { ordered[it].x + ordered[it].y }
                    val points = (0..3).map { ordered[(start + it) % 4] }.map { Corner((it.x / (bitmap.width - 1)).toFloat().coerceIn(0f, 1f), (it.y / (bitmap.height - 1)).toFloat().coerceIn(0f, 1f)) }
                    val crop = Crop(points); if (!crop.valid() || crop.area() < .12f) continue
                    val rectangularity = points.indices.map { i ->
                        val a = points[(i + 3) % 4]; val b = points[i]; val c = points[(i + 1) % 4]
                        val ux = (a.x - b.x) * bitmap.width; val uy = (a.y - b.y) * bitmap.height
                        val vx = (c.x - b.x) * bitmap.width; val vy = (c.y - b.y) * bitmap.height
                        abs((ux * vx + uy * vy) / (hypot(ux, uy) * hypot(vx, vy)).coerceAtLeast(.001f))
                    }.average()
                    val score = crop.area() * (1 - rectangularity * .5)
                    if (score > bestScore) { best = crop; bestScore = score }
                } finally { curve.release(); polygon.release() }
            }
            best
        } finally { contours.forEach { it.release() }; src.release(); gray.release(); edges.release(); hierarchy.release(); kernel.release() }
    }
}
