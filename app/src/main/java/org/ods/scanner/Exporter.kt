package org.ods.scanner

import android.content.*
import android.graphics.*
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID
import java.util.zip.*

class Exporter(private val repository: Repository) {
    suspend fun generate(documents: List<DocumentPages>, format: Format, quality: Quality, filename: String, merge: Boolean, paper: PaperSize = PaperSize.Auto, archiveFormat: Format = Format.PDF): List<File> = withContext(Dispatchers.IO) {
        require(documents.isNotEmpty() && documents.any { it.pages.isNotEmpty() }) { "Add a page first" }
        val directory = File(repository.context.cacheDir, "exports/${UUID.randomUUID()}").apply { check(mkdirs()) }
        require(directory.usableSpace > 32L * 1024 * 1024) { "At least 32 MB of free space is needed" }
        val name = safeName(filename)
        val outputFormat = if (format == Format.ZIP) archiveFormat else format
        require(outputFormat != Format.ZIP)
        try {
            val groups = if (merge) listOf(documents.flatMap { it.ordered() }) else documents.map { it.ordered() }.filter { it.isNotEmpty() }
            val outputs = mutableListOf<File>()
            for ((index, pages) in groups.withIndex()) {
                ensureActive()
                val base = if (groups.size == 1) name else "$name-${index + 1}"
                if (outputFormat == Format.PDF) {
                    val target = File(directory, "$base.pdf")
                    val pdf = PdfDocument()
                    try {
                        pages.forEachIndexed { i, page ->
                            ensureActive()
                            val bitmap = ImageProcessor.render(repository.file(page), page, quality.longEdge)
                            try {
                                val width = if (paper == PaperSize.Letter) 612 else 595
                                val height = when (paper) { PaperSize.A4 -> 842; PaperSize.Letter -> 792; else -> (width.toFloat() * bitmap.height / bitmap.width).toInt().coerceAtLeast(1) }
                                val sheet = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, i + 1).create())
                                sheet.canvas.drawColor(Color.WHITE)
                                val margin = if (paper == PaperSize.Auto) 0f else 18f
                                val scale = minOf((width-2*margin)/bitmap.width, (height-2*margin)/bitmap.height)
                                val w = bitmap.width*scale; val h = bitmap.height*scale
                                sheet.canvas.drawBitmap(bitmap, null, RectF((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                                pdf.finishPage(sheet)
                            } finally { bitmap.recycle() }
                        }
                        target.outputStream().use { pdf.writeTo(it) }; outputs += target
                    } finally { pdf.close() }
                } else {
                    pages.forEachIndexed { i, page ->
                        ensureActive()
                        val bitmap = ImageProcessor.render(repository.file(page), page, quality.longEdge)
                        try {
                            val target = File(directory, "$base-${i + 1}.${outputFormat.extension}")
                            target.outputStream().use { check(bitmap.compress(if (outputFormat == Format.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, quality.jpeg, it)) }
                            outputs += target
                        } finally { bitmap.recycle() }
                    }
                }
            }
            if (format == Format.ZIP) {
                val archive = File(directory, "$name.zip")
                ZipOutputStream(archive.outputStream().buffered()).use { zip -> outputs.forEach { file ->
                    ensureActive(); zip.putNextEntry(ZipEntry(file.name)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                } }
                outputs.forEach { it.delete() }; listOf(archive)
            } else outputs
        } catch (error: Throwable) { directory.deleteRecursively(); throw error }
    }
    fun share(files: List<File>, format: Format) {
        val context = repository.context
        val uris = ArrayList(files.map { FileProvider.getUriForFile(context, "${context.packageName}.files", it) })
        val intent = Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
            type = format.mime
            if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris.first()) else putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            clipData = ClipData.newUri(context.contentResolver, "ODS export", uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share documents").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
