package io.github.barista9409.calendar.ui.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** プレビューできる種類か */
fun canPreview(mime: String?, name: String): Boolean {
    val n = name.lowercase()
    return mime == "application/pdf" || n.endsWith(".pdf") || mime?.startsWith("image/") == true ||
        listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".heic").any { n.endsWith(it) }
}

private fun isPdf(mime: String?, name: String) = mime == "application/pdf" || name.lowercase().endsWith(".pdf")

/**
 * 添付ファイルのプレビュー（PDF・画像）。ピンチで拡大、ダブルタップで元の大きさ。
 */
@Composable
fun PreviewScreen(file: java.io.File, mime: String?, title: String, onClose: () -> Unit, onOpenExternal: () -> Unit, onShare: () -> Unit) {
    BackHandler(onBack = onClose)
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF111111))) {
        Box(
            Modifier.fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; offset = Offset.Zero }) }
                .transformable(transform, canPan = { scale > 1f })
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
        ) {
            if (isPdf(mime, title)) PdfPages(file) else ImageView(file)
        }
        Row(
            Modifier.fillMaxWidth().background(Color(0xAA000000)).statusBarsPadding().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "閉じる", tint = Color.White) }
            Text(title, Modifier.weight(1f), color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, contentDescription = "共有", tint = Color.White) }
            IconButton(onClick = onOpenExternal) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "他のアプリで開く", tint = Color.White) }
        }
    }
}

@Composable
private fun ImageView(file: java.io.File) {
    val maxPx = with(LocalDensity.current) { (LocalConfiguration.current.screenWidthDp.dp.toPx() * 2).toInt().coerceAtMost(4096) }
    var failed by remember { mutableStateOf(false) }
    val bmp by produceState<Bitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { decodeSampled(file, maxPx) }
        failed = value == null
    }
    if (failed) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("画像を表示できませんでした", color = Color.White) }
        return
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b == null) CircularProgressIndicator()
        else Image(b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
    }
}

/** 画像を画面に合う大きさで読み込む（向き情報 EXIF も反映） */
private fun decodeSampled(file: java.io.File, maxPx: Int): Bitmap? = runCatching {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, opts)
    var sample = 1
    while (opts.outWidth > 0 && maxOf(opts.outWidth, opts.outHeight) / sample > maxPx) sample *= 2
    val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: (if (android.os.Build.VERSION.SDK_INT >= 28) android.graphics.ImageDecoder.decodeBitmap(
            android.graphics.ImageDecoder.createSource(file)) { d, info, _ ->
                val s = maxOf(info.size.width, info.size.height).toFloat() / maxPx
                if (s > 1) d.setTargetSize((info.size.width / s).toInt(), (info.size.height / s).toInt())
                d.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            } else null)
        ?: return@runCatching null
    val rot = runCatching {
        when (androidx.exifinterface.media.ExifInterface(file.path).getAttributeInt(
            androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, 1)) { 6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f }
    }.getOrDefault(0f)
    if (rot == 0f) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, android.graphics.Matrix().apply { postRotate(rot) }, true)
}.getOrNull()

@Composable
private fun PdfPages(file: java.io.File) {
    val widthPx = with(LocalDensity.current) { (LocalConfiguration.current.screenWidthDp.dp.toPx() * 1.5f).toInt().coerceAtMost(2400) }
    val doc = remember(file) {
        runCatching {
            val pfd: ParcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pfd to PdfRenderer(pfd)
        }.getOrNull()
    }
    val lock = remember { Mutex() }
    DisposableEffect(doc) { onDispose { doc?.let { runCatching { it.second.close(); it.first.close() } } } }
    if (doc == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("PDF を開けませんでした", color = Color.White) }
        return
    }
    val renderer = doc.second
    // ページの縦横比（描画中に別ページを開けないため、最初にまとめて取得）
    val ratios = remember(doc) {
        (0 until renderer.pageCount).map { i -> runCatching { renderer.openPage(i).use { it.width.toFloat() / it.height } }.getOrDefault(0.707f) }
    }
    LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 64.dp, bottom = 16.dp, start = 8.dp, end = 8.dp)) {
        items(renderer.pageCount) { i ->
            val ratio = ratios[i]
            val page by produceState<Bitmap?>(null, i) {
                value = withContext(Dispatchers.IO) {
                    lock.withLock {
                        runCatching {
                            renderer.openPage(i).use { p ->
                                val h = (widthPx * p.height.toFloat() / p.width).toInt()
                                Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888).also { bmp ->
                                    bmp.eraseColor(AColor.WHITE)
                                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                }
                            }
                        }.getOrNull()
                    }
                }
            }
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().aspectRatio(ratio).background(Color.White), contentAlignment = Alignment.Center) {
                    val b = page
                    if (b == null) CircularProgressIndicator()
                    else Image(b.asImageBitmap(), contentDescription = "${i + 1} ページ", modifier = Modifier.fillMaxSize())
                }
                Text("${i + 1} / ${renderer.pageCount}", color = Color(0xFFBBBBBB), fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp))
            }
        }
    }
}
