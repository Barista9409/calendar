package io.github.barista9409.calendar.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.SizeF
import android.util.TypedValue
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.compose.ui.graphics.toArgb
import io.github.barista9409.calendar.MainActivity
import io.github.barista9409.calendar.R
import io.github.barista9409.calendar.container
import io.github.barista9409.calendar.model.EventInstance
import io.github.barista9409.calendar.util.ColorUtils
import io.github.barista9409.calendar.util.DateUtils
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/**
 * 月ウィジェット（Android 標準の仕組みで作成）。
 * 一覧は必要な週だけを読み込むので、今月から 3 か月分を週ごとに上下スクロールできる。
 * 見た目は Google カレンダーと同じく、複数日の予定は帯でつながり、入りきらない日は「+N」。
 */
class MonthWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { MonthWidget.update(context, it, scroll = true) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        MonthWidget.update(context, id, scroll = false)
        manager.notifyAppWidgetViewDataChanged(id, R.id.list)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val e = MonthWidget.store(context).edit()
        ids.forEach { e.remove("offset_$it") }
        e.apply()
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == MonthWidget.ACTION_NAV) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                // スクロールが終わるまでアプリが止められないようにする（止まるとスクロールが実行されず、表示がずれる）
                val pending = goAsync()
                MonthWidget.nav(context, id, intent.getIntExtra("delta", 0))
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ pending.finish() }, 2500)
            }
            return
        }
        if (intent.action == MonthWidget.ACTION_REFRESH) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                // 見出しをタップ：今月に戻し、「同期中」の印を付けて描き直す（くるくると「同期しています…」）。
                // 同期はジョブで頼み、終わるまで待ってから印を消して描き直す
                MonthWidget.store(context).edit().putInt("offset_$id", 0).commit()
                WidgetSyncState.startMonth(context, id)
                val pending = goAsync()
                MonthWidget.update(context, id, scroll = true)
                AppWidgetManager.getInstance(context).notifyAppWidgetViewDataChanged(id, R.id.list)
                WidgetSyncJob.start(context)
                // スクロールが終わるまで待つ
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ pending.finish() }, 7000)
            }
            return
        }
        super.onReceive(context, intent)
    }
}

class MonthWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        MonthWeekFactory(applicationContext, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0))
}

private class MonthWeekFactory(private val context: Context, private val id: Int) : RemoteViewsService.RemoteViewsFactory {
    private var start: LocalDate = LocalDate.now()
    private var count = 0
    private var today: LocalDate = LocalDate.now()
    private var data: Map<LocalDate, List<EventInstance>> = emptyMap()
    private var holidays: Set<LocalDate> = emptySet()
    private var spec: MonthWidget.Spec? = null
    private var first = true

    override fun onCreate() {}

    override fun onDataSetChanged() {
        today = LocalDate.now()
        start = MonthWidget.listStart(today)
        count = MonthWidget.listWeeks(today)
        spec = MonthWidget.spec(context, id)
        // ランチャーからの呼び出しなので、自分の権限でカレンダーを読む
        val token = Binder.clearCallingIdentity()
        try {
            data = runCatching { runBlocking { WidgetData.load(context, start, start.plusWeeks(count.toLong())) } }.getOrNull() ?: emptyMap()
            holidays = runCatching { runBlocking { MonthWidget.holidays(context, data) } }.getOrDefault(emptySet())
        } finally {
            Binder.restoreCallingIdentity(token)
        }
        if (first) { first = false; MonthWidget.scrollToAnchor(context, id, initial = true) }
    }

    override fun getCount() = count
    override fun getViewAt(position: Int): RemoteViews {
        val s = spec ?: MonthWidget.spec(context, id).also { spec = it }
        return MonthWidget.weekViews(context, s, start.plusWeeks(position.toLong()), today, data, holidays)
    }
    override fun getLoadingView(): RemoteViews {
        val s = spec ?: MonthWidget.spec(context, id).also { spec = it }
        return MonthWidget.emptyWeek(context, s)
    }
    override fun getViewTypeCount() = 1
    override fun getItemId(position: Int) = position.toLong()
    override fun hasStableIds() = true
    override fun onDestroy() {}
}

