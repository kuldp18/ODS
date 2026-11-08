package org.ods.scanner

import android.content.Context
import android.net.Uri
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.util.UUID

@Entity data class Document(@PrimaryKey val id: String = UUID.randomUUID().toString(), val title: String, val created: Long = System.currentTimeMillis())
@Entity(foreignKeys = [ForeignKey(entity = Document::class, parentColumns = ["id"], childColumns = ["documentId"], onDelete = ForeignKey.CASCADE)], indices = [Index("documentId")])
data class Page(@PrimaryKey val id: String = UUID.randomUUID().toString(), val documentId: String, val original: String, val position: Int,
    val crop: String = Crop.Full.encode(), val filter: String = Filter.Auto.name, val rotation: Int = 0, val contrast: Float = 1f)
data class DocumentPages(@Embedded val document: Document, @Relation(parentColumn = "id", entityColumn = "documentId") val pages: List<Page>) {
    fun ordered() = pages.sortedWith(compareBy<Page> { it.position }.thenBy { it.id })
}
@Dao interface ScannerDao {
    @Transaction @Query("SELECT * FROM Document ORDER BY created DESC") fun observe(): Flow<List<DocumentPages>>
    @Insert suspend fun insert(document: Document)
    @Insert suspend fun insert(page: Page)
    @Update suspend fun update(page: Page)
    @Update suspend fun update(document: Document)
    @Query("DELETE FROM Document WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM Page WHERE id = :id") suspend fun deletePage(id: String)
    @Transaction suspend fun reorder(pages: List<Page>) { pages.forEachIndexed { index, page -> update(page.copy(position = index)) } }
}
@Database(entities = [Document::class, Page::class], version = 1, exportSchema = false)
abstract class ScannerDatabase : RoomDatabase() { abstract fun dao(): ScannerDao }

class Repository(val context: Context) {
    private val db = Room.databaseBuilder(context, ScannerDatabase::class.java, "scanner.db").build()
    val dao = db.dao()
    private val originals = File(context.filesDir, "originals").apply { mkdirs() }
    fun file(page: Page) = File(originals, page.original)
    fun captureFile() = File(originals, "${UUID.randomUUID()}.jpg")
    suspend fun import(uri: Uri, document: Document, position: Int): Page = withContext(Dispatchers.IO) {
        val target = captureFile(); val temp = File(target.path + ".part")
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Cannot open this image" }
                temp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024); var total = 0L
                    while (true) { currentCoroutineContext().ensureActive(); val n = input.read(buffer); if (n < 0) break; total += n; require(total <= 80L * 1024 * 1024) { "Image exceeds 80 MB" }; output.write(buffer, 0, n) }
                }
            }
            ImageProcessor.decode(temp, 128).recycle() // Reject corrupt/unsupported content before committing.
            check(temp.renameTo(target)) { "Could not save image" }
            val page = Page(documentId = document.id, original = target.name, position = position)
            dao.insert(page); page
        } catch (error: Exception) { temp.delete(); target.delete(); throw error }
    }
}
