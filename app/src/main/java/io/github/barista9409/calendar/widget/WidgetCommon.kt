package io.github.barista9409.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalGlanceId
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Spacer
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.barista9409.calendar.MainActivity
import io.github.barista9409.calendar.container
import io.github.barista9409.calendar.model.EventInstance
import io.github.barista9409.calendar.util.ColorUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import androidx.glance.color.ColorProvider as DayNight
import androidx.glance.unit.ColorProvider as Fixed

val TICK_KEY = longPreferencesKey("tick")
/** 日ウィジェットの見出しタップで更新を始めた時刻（この間は左にくるくる） */
val REFRESHING_KEY = longPreferencesKey("refreshing_at")

/** 日ウィジェットの見出しタップ：くるくるを出して読み込み直し、Google との同期も頼む */
class DayRefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val now = System.currentTimeMillis()
        updateAppWidgetState(context, glanceId) { it[REFRESHING_KEY] = now; it[TICK_KEY] = now }
        DayWidget().update(context, glanceId)
        runCatching { (context.applicationContext as io.github.barista9409.calendar.CalendarApp).container.sync.requestAll() }
        // 短すぎると気づかないので、少し出してから消す（同期で届いた変更は、あとで自動で描き直される）
        kotlinx.coroutines.delay(1500)
        updateAppWidgetState(context, glanceId) { it.remove(REFRESHING_KEY); it[TICK_KEY] = System.currentTimeMillis() }
        DayWidget().update(context, glanceId)
    }
}

/** 予定の色の上に載せる文字色（明るい色には濃い文字） */
fun onEventColor(argb: Int) = Fixed(ColorUtils.onColor(argb))

/** ウィジェットの配色（設定の背景色・透明度を反映） */
data class WTheme(
    val bg: ColorProvider, val text: ColorProvider, val sub: ColorProvider, val faint: ColorProvider,
    val line: ColorProvider, val todayBg: ColorProvider, val primary: ColorProvider, val onPrimary: ColorProvider,
    val sunday: ColorProvider, val saturday: ColorProvider, val chipBg: ColorProvider, val nowLine: ColorProvider,
) {
    fun dow(d: LocalDate, strong: Boolean = true): ColorProvider = when (d.dayOfWeek) {
        DayOfWeek.SUNDAY -> sunday
        DayOfWeek.SATURDAY -> saturday
        else -> if (strong) text else sub
    }
}

private data class Palette(
    val text: Color, val sub: Color, val faint: Color, val line: Color, val todayBg: Color, val primary: Color,
    val sunday: Color, val saturday: Color, val chipBg: Color,
)

private val LIGHT = Palette(Color(0xFF1B1D22), Color(0xFF4A4F5A), Color(0xFF8A909B), Color(0x1F1B1D22), Color(0x1A3157D5),
    Color(0xFF3157D5), Color(0xFFC0392B), Color(0xFF2B5FB8), Color(0xFFE8EEFC))
private val DARK = Palette(Color(0xFFF1F3F7), Color(0xFFC3C8D2), Color(0xFF7E8592), Color(0x2EFFFFFF), Color(0x339DB4FF),
    Color(0xFF9DB4FF), Color(0xFFFF8A80), Color(0xFF8AB4F8), Color(0xFF2E3A5E))

