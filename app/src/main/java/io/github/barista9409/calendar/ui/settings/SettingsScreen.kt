package io.github.barista9409.calendar.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.barista9409.calendar.container
import io.github.barista9409.calendar.widget.WidgetUpdater
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.Check
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.border
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.barista9409.calendar.BuildConfig
import io.github.barista9409.calendar.data.Prefs

/** 設定の各画面 */
private enum class Page(val title: String) {
    TOP("設定"), APPEARANCE("色とテーマ"), DISPLAY("カレンダーの表示"), NOTIFY("通知"),
    ATTACH("添付ファイル"), ACCOUNTS("アカウント"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenUpdate: () -> Unit = {}) {
    var page by remember { mutableStateOf(Page.TOP) }
    val snackbar = remember { SnackbarHostState() }
    BackHandler(enabled = page != Page.TOP) { page = Page.TOP }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(page.title) }, navigationIcon = {
                IconButton(onClick = { if (page == Page.TOP) onBack() else page = Page.TOP }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "戻る")
                }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (page) {
                Page.TOP -> TopPage(onOpen = { page = it }, onOpenUpdate = onOpenUpdate)
                Page.APPEARANCE -> AppearancePage()
                Page.DISPLAY -> DisplayPage()
                Page.NOTIFY -> NotifyPage()
                Page.ATTACH -> AttachPage(snackbar)
                Page.ACCOUNTS -> AccountsPage()
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------------- トップ：種類ごとに分類 ----------------

@Composable
private fun TopPage(onOpen: (Page) -> Unit, onOpenUpdate: () -> Unit) {
    Group("見た目") {
        NavRow(Icons.Outlined.Palette, Color(0xFF2443A8), "色とテーマ", "明るさ・メインの色・背景の色と透け具合") { onOpen(Page.APPEARANCE) }
        NavRow(Icons.Outlined.CalendarMonth, Color(0xFF1E6B47), "カレンダーの表示", "週表示の列の幅") { onOpen(Page.DISPLAY) }
    }
    Group("予定") {
        NavRow(Icons.Outlined.NotificationsNone, Color(0xFF9A3412), "通知", "このアプリで通知する・再通知") { onOpen(Page.NOTIFY) }
        NavRow(Icons.Outlined.AttachFile, Color(0xFF6B21A8), "添付ファイル", "Google ドライブの保存先・一時ファイル") { onOpen(Page.ATTACH) }
    }
    // ウィジェットの見た目は、ホーム画面でウィジェットを長押し →「設定」だけで変える
    Group("アカウントとアプリ") {
        NavRow(Icons.Outlined.Person, Color(0xFF3F444E), "アカウント", "追加・表示するアカウント") { onOpen(Page.ACCOUNTS) }
        NavRow(Icons.Outlined.SystemUpdate, Color(0xFF3F444E), "アップデート", "バージョン ${BuildConfig.VERSION_NAME}") { onOpenUpdate() }
        var share by remember { mutableStateOf(false) }
        NavRow(Icons.Outlined.Share, Color(0xFF3F444E), "このアプリを共有", "ダウンロード先と説明をコピー・送る") { share = true }
        if (share) io.github.barista9409.calendar.ui.ShareAppDialog(onDismiss = { share = false })
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp))
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column { content() }
        }
    }
}

@Composable
private fun NavRow(icon: ImageVector, tint: Color, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint)
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, fontSize = 16.sp)
            Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------- 色とテーマ ----------------

