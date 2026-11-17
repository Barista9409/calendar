package io.github.barista9409.calendar.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.barista9409.calendar.google.Attachment
import io.github.barista9409.calendar.ui.AttachState

private fun kindOf(a: Attachment): Triple<String, Color, Color> {
    val m = a.mimeType.orEmpty(); val n = a.title.lowercase()
    return when {
        m == "application/pdf" || n.endsWith(".pdf") -> Triple("PDF", Color(0xFFFDECEA), Color(0xFFA93226))
        m.startsWith("image/") || listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".heic").any { n.endsWith(it) } -> Triple("IMG", Color(0xFFE8EEFC), Color(0xFF2443A8))
        m.contains("sheet") || m.contains("excel") || n.endsWith(".xlsx") || n.endsWith(".csv") -> Triple("XLS", Color(0xFFE4F4EC), Color(0xFF1E6B47))
        m.contains("presentation") || m.contains("powerpoint") || n.endsWith(".pptx") -> Triple("PPT", Color(0xFFFDF0E6), Color(0xFF9A3412))
        m.contains("word") || m.contains("document") || n.endsWith(".docx") -> Triple("DOC", Color(0xFFE8F0FE), Color(0xFF1A4CB0))
        else -> Triple("FILE", Color(0xFFEEF0F4), Color(0xFF4A4F5A))
    }
}

/** 予定詳細の「添付ファイル」欄 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AttachmentsSection(
    state: AttachState,
    uploading: Float?,
    isRecurring: Boolean,
    onPick: () -> Unit,
    onOpen: (Attachment) -> Unit,
    onRemove: (Attachment) -> Unit,
    onLink: (String) -> Unit,
    thumbnail: suspend (account: String, Attachment, sizePx: Int) -> android.graphics.Bitmap? = { _, _, _ -> null },
    showPreview: Boolean = true,
) {
    val sizePx = with(androidx.compose.ui.platform.LocalDensity.current) { 360.dp.roundToPx() }
    val scheme = MaterialTheme.colorScheme
    var confirmRemove by remember { mutableStateOf<Attachment?>(null) }

    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AttachFile, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Text("添付ファイル" + ((state as? AttachState.Ready)?.items?.size?.let { "（$it）" } ?: ""),
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
        }
        when (state) {
            AttachState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            AttachState.NotGoogle -> Hint("Google のカレンダーの予定にだけ添付できます。")
            is AttachState.Error -> Hint(state.message, error = true)
            is AttachState.Ready -> {
                if (state.items.isEmpty() && uploading == null) Hint("添付ファイルはありません")
                state.items.forEach { a ->
                    val (label, bg, fg) = kindOf(a)
                    val visual = showPreview && io.github.barista9409.calendar.util.Thumbnails.supports(a.mimeType, a.title) && a.fileId != null
                    val thumb by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, a.fileId) {
                        if (visual) value = thumbnail(state.account, a, sizePx)
                    }
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(12.dp))
                            .combinedClickable(onClick = { onOpen(a) }, onLongClick = { if (state.canEdit) confirmRemove = a }),
                    ) {
                        if (visual) {
                            // 画像・PDF は切らずに全体を表示（縦長は高さを抑える。タップで全画面）
                            val b = thumb
                            if (b != null) {
                                val ratio = (b.width.toFloat() / b.height.coerceAtLeast(1)).coerceIn(0.5f, 3f)
                                Box(Modifier.fillMaxWidth().heightIn(max = 420.dp).background(bg), contentAlignment = Alignment.Center) {
                                    androidx.compose.foundation.Image(b.asImageBitmap(), contentDescription = a.title,
                                        modifier = Modifier.fillMaxWidth().aspectRatio(ratio),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                                }
                            } else Box(Modifier.fillMaxWidth().height(160.dp).background(bg), contentAlignment = Alignment.Center) {
                                androidx.compose.material3.CircularProgressIndicator(Modifier.size(28.dp), color = fg, strokeWidth = 3.dp)
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(width = 34.dp, height = 22.dp).clip(RoundedCornerShape(5.dp)).background(bg), contentAlignment = Alignment.Center) {
                                Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = fg)
                            }
                            Text(a.title, Modifier.weight(1f).padding(start = 10.dp, top = 10.dp, bottom = 10.dp), fontSize = 14.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            if (state.canEdit) IconButton(onClick = { confirmRemove = a }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "添付を削除", tint = scheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (uploading != null) {
                    Text("アップロード中… ${(uploading * 100).toInt()}%", fontSize = 12.sp, color = scheme.onSurfaceVariant)
                    LinearProgressIndicator(progress = { uploading }, modifier = Modifier.fillMaxWidth())
                } else if (state.canEdit && !state.linked) {
                    Hint("ファイルを添付するには、${state.account} を Google と連携してください（審査のいらない、このアプリが作るファイルだけの権限です）。")
                    Button(onClick = { onLink(state.account) }) { Text("Google と連携") }
                } else if (state.canEdit) {
                    OutlinedButton(onClick = onPick) {
                        Icon(Icons.Outlined.AttachFile, null); Spacer(Modifier.width(6.dp)); Text("ファイルを添付")
                    }
                    if (state.items.isEmpty()) Hint("Google ドライブの「カレンダー添付ファイル」フォルダに保存し、予定の説明にリンクを書き込みます。リンクを知っている人なら見られるので、家族も Google カレンダーアプリから開けます。")
                    if (isRecurring) Hint("繰り返しの予定では、すべての回に添付されます。")
                }
            }
        }
    }

    confirmRemove?.let { a ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("「${a.title}」を削除しますか？") },
            text = { Text("予定の説明からリンクを外します。自分が追加したファイルはドライブのゴミ箱に移動します（30 日間は復元できます）。") },
            confirmButton = { TextButton(onClick = { confirmRemove = null; onRemove(a) }) { Text("削除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("キャンセル") } },
        )
    }
}

@Composable
private fun Hint(text: String, error: Boolean = false) {
    Text(text, fontSize = 12.sp, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
}