object MonthWidget {
    const val ACTION_NAV = "io.github.barista9409.calendar.MONTH_WIDGET_NAV"
    /** 同期中の見出し */
    const val SYNCING_TITLE = "同期しています…"
    const val ACTION_REFRESH = "io.github.barista9409.calendar.MONTH_WIDGET_REFRESH"
    /** 表示する月の数（今月から。過去は出さない：少ないほど読み込みが軽い） */
    const val MONTHS = 3L

    fun store(context: Context) = context.getSharedPreferences("month_widget", Context.MODE_PRIVATE)
    private fun offset(context: Context, id: Int) = store(context).getInt("offset_$id", 0)
    private fun anchor(context: Context, id: Int): YearMonth = YearMonth.now().plusMonths(offset(context, id).toLong())

    /** 一覧の先頭（今月の最初の週）と週の数（今月から MONTHS か月分の最後の週まで） */
    fun listStart(today: LocalDate): LocalDate = DateUtils.startOfWeek(today.withDayOfMonth(1))
    fun listWeeks(today: LocalDate): Int {
        val lastDay = today.withDayOfMonth(1).plusMonths(MONTHS).minusDays(1)
        return (ChronoUnit.WEEKS.between(listStart(today), DateUtils.startOfWeek(lastDay)) + 1).toInt()
    }
    private fun indexOf(month: YearMonth, today: LocalDate): Int =
        ChronoUnit.WEEKS.between(listStart(today), DateUtils.startOfWeek(month.atDay(1))).toInt()
    private fun monthWeeks(month: YearMonth): Int {
        val s = DateUtils.startOfWeek(month.atDay(1))
        return (ChronoUnit.DAYS.between(s, month.atEndOfMonth()) / 7 + 1).toInt()
    }

    private val DAY_IDS = intArrayOf(R.id.day0, R.id.day1, R.id.day2, R.id.day3, R.id.day4, R.id.day5, R.id.day6)
    private val NUM_IDS = intArrayOf(R.id.num0, R.id.num1, R.id.num2, R.id.num3, R.id.num4, R.id.num5, R.id.num6)
    private val DOW_IDS = intArrayOf(R.id.dow0, R.id.dow1, R.id.dow2, R.id.dow3, R.id.dow4, R.id.dow5, R.id.dow6)
    private val MORE_IDS = intArrayOf(R.id.more0, R.id.more1, R.id.more2, R.id.more3, R.id.more4, R.id.more5, R.id.more6)
    private val GAPS = intArrayOf(R.layout.widget_gap_1, R.layout.widget_gap_2, R.layout.widget_gap_3, R.layout.widget_gap_4,
        R.layout.widget_gap_5, R.layout.widget_gap_6, R.layout.widget_gap_7)
    private val CHIPS = intArrayOf(R.layout.widget_chip_1, R.layout.widget_chip_2, R.layout.widget_chip_3, R.layout.widget_chip_4,
        R.layout.widget_chip_5, R.layout.widget_chip_6, R.layout.widget_chip_7)

    /** 行の高さ・文字の大きさ・1 日に置ける行数 */
    /** maxEvents：設定で決めた 1 日の最大件数（0 = 自動：ウィジェットの大きさに合わせる） */
    data class Spec(val rowDp: Float, val laneDp: Float, val capacity: Int, val fontSp: Float, val c: WColors, val maxEvents: Int = 0)