@Composable
private fun AppearancePage() {
    val c = LocalContext.current.container
    val theme by c.prefs.appTheme.collectAsState()
    val myTheme by c.prefs.myTheme.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val dark = when (theme.mode) { 1 -> false; 2 -> true; else -> systemDark }
    val bgs = if (dark) DARK_BG_COLORS else LIGHT_BG_COLORS
    // 今の明るさに合わない背景色は「標準」として表示（実際の表示と同じ）
    fun shownBg(v: Int) = if (io.github.barista9409.calendar.ui.theme.bgFitsMode(v, dark)) v else 0
    fun set(t: Prefs.AppTheme) = c.prefs.setAppTheme(t)
    var open by remember { mutableIntStateOf(-1) }

    AppearancePreview(dark)

    // おすすめのテーマ
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column {
            Text("おすすめのテーマ", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Hint("選ぶと下の色がまとめて変わります。そこから好みに直せます")
        }
        val cards = THEME_PRESETS.map { Triple(it.name, it.mood, it.theme) } +
            listOfNotNull(myTheme?.let { Triple("マイテーマ", "保存した組み合わせ", it.copy(preset = MY_THEME)) })
        cards.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (name, mood, t) ->
                    PresetCard(name, mood, t, selected = theme.preset == t.preset, modifier = Modifier.weight(1f)) { set(t); open = -1 }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

    // 細かく調整する
    val base = when (theme.preset) {
        MY_THEME -> myTheme
        in THEME_PRESETS.indices -> THEME_PRESETS[theme.preset].theme
        else -> null
    }
    val baseName = when (theme.preset) { MY_THEME -> "マイテーマ"; in THEME_PRESETS.indices -> THEME_PRESETS[theme.preset].name; else -> null }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column {
            Text("細かく調整する", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            val n = base?.let { changedAreas(it, theme) } ?: 0
            Hint(when {
                baseName == null -> "好きな色を自由に組み合わせられます"
                n == 0 -> "今はテーマ「$baseName」のままです"
                else -> "テーマ「$baseName」から $n か所を変更中"
            })
        }
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column {
                val modeNames = listOf("端末に合わせる", "ライト", "ダーク")
                AdjustRow(0, open, { open = it }, null, "明るさ", modeNames[theme.mode]) {
                    Segmented(modeNames, theme.mode) { set(theme.copy(mode = it)) }
                    Hint("「端末に合わせる」は、スマホのダークモードの設定に合わせて自動で切り替えます。")
                }
                AdjustRow(1, open, { open = it }, theme.accent.takeIf { it != 0 } ?: MaterialTheme.colorScheme.primary.toArgbInt(),
                    "メインの色", "ボタン・今日の印・選んだ日の枠") {
                    NamedColorGrid(ACCENT_COLORS, theme.accent, onSelect = { set(theme.copy(accent = it)) })
                }
                AdjustRow(2, open, { open = it }, shownBg(theme.calendarBg).takeIf { it != 0 } ?: bgs[0].argb,
                    "カレンダーの背景", "月・週・日の表示" + transparencyLabel(theme.calendarAlpha)) {
                    NamedColorGrid(bgs, shownBg(theme.calendarBg), onSelect = { set(theme.copy(calendarBg = it)) })
                    TransparencySlider(theme.calendarAlpha, { set(theme.copy(calendarAlpha = it)) })
                }
                AdjustRow(3, open, { open = it }, shownBg(theme.popupBg).takeIf { it != 0 } ?: bgs[0].argb,
                    "予定の詳細・入力画面の背景", "下から出る詳細・予定の入力" + transparencyLabel(theme.popupAlpha)) {
                    NamedColorGrid(bgs, shownBg(theme.popupBg), onSelect = { set(theme.copy(popupBg = it)) })
                    TransparencySlider(theme.popupAlpha, { set(theme.copy(popupAlpha = it)) })
                    Hint("日付・時刻を選ぶ画面も、この色に合わせます。")
                }
                val menuShown = if (theme.menuBg == 0) shownBg(theme.popupBg) else shownBg(theme.menuBg)
                AdjustRow(4, open, { open = it }, menuShown.takeIf { it != 0 } ?: bgs[0].argb,
                    "メニューの背景", "左から出るメニュー" + if (theme.menuBg == 0) "（予定の詳細と同じ）" else transparencyLabel(theme.menuAlpha), last = true) {
                    NamedColorGrid(bgs, menuShown, onSelect = { set(theme.copy(menuBg = it)) })
                    TransparencySlider(if (theme.menuBg == 0) theme.popupAlpha else theme.menuAlpha,
                        { set(theme.copy(menuBg = if (theme.menuBg == 0) (menuShown.takeIf { m -> m != 0 } ?: bgs[0].argb) else theme.menuBg, menuAlpha = it)) })
                    if (theme.menuBg != 0) TextButton(onClick = { set(theme.copy(menuBg = 0, menuAlpha = 100)) }) { Text("予定の詳細と同じ色に戻す") }
                }
            }
        }
    }
    var saved by remember { mutableStateOf(false) }
    TextButton(onClick = { c.prefs.saveMyTheme(theme.copy(preset = MY_THEME)); set(theme.copy(preset = MY_THEME)); saved = true },
        modifier = Modifier.fillMaxWidth()) {
        Text(if (saved) "マイテーマに保存しました" else if (myTheme != null) "今の色でマイテーマを上書き保存" else "＋ 今の色を「マイテーマ」として保存")
    }
    Hint("透け具合を上げると、ホーム画面の壁紙が透けて見えます。")
}