fun widgetTheme(context: Context): WTheme {
    val p = context.container.prefs
    val alpha = (p.widgetOpacity.coerceIn(0, 100) / 100f)
    val custom = p.widgetColor
    fun dn(l: Color, d: Color) = DayNight(l, d)
    return if (custom == 0) {
        WTheme(
            bg = dn(Color.White.copy(alpha = alpha), Color(0xFF1F242C).copy(alpha = alpha)),
            text = dn(LIGHT.text, DARK.text), sub = dn(LIGHT.sub, DARK.sub), faint = dn(LIGHT.faint, DARK.faint),
            line = dn(LIGHT.line, DARK.line), todayBg = dn(LIGHT.todayBg, DARK.todayBg), primary = dn(LIGHT.primary, DARK.primary),
            onPrimary = dn(Color.White, Color(0xFF0B2161)), sunday = dn(LIGHT.sunday, DARK.sunday),
            saturday = dn(LIGHT.saturday, DARK.saturday), chipBg = dn(LIGHT.chipBg, DARK.chipBg), nowLine = Fixed(Color(0xFFD93025)),
        )
    } else {
        val bgColor = Color(custom or (0xFF shl 24))
        val pal = if (ColorUtils.onColor(custom) == Color.White) DARK else LIGHT
        WTheme(
            bg = Fixed(bgColor.copy(alpha = alpha)), text = Fixed(pal.text), sub = Fixed(pal.sub), faint = Fixed(pal.faint),
            line = Fixed(pal.line), todayBg = Fixed(pal.todayBg), primary = Fixed(pal.primary),
            onPrimary = Fixed(if (pal == DARK) Color(0xFF0B2161) else Color.White),
            sunday = Fixed(pal.sunday), saturday = Fixed(pal.saturday), chipBg = Fixed(pal.chipBg), nowLine = Fixed(Color(0xFFD93025)),
        )
    }
}

object WidgetData {
    /** 表示中のカレンダー（設定で非表示・アカウント絞り込みを反映）の予定を日付ごとに */
    suspend fun load(context: Context, from: LocalDate, toExclusive: LocalDate): Map<LocalDate, List<EventInstance>>? {
        val c = context.container
        if (!c.repository.hasPermission()) return null
        val zone = ZoneId.systemDefault()
        val hidden = c.prefs.hiddenCalendars.value
        val acc = c.prefs.accountFilter.value
        val cals = c.repository.calendars().filter { it.id !in hidden && (acc == null || it.accountName == acc) }.map { it.id }.toSet()
        val events = c.repository.instances(
            from.atStartOfDay(zone).toInstant().toEpochMilli(), toExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
        ).filter { it.calendarId in cals }
        val map = HashMap<LocalDate, MutableList<EventInstance>>()
        for (e in events) {
            var d = maxOf(e.startDate(zone), from)
            val last = minOf(e.lastDate(zone), toExclusive.minusDays(1))
            while (!d.isAfter(last)) { map.getOrPut(d) { mutableListOf() }.add(e); d = d.plusDays(1) }
        }
        return map.mapValues { (_, v) -> v.sortedWith(compareByDescending<EventInstance> { it.isLong }.thenBy { it.begin }) }
    }
}

