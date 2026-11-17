package io.github.barista9409.calendar.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.barista9409.calendar.util.ColorUtils

/** 名前つきの色（argb は不透明。0 は「標準」の意味で使うので、色として 0 は持たない） */
data class NamedColor(val name: String, val argb: Int)

private fun c(name: String, rgb: Long) = NamedColor(name, (0xFF000000L or rgb).toInt())

/** メインの色（ボタン・今日の印・選んだ日の枠）：万人向けの 8 色 */
val ACCENT_COLORS = listOf(
    c("ブルー", 0x3157D5), c("ティール", 0x00897B), c("グリーン", 0x2E7D32), c("オレンジ", 0xE8710A),
    c("レッド", 0xD93025), c("ピンク", 0xD81B60), c("パープル", 0x7B1FA2), c("グレー", 0x5F6368),
)

/** 背景（ライト）：先頭が標準 */
val LIGHT_BG_COLORS = listOf(
    c("ホワイト", 0xFFFFFF), c("スノー", 0xF6F7F9), c("クリーム", 0xFFF8E1), c("ベージュ", 0xF5F1E8),
    c("ミント", 0xE8F5EC), c("スカイ", 0xE8F0FE), c("ラベンダー", 0xF1ECFB), c("ピーチ", 0xFDEEE8),
)

/** 背景（ダーク）：先頭が標準 */
val DARK_BG_COLORS = listOf(
    c("チャコール", 0x15171C), c("ブラック", 0x000000), c("グラファイト", 0x2A2D33), c("ネイビー", 0x1A2340),
    c("フォレスト", 0x17302A), c("ブラウン", 0x2D2620), c("プラム", 0x2A1F33), c("スレート", 0x263238),
)

/** ウィジェットの背景 */
val WIDGET_BG_COLORS = listOf(
    c("ホワイト", 0xFFFFFF), c("クリーム", 0xFFF8E1), c("ミント", 0xE8F5EC), c("スカイ", 0xE8F0FE),
    c("チャコール", 0x1F242C), c("ネイビー", 0x1A2340), c("フォレスト", 0x17302A), c("ブラック", 0x000000),
)

/** 保存値（0 = 標準）を、一覧のどれが選ばれているかに直す。標準なら先頭（defaultIndex） */
fun selectedIndex(colors: List<NamedColor>, saved: Int, defaultIndex: Int = 0): Int =
    if (saved == 0) defaultIndex else colors.indexOfFirst { it.argb == (saved or (0xFF shl 24)) }

/**
 * 名前つきの色を 4 列で並べる。選んだ色は太い枠・チェック・太字の名前で示す。
 * 一覧にない色（ほかの色で作った色）が選ばれているときは、最後に「自分の色」として表示する。
 */
@Composable
fun NamedColorGrid(
    colors: List<NamedColor>,
    saved: Int,
    onSelect: (Int) -> Unit,
    defaultIndex: Int = 0,
    allowCustom: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    var custom by remember { mutableStateOf(false) }
    val sel = selectedIndex(colors, saved, defaultIndex)
    val items = if (sel < 0 && saved != 0) colors + NamedColor("自分の色", saved or (0xFF shl 24)) else colors
    val selIdx = if (sel < 0 && saved != 0) items.lastIndex else sel
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(4).forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEachIndexed { i, nc ->
                    val idx = r * 4 + i
                    val on = idx == selIdx
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(nc.argb) }
                            .semantics { contentDescription = nc.name + if (on) "（選択中）" else "" }
                            .padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            Modifier.size(46.dp).border(3.dp, if (on) scheme.primary else Color.Transparent, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(Color(nc.argb))
                                .border(1.dp, scheme.outlineVariant, CircleShape), contentAlignment = Alignment.Center) {
                                if (on) Icon(Icons.Outlined.Check, null, tint = ColorUtils.onColor(nc.argb))
                            }
                        }
                        Text(nc.name, fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            color = if (on) scheme.onSurface else scheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                repeat(4 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
        if (allowCustom) {
            OutlinedButton(onClick = { custom = true }) { Text("＋ ほかの色を選ぶ…") }
        }
    }
    if (custom) {
        val start = if (saved != 0) saved else colors.getOrNull(defaultIndex)?.argb ?: 0xFF3157D5.toInt()
        HsvDialog(start and 0xFFFFFF, onDone = { custom = false; onSelect(it or (0xFF shl 24)) }, onDismiss = { custom = false })
    }
}

/** 3 つ程度の選択肢を横に並べるボタン（明るさ・文字の大きさ） */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(scheme.onSurface.copy(alpha = 0.07f)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                    .background(if (on) scheme.surface else Color.Transparent)
                    .clickable { onSelect(i) }
                    .semantics { contentDescription = label + if (on) "（選択中）" else "" }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) scheme.onSurface else scheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/**
 * 壁紙の透け具合（0 = 透けない … 80 = よく透ける）。
 * 保存は「不透明度（100 − 透け具合）」のまま。
 */
@Composable
fun TransparencySlider(opacity: Int, onChange: (Int) -> Unit, onDone: () -> Unit = {}) {
    var t by remember(opacity) { mutableFloatStateOf((100 - opacity).coerceIn(0, 80).toFloat()) }
    val scheme = MaterialTheme.colorScheme
    Column {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("壁紙の透け具合", fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(if (t.toInt() == 0) "透けない" else "${t.toInt()}%", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Slider(t, { t = it; onChange(100 - it.toInt()) }, valueRange = 0f..80f, steps = 7, onValueChangeFinished = onDone)
        Row(Modifier.fillMaxWidth()) {
            Text("透けない", fontSize = 11.sp, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("よく透ける", fontSize = 11.sp, color = scheme.onSurfaceVariant)
        }
    }
}

/** 色相・彩度・明るさで色を作る */
@Composable
private fun HsvDialog(initial: Int, onDone: (Int) -> Unit, onDismiss: () -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial or (0xFF shl 24), it) } }
    var h by remember { mutableFloatStateOf(hsv[0]) }
    var s by remember { mutableFloatStateOf(hsv[1]) }
    var v by remember { mutableFloatStateOf(hsv[2]) }
    val color = Color.hsv(h, s, v)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("その他の色") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(12.dp)).background(color))
                Text("色合い", fontSize = 12.sp)
                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(
                    Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) })))
                Slider(h, { h = it }, valueRange = 0f..359f)
                Text("あざやかさ", fontSize = 12.sp)
                Slider(s, { s = it })
                Text("明るさ", fontSize = 12.sp)
                Slider(v, { v = it })
            }
        },
        confirmButton = { TextButton(onClick = { onDone(color.toArgb() and 0xFFFFFF) }) { Text("決定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}