private fun transparencyLabel(opacity: Int) = if (opacity >= 100) "" else "・透け ${100 - opacity}%"

private fun Color.toArgbInt(): Int = android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

/** たたんだ 1 行（今の色の丸・名前・説明）。押すと広がって色を選べる */
@Composable
private fun AdjustRow(
    index: Int, open: Int, onOpen: (Int) -> Unit, swatch: Int?, title: String, sub: String, last: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val expanded = open == index
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(if (expanded) -1 else index) }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (swatch != null) Box(Modifier.size(28.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(swatch))
            .then(Modifier.padding(0.dp)))
        else Icon(Icons.Outlined.Palette, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, fontSize = 15.sp)
            Text(sub, fontSize = 12.sp, color = scheme.onSurfaceVariant)
        }
        Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = if (expanded) "たたむ" else "広げる",
            tint = scheme.onSurfaceVariant)
    }
    if (expanded) Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        content()
    }
    if (!last) HorizontalDivider(color = scheme.outlineVariant)
}

/** おすすめテーマのカード：左がメニュー、真ん中がカレンダー（メインの色の印）、右が予定の詳細の色 */
@Composable
private fun PresetCard(name: String, mood: String, t: Prefs.AppTheme, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val menu = Color(if (t.menuBg != 0) t.menuBg else t.popupBg)
    Column(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceContainerHigh)
            .border(if (selected) 3.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "テーマ $name" + if (selected) "（選択中）" else "" }
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(10.dp))) {
            Box(Modifier.weight(0.26f).fillMaxHeight().background(menu))
            Column(
                Modifier.weight(0.44f).fillMaxHeight().background(
                    if (t.calendarAlpha < 100) androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFE9A23B), Color(0xFF3A4453)))
                    else androidx.compose.ui.graphics.SolidColor(Color(t.calendarBg))),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.size(14.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(t.accent)))
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth(0.6f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(t.accent).copy(alpha = 0.5f)))
            }
            Box(Modifier.weight(0.30f).fillMaxHeight().background(Color(t.popupBg)))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(mood, fontSize = 10.sp, color = scheme.onSurfaceVariant)
            }
            if (selected) Icon(Icons.Outlined.Check, null, tint = scheme.primary)
        }
    }
}

/** 見本：メニュー・カレンダー・予定の詳細の 3 か所を並べる */
@Composable
private fun AppearancePreview(dark: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val popup = io.github.barista9409.calendar.ui.theme.LocalPopupScheme.current ?: scheme
    val menu = io.github.barista9409.calendar.ui.theme.LocalMenuScheme.current ?: popup
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(18.dp))
                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFE9A23B), Color(0xFF3A4453))))
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.width(100.dp).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(menu.surface).padding(7.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("メニュー", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = menu.onSurface)
                Text("表示するアカウント", fontSize = 8.sp, color = menu.onSurface)
                Text("家族", fontSize = 8.sp, color = menu.onPrimaryContainer,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(menu.primaryContainer).padding(horizontal = 6.dp, vertical = 1.dp))
                Text("設定", fontSize = 8.sp, color = menu.onSurface)
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(10.dp)).background(scheme.surface).padding(7.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("11月", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
                    Row {
                        listOf("15", "16", "17", "18", "19", "20", "21").forEachIndexed { i, d ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Text(d, fontSize = 8.sp, color = if (i == 1) scheme.onPrimary else scheme.onSurface,
                                    fontWeight = if (i == 1) FontWeight.Bold else FontWeight.Normal,
                                    modifier = if (i == 1) Modifier.clip(RoundedCornerShape(7.dp)).background(scheme.primary).padding(horizontal = 3.dp) else Modifier)
                            }
                        }
                    }
                    Text("習い事", fontSize = 7.sp, color = Color.White, maxLines = 1,
                        modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(Color(0xFFAD1457)).padding(horizontal = 3.dp))
                }
                Column(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(10.dp)).background(popup.surface).padding(7.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("予定の詳細", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = popup.onSurface)
                    Text("11月16日 15:30〜", fontSize = 8.sp, color = popup.onSurface)
                    Text("保存", fontSize = 8.sp, color = popup.onPrimary,
                        modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(popup.primary).padding(horizontal = 8.dp, vertical = 1.dp))
                }
            }
        }
        Hint("見本：変えた色はここにすぐ反映されます" + if (dark) "（ダーク）" else "（ライト）")
    }
}

