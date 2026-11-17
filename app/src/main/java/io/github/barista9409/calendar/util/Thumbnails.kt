package io.github.barista9409.calendar.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.exifinterface.media.ExifInterface
import java.io.File

/** 添付ファイルの小さなサムネイル（画像と PDF の 1 ページ目） */
object Thumbnails {
    private val cache = android.util.LruCache<String, Bitmap>(12)

    fun supports(mime: String?, name: String): Boolean {
        val n = name.lowercase()
        return mime?.startsWith("image/") == true || mime == "application/pdf" ||
            listOf(".jpg", ".jpeg", ".png", ".webp", ".heic", ".gif", ".pdf").any { n.endsWith(it) }
    }

    fun load(file: File, sizePx: Int): Bitmap? {
        val key = "${file.path}:${file.length()}:$sizePx"
        cache.get(key)?.let { return it }
        val bmp = runCatching { if (file.name.lowercase().endsWith(".pdf") || isPdf(file)) pdf(file, sizePx) else image(file, sizePx) }.getOrNull()
        if (bmp != null) cache.put(key, bmp)
        return bmp
    }

    private fun isPdf(f: File) = runCatching { f.inputStream().use { s -> ByteArray(4).also { s.read(it) }.contentEquals("%PDF".toByteArray()) } }.getOrDefault(false)

    private fun image(file: File, sizePx: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, o)
        if (o.outWidth <= 0) return null
        var s = 1
        while (minOf(o.outWidth, o.outHeight) / (s * 2) >= sizePx) s *= 2
        val b = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
        val deg = when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) { 6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f }
        return if (deg == 0f) b else Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(deg) }, true)
    }

    private fun pdf(file: File, sizePx: Int): Bitmap? =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { r ->
                if (r.pageCount == 0) return null
                r.openPage(0).use { p ->
                    val scale = sizePx.toFloat() / maxOf(p.width, p.height)
                    val bmp = Bitmap.createBitmap((p.width * scale).toInt().coerceAtLeast(1), (p.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }
        }
}
