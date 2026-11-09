package org.ods.scanner

import android.graphics.Bitmap
import android.util.LruCache

object PreviewCache {
    private val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    fun get(repository: Repository, page: Page, edge: Int): Bitmap {
        val key = "$page:$edge"
        cache.get(key)?.let { return it }
        return ImageProcessor.render(repository.file(page),page,edge).also { cache.put(key,it) }
    }
}