// ---------------- カレンダーの表示 ----------------

@Composable
private fun DisplayPage() {
    val c = LocalContext.current.container
    val weekAuto by c.prefs.weekAutoWidth.collectAsState()
    Section("週表示") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("予定が重なる日を広く表示", fontSize = 15.sp)
                Hint("同じ時間に予定が重なっている日ほど列を広くします")
            }
            Switch(weekAuto, { c.prefs.setWeekAutoWidth(it) })
        }
    }
}

// ---------------- 通知 ----------------

@Composable
private fun NotifyPage() {
    val context = LocalContext.current
    val c = context.container
    val scheme = MaterialTheme.colorScheme
    var reminders by remember { mutableStateOf(c.prefs.appReminders) }
    var style by remember { mutableIntStateOf(c.prefs.reminderStyle) }
    Section("通知") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("このアプリで予定を通知する", fontSize = 15.sp)
                Hint("通知の時刻になると、このアプリから通知します（10分後に再通知・地図）")
            }
            Switch(reminders, { reminders = it; c.prefs.appReminders = it })
        }
        if (reminders) Text("二重に通知されないよう、公式の Google カレンダーアプリの通知はオフにしてください。", fontSize = 12.sp, color = scheme.error)
        else Hint("オフのときは、このアプリからは通知しません（公式の Google カレンダーアプリの通知だけになります）。")
    }
    Section("通知の種類", "マナーモードのときにどう知らせるかを選べます") {
        io.github.barista9409.calendar.notify.ReminderStyle.entries.forEachIndexed { i, st ->
            val on = i == style
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .clickable { style = i; c.prefs.reminderStyle = i }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.RadioButton(selected = on, onClick = { style = i; c.prefs.reminderStyle = i })
                Column(Modifier.weight(1f)) {
                    Text(st.label + if (i == 0) "（おすすめ）" else "", fontSize = 15.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                    Hint(st.description)
                }
            }
        }
    }
    Section("バイブと鳴らし方") {
        var length by remember { mutableIntStateOf(c.prefs.vibrateLength) }
        var repeat by remember { mutableStateOf(c.prefs.alertRepeat) }
        val vibrates = io.github.barista9409.calendar.notify.ReminderStyle.of(style).vibrate
        Text("バイブの長さ", fontSize = 15.sp)
        Segmented(listOf("短い", "普通", "長い"), length) { length = it; c.prefs.vibrateLength = it }
        Hint(if (vibrates) "短い：約0.5秒 ／ 普通：約3秒 ／ 長い：約7秒" else "「表示のみ」ではバイブは鳴りません")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("止めるまで鳴らし続ける", fontSize = 15.sp)
                Hint("通知を消す・開く・［止める］を押すまで、くり返し知らせます（最長5分）")
            }
            Switch(repeat, { repeat = it; c.prefs.alertRepeat = it })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                io.github.barista9409.calendar.notify.ReminderReceiver.showTest(context)
            }) { Text("テスト通知を送る") }
            TextButton(onClick = {
                val st = io.github.barista9409.calendar.notify.ReminderStyle.of(style)
                io.github.barista9409.calendar.notify.ReminderReceiver.ensureChannel(context, st)
                runCatching {
                    context.startActivity(Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, st.channelId))
                }
            }) { Text("音を変える…") }
        }
        Hint("「テスト通知を送る」で、選んだ種類の鳴り方をすぐ確かめられます。着信音の種類は「音を変える…」から、スマホの設定で変えられます。")
    }
}