    /** ウィジェットの実際の大きさ（dp）。Android 12 以降はランチャーが知らせる大きさの一覧から、今の向きに合うものを選ぶ */
    private fun sizeDp(context: Context, id: Int): Pair<Float, Float> {
        val opts = AppWidgetManager.getInstance(context).getAppWidgetOptions(id)
        val portrait = context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        if (Build.VERSION.SDK_INT >= 31) {
            @Suppress("DEPRECATION")
            val sizes = opts.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            val s = sizes?.let { l -> if (portrait) l.maxByOrNull { it.height } else l.maxByOrNull { it.width } }
            if (s != null && s.width > 0 && s.height > 0) return s.width to s.height
        }
        val w = opts.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val h = opts.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        return (if (w > 0) w.toFloat() else 300f) to (if (h > 0) h.toFloat() else 300f)
    }

    fun spec(context: Context, id: Int): Spec {
        val (_, h) = sizeDp(context, id)
        val font = context.container.prefs.widgetFontSize.coerceIn(8, 16).toFloat()
        val scale = context.resources.configuration.fontScale
        val rows = monthWeeks(anchor(context, id))
        val rowDp = ((h - 16f - 46f - 18f) / rows).coerceAtLeast(44f)
        val laneDp = ceil(font * scale * 1.25f + 4f)
        val max = context.container.prefs.widgetMaxEvents
        if (max > 0) {
            // 件数を指定：その件数がちょうど入る高さに行をそろえる（入りきらない週はスクロールで見る）
            return Spec(MonthLayout.rowHeightFor(max, laneDp), laneDp, max, font, widgetColors(context), maxEvents = max)
        }
        val capacity = ((rowDp - 24f - 2f) / laneDp).toInt().coerceIn(1, 15)
        return Spec(rowDp, laneDp, capacity, font, widgetColors(context))
    }

