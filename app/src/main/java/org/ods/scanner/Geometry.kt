package org.ods.scanner

import kotlin.math.abs

data class Corner(val x: Float, val y: Float)

/** Clockwise TL, TR, BR, BL in the EXIF-oriented original, independent of preview size. */
data class Crop(val points: List<Corner>) {
    fun valid(): Boolean {
        if (points.size != 4 || points.any { !it.x.isFinite() || !it.y.isFinite() || it.x !in 0f..1f || it.y !in 0f..1f }) return false
        val crosses = points.indices.map { i ->
            val a = points[i]; val b = points[(i + 1) % 4]; val c = points[(i + 2) % 4]
            (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
        }
        return crosses.all { it > .0001f } && area() > .005f
    }
    fun area(): Float = abs(points.indices.sumOf { i ->
        val a = points[i]; val b = points[(i + 1) % points.size]
        (a.x * b.y - b.x * a.y).toDouble()
    }.toFloat()) / 2
    fun encode() = points.joinToString(";") { "${it.x},${it.y}" }
    companion object {
        val Full = Crop(listOf(Corner(0f, 0f), Corner(1f, 0f), Corner(1f, 1f), Corner(0f, 1f)))
        fun decode(value: String): Crop = runCatching {
            Crop(value.split(';').map { val p = it.split(','); Corner(p[0].toFloat(), p[1].toFloat()) }).also { require(it.valid()) }
        }.getOrDefault(Full)
    }
}
enum class Filter { Original, Auto, BW, Gray, Color }
enum class Format(val extension: String, val mime: String) { PDF("pdf", "application/pdf"), JPEG("jpg", "image/jpeg"), PNG("png", "image/png"), ZIP("zip", "application/zip") }
enum class Quality(val longEdge: Int, val jpeg: Int) { Small(1400, 65), Balanced(2400, 85), Best(4000, 95) }
fun safeName(name: String): String = name.replace(Regex("[^\\p{L}\\p{M}\\p{N} ._-]"), "_").trim(' ', '.').take(80).ifBlank { "Document" }