// ---------------- 添付ファイル ----------------

@Composable
private fun AttachPage(snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val roots by c.prefs.attachRoots.collectAsState()
    val linked by c.prefs.linkedAccounts.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    val cache by produceState(0L to 0, refresh) { value = c.attachments.store.usage() }
    Section("保存先（Google ドライブ）") {
        Text("マイドライブ ／ ${io.github.barista9409.calendar.google.AttachmentService.ROOT_NAME}", fontSize = 15.sp)
        Hint("最初に添付したとき、アプリがこのフォルダを作ります。ドライブ上で好きな場所へ移動したり、名前を変えたりしても使い続けられます。")
        Hint("添付したファイルは「リンクを知っている人なら閲覧可」になり、予定の説明にリンクが入ります。家族も Google カレンダーアプリやこのアプリから開けます。")
        if (linked.isEmpty()) Hint("添付ファイルを使うには、メニューの「カレンダーと同期」から Google と連携してください。")
        linked.sorted().forEach { acc ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(acc, fontSize = 13.sp, color = scheme.onSurfaceVariant)
                    Text(if (roots[acc] != null) "フォルダ作成済み" else "まだ添付していません", fontSize = 14.sp)
                }
                OutlinedButton(onClick = {
                    scope.launch {
                        val url = runCatching { c.attachments.rootFolder(acc); c.attachments.folderUrl(acc) }.getOrNull()
                        if (url == null) snackbar.showSnackbar("フォルダを開けませんでした（Google との連携を確認してください）")
                        else runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
                    }
                }) { Text("ドライブで開く") }
            }
        }
    }
    Section("表示") {
        val preview by c.prefs.attachPreview.collectAsState()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("画像・PDF のプレビューを表示", fontSize = 15.sp)
                Hint("予定の詳細で、添付した画像や PDF の全体を表示します。オフにするとファイル名だけになります。")
            }
            Switch(preview, { c.prefs.setAttachPreview(it) })
        }
    }
    Section("一時ファイル") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${cache.second} 件・${"%.1f".format(cache.first / 1_048_576.0)} MB", fontSize = 15.sp)
                Hint("開いた添付ファイルを、次にすぐ開けるよう端末に一時保存しています。")
            }
            TextButton(onClick = { scope.launch { c.attachments.store.clear(); refresh++; snackbar.showSnackbar("一時ファイルを削除しました") } }) { Text("削除") }
        }
    }
}

// ---------------- アカウント ----------------

@Composable
private fun AccountsPage() {
    val context = LocalContext.current
    val c = context.container
    val scheme = MaterialTheme.colorScheme
    val linked by c.prefs.linkedAccounts.collectAsState()
    val hiddenAccounts by c.prefs.hiddenAccounts.collectAsState()
    val accounts by produceState(emptyList<Pair<String, Boolean>>(), hiddenAccounts) {
        value = c.repository.calendars(includeHiddenAccounts = true).groupBy { it.accountName }.map { (a, l) -> a to l.any { it.isGoogle } }
    }
    Section("このアプリで表示するアカウント") {
        accounts.forEach { (acc, isGoogle) ->
            var confirm by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(acc, fontSize = 15.sp)
                    Hint(buildString {
                        append(if (acc in hiddenAccounts) "このアプリでは非表示" else "表示中")
                        if (isGoogle) append(if (acc in linked) "・Google と連携済み" else "・未連携")
                    })
                }
                Switch(acc !in hiddenAccounts, { c.prefs.setAccountHidden(acc, !it) })
                TextButton(onClick = { confirm = true }) { Text("削除", color = scheme.error) }
            }
            if (confirm) {
                AlertDialog(
                    onDismissRequest = { confirm = false },
                    title = { Text("「$acc」を端末から削除") },
                    text = { Text("アカウントの削除は Android の設定で行います（Google の仕様で、アプリから直接は削除できません）。開いた画面の「アカウントを削除」を押してください。\nこのアプリで見えなくするだけなら、スイッチをオフにしてください。") },
                    confirmButton = {
                        TextButton(onClick = {
                            confirm = false
                            val i = Intent("android.settings.ACCOUNT_SYNC_SETTINGS").putExtra("account", android.accounts.Account(acc, "com.google"))
                            runCatching { context.startActivity(i) }.onFailure { context.startActivity(Intent(android.provider.Settings.ACTION_SYNC_SETTINGS)) }
                        }) { Text("設定を開く") }
                    },
                    dismissButton = { TextButton(onClick = { confirm = false }) { Text("キャンセル") } },
                )
            }
        }
        Button(onClick = { context.startActivity(io.github.barista9409.calendar.ui.addAccountIntent()) }) { Text("＋ アカウントを追加") }
        Hint("追加したアカウントは、端末のカレンダー同期が終わると表示されます。")
    }
}

