package io.github.barista9409.calendar.google

import android.content.Context
import io.github.barista9409.calendar.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * 添付ファイルの端末内キャッシュ（アプリ専用フォルダ）。保存先の本体は Google ドライブ。
 * ファイル名は「元の名前 (ID の先頭 8 文字).拡張子」。
 */
class AttachmentStore(private val context: Context, @Suppress("unused") private val prefs: Prefs) {

    private fun dir(): File = (context.getExternalFilesDir("attachments") ?: File(context.filesDir, "attachments")).apply { mkdirs() }

    fun file(fileId: String, title: String): File = File(dir(), storedName(fileId, title))

    fun find(fileId: String, title: String): File? = file(fileId, title).takeIf { it.exists() && it.length() > 0 }

    suspend fun save(fileId: String, title: String, source: () -> InputStream): File = withContext(Dispatchers.IO) {
        val f = file(fileId, title)
        val tmp = File(f.path + ".part")
        source().use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
        tmp.renameTo(f)
        f
    }

    suspend fun delete(fileId: String, title: String) = withContext(Dispatchers.IO) { file(fileId, title).delete(); Unit }

    /** キャッシュの合計サイズ（バイト）と件数 */
    suspend fun usage(): Pair<Long, Int> = withContext(Dispatchers.IO) {
        val files = dir().listFiles().orEmpty().filter { it.isFile }
        files.sumOf { it.length() } to files.size
    }

    suspend fun clear() = withContext(Dispatchers.IO) { dir().listFiles()?.forEach { it.delete() }; Unit }

    companion object {
        fun storedName(fileId: String, title: String): String {
            val safe = AttachmentService.safeName(title)
            val dot = safe.lastIndexOf('.')
            val (base, ext) = if (dot > 0) safe.substring(0, dot) to safe.substring(dot) else safe to ""
            return "$base (${fileId.take(8)})$ext"
        }
    }
}