fun openDateAction(context: Context, date: LocalDate) = actionStartActivity(
    Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_DATE, date.toString())
        .setData(android.net.Uri.parse("calendar://date/$date"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
)

/** 予定をタップしたとき：アプリでその予定を全画面表示 */
fun openEventAction(context: Context, e: EventInstance) = actionStartActivity(
    Intent(context, MainActivity::class.java)
        .putExtra(MainActivity.EXTRA_EVENT_ID, e.eventId).putExtra(MainActivity.EXTRA_EVENT_BEGIN, e.begin)
        .setData(android.net.Uri.parse("calendar://event/${e.eventId}/${e.begin}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
)

/** 標準ウィジェット（RemoteViews）用の色。設定の背景色・透明度とダークモードを反映 */
data class WColors(
    val bg: Int, val text: Int, val sub: Int, val faint: Int, val line: Int, val todayBg: Int,
    val primary: Int, val onPrimary: Int, val sunday: Int, val saturday: Int, val chipBg: Int,
)

fun widgetColors(context: Context): WColors {
    val p = context.container.prefs
    val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val alpha = p.widgetOpacity.coerceIn(0, 100) / 100f
    val dark = if (p.widgetColor == 0) night else ColorUtils.onColor(p.widgetColor) == Color.White
    val pal = if (dark) DARK else LIGHT
    fun Color.c(): Int = android.graphics.Color.argb((this.alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())
    val base = if (p.widgetColor != 0) Color(p.widgetColor or (0xFF shl 24)) else if (dark) Color(0xFF1F242C) else Color.White
    return WColors(
        bg = android.graphics.Color.argb((alpha * 255).toInt(), (base.red * 255).toInt(), (base.green * 255).toInt(), (base.blue * 255).toInt()),
        text = pal.text.c(), sub = pal.sub.c(), faint = pal.faint.c(), line = pal.line.c(), todayBg = pal.todayBg.c(),
        primary = pal.primary.c(), onPrimary = if (dark) 0xFF0B2161.toInt() else android.graphics.Color.WHITE,
        sunday = pal.sunday.c(), saturday = pal.saturday.c(), chipBg = pal.chipBg.c(),
    )
}

fun newEventAction(context: Context) = actionStartActivity(
    Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_NEW_EVENT, true)
        .setData(android.net.Uri.parse("calendar://new"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
)

object WidgetUpdater {

    suspend fun updateAll(context: Context) {
        val mgr = GlanceAppWidgetManager(context)
        runCatching { MonthWidget.updateAll(context) }
        for (w in listOf(DayWidget())) {
            mgr.getGlanceIds(w.javaClass).forEach { id ->
                updateAppWidgetState(context, id) { it[TICK_KEY] = System.currentTimeMillis() }
                w.update(context, id)
            }
        }
    }

    fun updateAllAsync(context: Context) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { runCatching { updateAll(context.applicationContext) } }
    }
}

/** アプリを更新したあと、置いてあるウィジェットを自動で描き直す（置き直し不要にする） */
class AppUpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val app = context.applicationContext
                MonthWidget.allIds(app).forEach { id ->
                    MonthWidget.update(app, id, scroll = true)
                    android.appwidget.AppWidgetManager.getInstance(app).notifyAppWidgetViewDataChanged(id, io.github.barista9409.calendar.R.id.list)
                }
                WidgetUpdater.updateAll(app)
            } finally { pending.finish() }
        }
    }
}

/** カレンダーの変更・日付変更でウィジェットを更新 */
class CalendarChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try { WidgetUpdater.updateAll(context.applicationContext) } finally { pending.finish() }
        }
    }
}

/** 見出し（真ん中）：「本日：…」と右端の＋。日ウィジェット用 */
@Composable
fun CenteredHeader(context: Context, t: WTheme, title: String, refreshing: Boolean = false) {
    Row(GlanceModifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        // 左：更新中だけくるくる（＋と同じ幅で、見出しを真ん中にそろえる）
        Box(GlanceModifier.size(44.dp), contentAlignment = Alignment.Center) {
            if (refreshing) androidx.glance.appwidget.CircularProgressIndicator(GlanceModifier.size(22.dp), color = t.primary)
        }
        // 見出しをタップすると更新（月ウィジェットと同じ）
        Text(title, GlanceModifier.defaultWeight().clickable(actionRunCallback<DayRefreshAction>()),
            style = TextStyle(color = t.text, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                textAlign = androidx.glance.text.TextAlign.Center), maxLines = 1)
        IconButton(t, io.github.barista9409.calendar.R.drawable.ic_w_add, "予定を追加", newEventAction(context), filled = true)
    }
}

@Composable
private fun IconButton(t: WTheme, icon: Int, label: String, action: Action, filled: Boolean = false) {
    Box(GlanceModifier.size(44.dp).padding(2.dp).clickable(action), contentAlignment = Alignment.Center) {
        Box(
            GlanceModifier.size(38.dp).cornerRadius(19.dp).background(if (filled) t.primary else t.chipBg),
            contentAlignment = Alignment.Center,
        ) {
            androidx.glance.Image(
                androidx.glance.ImageProvider(icon), contentDescription = label, modifier = GlanceModifier.size(24.dp),
                colorFilter = androidx.glance.ColorFilter.tint(if (filled) t.onPrimary else t.primary),
            )
        }
    }
}