// ---------------- ウィジェットの見た目（アプリの設定・ウィジェットの長押しで共通） ----------------

private val FONT_SIZES = listOf(8, 10, 12)
private val MAX_EVENT_OPTIONS = listOf(0, 2, 3, 4, 5, 6)

/**
 * ウィジェットの見た目。appWidgetId が月ウィジェットのときは、そのウィジェットの大きさで 1 日に表示できる件数を出す。
 */
@Composable
fun WidgetAppearance(appWidgetId: Int?) {
    val context = LocalContext.current
    val prefs = context.container.prefs
    var font by remember { mutableIntStateOf(prefs.widgetFontSize) }
    var color by remember { mutableIntStateOf(prefs.widgetColor) }
    var opacity by remember { mutableIntStateOf(prefs.widgetOpacity) }
    val night = isSystemInDarkTheme()
    val fontIdx = FONT_SIZES.indexOf(font).let { if (it >= 0) it else FONT_SIZES.indexOfFirst { s -> s >= font }.coerceAtLeast(0) }
    val isMonth = appWidgetId != null && io.github.barista9409.calendar.widget.MonthWidget.allIds(context).contains(appWidgetId)
    var maxEvents by remember { mutableIntStateOf(prefs.widgetMaxEvents) }
    val perDay = if (isMonth) remember(font, maxEvents) { io.github.barista9409.calendar.widget.MonthWidget.spec(context, appWidgetId!!).capacity } else null
    fun apply() = io.github.barista9409.calendar.widget.WidgetUpdater.updateAllAsync(context.applicationContext)

    Section("予定の文字の大きさ", "小さくすると、1 日に表示できる予定が増えます") {
        Segmented(listOf("小さめ", "標準", "大きめ"), fontIdx) { i -> font = FONT_SIZES[i]; prefs.widgetFontSize = font; apply() }
    }
    Section("月ウィジェット：1 日に表示する件数", "それより多い日は「•••」で表示します") {
        Segmented(MAX_EVENT_OPTIONS.map { if (it == 0) "自動" else "${it}件" }, MAX_EVENT_OPTIONS.indexOf(maxEvents).coerceAtLeast(0)) { i ->
            maxEvents = MAX_EVENT_OPTIONS[i]; prefs.widgetMaxEvents = maxEvents; apply()
        }
        if (maxEvents == 0) {
            if (perDay != null) Text("このウィジェット（いまの大きさ）では、1 日に最大 $perDay 件まで表示", fontSize = 14.sp)
            Hint("「自動」は、ウィジェットの大きさに合わせて入るだけ表示します。縦に広げると件数が増えます。")
        } else {
            Text("1 日に最大 $maxEvents 件まで表示", fontSize = 14.sp)
            Hint("件数に合わせて 1 週の高さをそろえます。ウィジェットに入りきらない週は、上下にスクロールして見られます。")
        }
    }
    Section("背景の色") {
        // 標準（0）は端末の明るさに合わせた色（ホワイトかチャコール）として表示
        NamedColorGrid(WIDGET_BG_COLORS, color, defaultIndex = if (night) 4 else 0,
            onSelect = { color = it; prefs.widgetColor = it; apply() })
        TransparencySlider(opacity, { opacity = it; prefs.widgetOpacity = it }, onDone = { apply() })
    }
    Hint("この設定は、置いてあるすべてのウィジェット（月・日）に反映されます。ホーム画面でウィジェットを長押し →「設定」からも開けます。")
}

@Composable
private fun Section(title: String, sub: String? = null, content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (sub != null) Hint(sub)
            }
            content()
        }
    }
}

@Composable
private fun Hint(text: String) = Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