    fun allIds(context: Context): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, MonthWidgetReceiver::class.java))

    /** 設定やカレンダーが変わったとき：すべての月ウィジェットを描き直す */
    fun updateAll(context: Context) {
        val ids = allIds(context)
        ids.forEach { update(context, it, scroll = false) }
        if (ids.isNotEmpty()) AppWidgetManager.getInstance(context).notifyAppWidgetViewDataChanged(ids, R.id.list)
    }

    fun update(context: Context, id: Int, scroll: Boolean) {
        AppWidgetManager.getInstance(context).updateAppWidget(id, frame(context, id))
        if (scroll) scrollToAnchor(context, id, initial = true)
    }

    /** ［今日］［‹］［›］：表示する月を変えて、その月の最初の週までスクロール */
    fun nav(context: Context, id: Int, delta: Int) {
        val before = anchor(context, id)
        val off = if (delta == 0) 0 else (offset(context, id) + delta).coerceIn(0, MONTHS.toInt() - 1)
        store(context).edit().putInt("offset_$id", off).apply()
        val mgr = AppWidgetManager.getInstance(context)
        val after = anchor(context, id)
        mgr.partiallyUpdateAppWidget(id, RemoteViews(context.packageName, R.layout.widget_month).apply {
            setTextViewText(R.id.title, title(LocalDate.now()))
        })
        // 月によって週の数（行の高さ）が変わるときは一覧を作り直す
        if (monthWeeks(before) != monthWeeks(after)) mgr.notifyAppWidgetViewDataChanged(id, R.id.list)
        scrollToAnchor(context, id, initial = false)
    }

    /**
     * 表示中の月の最初の週が一番上に来るようにスクロールする。
     * 標準のスクロールは「見える位置まで」しか動かないので、
     * いったん月の最後の週まで動かし（下へ動くときは最初の週が上に来る）、そのあと最初の週へ戻す（上へ動くとき）。
     */
    fun scrollToAnchor(context: Context, id: Int, initial: Boolean) {
        val app = context.applicationContext
        val today = LocalDate.now()
        val month = anchor(app, id)
        val target = indexOf(month, today)
        val last = (target + monthWeeks(month) - 1).coerceAtMost(listWeeks(today) - 1)
        val mgr = AppWidgetManager.getInstance(app)
        val h = Handler(Looper.getMainLooper())
        val first = if (initial) 1200L else 0L
        if (target == 0) {
            // 今月（一覧の先頭）へ戻るだけなら 1 回で上まで動く
            h.postDelayed({
                runCatching { mgr.partiallyUpdateAppWidget(id, RemoteViews(app.packageName, R.layout.widget_month).apply { setScrollPosition(R.id.list, 0) }) }
            }, first)
            return
        }
        h.postDelayed({
            runCatching { mgr.partiallyUpdateAppWidget(id, RemoteViews(app.packageName, R.layout.widget_month).apply { setScrollPosition(R.id.list, last) }) }
        }, first)
        h.postDelayed({
            runCatching { mgr.partiallyUpdateAppWidget(id, RemoteViews(app.packageName, R.layout.widget_month).apply { setScrollPosition(R.id.list, target) }) }
        }, first + if (initial) 4500L else 900L)
    }

    /** 見出し：「本日：2026年11月17日（火）」。幅が狭いときは年を省く */
    fun title(today: LocalDate, narrow: Boolean = false) =
        "本日：" + (if (narrow) "" else "${today.year}年") + "${today.monthValue}月${today.dayOfMonth}日（${DateUtils.weekday(today)}）"

    /** 週の行の左に出す月（その週に 1 日があればその月、なければ週の始まりの月）と、月が変わる週か */
    fun railMonth(weekStart: LocalDate): Pair<Int, Boolean> {
        val first = (0..6L).map { weekStart.plusDays(it) }.firstOrNull { it.dayOfMonth == 1 }
        return if (first != null) first.monthValue to true else weekStart.monthValue to false
    }

    private fun tint(rv: RemoteViews, viewId: Int, color: Int) {
        if (Build.VERSION.SDK_INT >= 31) rv.setColorStateList(viewId, "setBackgroundTintList", ColorStateList.valueOf(color))
        else rv.setInt(viewId, "setBackgroundColor", color)
    }

    /** 外枠：背景、見出し、ボタン、曜日、週の一覧 */
    internal fun frame(context: Context, id: Int): RemoteViews {
        val c = widgetColors(context)
        val rv = RemoteViews(context.packageName, R.layout.widget_month)
        rv.setInt(R.id.bg, "setColorFilter", c.bg or (0xFF shl 24))
        rv.setInt(R.id.bg, "setImageAlpha", android.graphics.Color.alpha(c.bg))
        // 同期中は、見出しの左にくるくる、見出しは「同期しています…」（同期が終わるまで）
        val syncing = WidgetSyncState.isActive(WidgetSyncState.monthStartedAt(context, id))
        rv.setTextViewText(R.id.title, if (syncing) SYNCING_TITLE else title(LocalDate.now(), narrow = sizeDp(context, id).first < 300f))
        rv.setViewVisibility(R.id.spinner, if (syncing) android.view.View.VISIBLE else android.view.View.GONE)
        rv.setOnClickPendingIntent(R.id.title, PendingIntent.getBroadcast(context, id * 10 + 7,
            Intent(context, MonthWidgetReceiver::class.java).setAction(ACTION_REFRESH)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).setData(Uri.parse("calendar://monthwidget/$id/refresh")),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        rv.setContentDescription(R.id.title, if (syncing) SYNCING_TITLE else "今日に戻って同期")
        rv.setTextColor(R.id.title, c.text)
        rv.setInt(R.id.btn_add_bg, "setColorFilter", c.primary)
        rv.setInt(R.id.btn_add_ic, "setColorFilter", c.onPrimary)
        rv.setContentDescription(R.id.btn_add, "予定を追加")
        rv.setInt(R.id.dow_line, "setBackgroundColor", c.line)
        rv.setOnClickPendingIntent(R.id.btn_add, PendingIntent.getActivity(context, id * 10 + 5,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_NEW_EVENT, true).setData(Uri.parse("calendar://new"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        var d = DateUtils.startOfWeek(LocalDate.now())
        for (i in 0..6) {
            rv.setTextViewText(DOW_IDS[i], DateUtils.weekday(d))
            rv.setTextColor(DOW_IDS[i], when (d.dayOfWeek) { java.time.DayOfWeek.SUNDAY -> c.sunday; java.time.DayOfWeek.SATURDAY -> c.saturday; else -> c.text })
            d = d.plusDays(1)
        }
        rv.setRemoteAdapter(R.id.list, Intent(context, MonthWidgetService::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).setData(Uri.parse("calendar://monthwidget/$id")))
        // 一覧の中のタップ：日付や予定を開く（中身は各セルの fill-in で渡す）
        rv.setPendingIntentTemplate(R.id.list, PendingIntent.getActivity(context, id * 10 + 6,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE))
        return rv
    }

    /** 祝日（祝日カレンダーに予定がある日。アプリと同じ判定） */
    suspend fun holidays(context: Context, data: Map<LocalDate, List<EventInstance>>): Set<LocalDate> {
        val ids = context.container.repository.calendars().filter { it.ownerAccount.orEmpty().contains("holiday", ignoreCase = true) }
            .map { it.id }.toSet()
        if (ids.isEmpty()) return emptySet()
        return data.filterValues { l -> l.any { it.calendarId in ids } }.keys
    }

    /** 月の初日の表示（アプリの月表示と同じく「1 12月」） */
    fun dayLabel(d: LocalDate, isToday: Boolean): String = if (d.dayOfMonth == 1 && !isToday) "${d.monthValue}/1" else "${d.dayOfMonth}"

    /** 読み込み中の行（高さだけ同じ） */
    fun emptyWeek(context: Context, s: Spec): RemoteViews = RemoteViews(context.packageName, R.layout.widget_month_week).apply {
        if (Build.VERSION.SDK_INT >= 31) setViewLayoutHeight(R.id.week_root, s.rowDp, TypedValue.COMPLEX_UNIT_DIP)
        setInt(R.id.hline, "setBackgroundColor", s.c.line)
        removeAllViews(R.id.lanes)
    }

    /** 1 週分の行 */
    fun weekViews(
        context: Context, s: Spec, weekStart: LocalDate, today: LocalDate, data: Map<LocalDate, List<EventInstance>>,
        holidays: Set<LocalDate> = emptySet(),
    ): RemoteViews {
        val pkg = context.packageName
        val c = s.c
        val zone = ZoneId.systemDefault()
        val rv = RemoteViews(pkg, R.layout.widget_month_week)
        if (Build.VERSION.SDK_INT >= 31) rv.setViewLayoutHeight(R.id.week_root, s.rowDp, TypedValue.COMPLEX_UNIT_DIP)
        rv.setInt(R.id.hline, "setBackgroundColor", c.line)
        rv.setInt(R.id.rail_line, "setBackgroundColor", c.line)
        // 左の月の列：その週の月。月が変わる週は青いラベルで強調
        val (railM, starts) = railMonth(weekStart)
        rv.setTextViewText(R.id.rail_text, "${railM}月")
        rv.setTextViewTextSize(R.id.rail_text, TypedValue.COMPLEX_UNIT_SP, 13f)
        rv.setTextColor(R.id.rail_text, if (starts) c.onPrimary else c.text)
        if (starts) { rv.setInt(R.id.rail_text, "setBackgroundResource", R.drawable.w_pill); tint(rv, R.id.rail_text, c.primary) }
        else rv.setInt(R.id.rail_text, "setBackgroundResource", 0)
        // 一覧の行は使い回されるので、前の週の予定を消してから描く
        rv.removeAllViews(R.id.lanes)
        for (i in 0..6) {
            val d = weekStart.plusDays(i.toLong())
            val isToday = d == today
            // 今日は強調、偶数月はうっすら塗って、月のまとまりが分かるように
            rv.setInt(DAY_IDS[i], "setBackgroundColor", when {
                isToday -> c.todayBg
                d.monthValue % 2 == 0 -> (c.text and 0x00FFFFFF) or (0x0D shl 24)
                else -> 0
            })
            rv.setOnClickFillInIntent(DAY_IDS[i], Intent().putExtra(MainActivity.EXTRA_DATE, d.toString()).setData(Uri.parse("calendar://date/$d")))
            val label = dayLabel(d, isToday)
            rv.setTextViewText(NUM_IDS[i], label)
            rv.setTextViewTextSize(NUM_IDS[i], TypedValue.COMPLEX_UNIT_SP, s.fontSp + 2f)
            rv.setTextColor(NUM_IDS[i], when {
                isToday -> c.onPrimary
                d.dayOfMonth == 1 -> c.onPrimary
                d.dayOfWeek == java.time.DayOfWeek.SUNDAY || d in holidays -> c.sunday
                d.dayOfWeek == java.time.DayOfWeek.SATURDAY -> c.saturday
                else -> c.text
            })
            if (isToday || d.dayOfMonth == 1) { rv.setInt(NUM_IDS[i], "setBackgroundResource", R.drawable.w_pill); tint(rv, NUM_IDS[i], c.primary) }
            else rv.setInt(NUM_IDS[i], "setBackgroundResource", 0)
        }

        val items = MonthLayout.weekItems(weekStart, data, zone)
        val lanes = MonthLayout.lanes(items)
        val (shown, more) = if (s.maxEvents > 0) MonthLayout.fitMax(lanes, s.maxEvents) else MonthLayout.fit(lanes, s.capacity)
        for (lane in lanes.take(shown)) {
            val lr = RemoteViews(pkg, R.layout.widget_lane)
            if (Build.VERSION.SDK_INT >= 31) lr.setViewLayoutHeight(R.id.lane, s.laneDp, TypedValue.COMPLEX_UNIT_DIP)
            var col = 0
            for (p in lane) {
                if (p.start > col) lr.addView(R.id.lane, RemoteViews(pkg, GAPS[p.start - col - 1]))
                val e = p.item
                val chip = RemoteViews(pkg, CHIPS[p.end - p.start])
                chip.setTextViewText(R.id.chip, e.title)
                chip.setTextViewTextSize(R.id.chip, TypedValue.COMPLEX_UNIT_SP, s.fontSp)
                chip.setTextColor(R.id.chip, ColorUtils.onColor(e.color).toArgb())
                tint(chip, R.id.chip, e.color)
                chip.setOnClickFillInIntent(R.id.chip, Intent()
                    .putExtra(MainActivity.EXTRA_EVENT_ID, e.eventId).putExtra(MainActivity.EXTRA_EVENT_BEGIN, e.begin)
                    .setData(Uri.parse("calendar://event/${e.eventId}/${e.begin}")))
                lr.addView(R.id.lane, chip)
                col = p.end + 1
            }
            rv.addView(R.id.lanes, lr)
        }
        if (more.any { it > 0 }) {
            val mr = RemoteViews(pkg, R.layout.widget_more)
            if (Build.VERSION.SDK_INT >= 31) mr.setViewLayoutHeight(R.id.more, s.laneDp, TypedValue.COMPLEX_UNIT_DIP)
            for (i in 0..6) {
                mr.setTextViewText(MORE_IDS[i], if (more[i] > 0) "•••" else "")
                mr.setTextViewTextSize(MORE_IDS[i], TypedValue.COMPLEX_UNIT_SP, s.fontSp)
                mr.setTextColor(MORE_IDS[i], c.sub)
            }
            rv.addView(R.id.lanes, mr)
        }
        return rv
    }
}
