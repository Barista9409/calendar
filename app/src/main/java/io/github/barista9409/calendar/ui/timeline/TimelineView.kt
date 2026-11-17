package io.github.barista9409.calendar.ui.timeline

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.barista9409.calendar.model.EventInstance
import io.github.barista9409.calendar.ui.theme.LocalCalendarColors
import io.github.barista9409.calendar.ui.theme.NowRed
import io.github.barista9409.calendar.util.ColorUtils
import io.github.barista9409.calendar.util.DateUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** 新規作成中の時間枠（強調表示用） */
data class SlotHighlight(val start: LocalDateTime, val end: LocalDateTime)

/**
 * 週表示（days=7）と日表示（days=1）で共通の時間軸ビュー。左右スワイプで前後へ。
 */
@Composable
fun TimelineView(
    days: Int,
    selected: LocalDate,
    today: LocalDate,
    eventsByDay: Map<LocalDate, List<EventInstance>>,
    holidays: Set<LocalDate>,
    zone: ZoneId,
    highlight: SlotHighlight?,
    selectedEventId: Long?,
    jumpRequests: SharedFlow<LocalDate>,
    onPageChange: (firstDate: LocalDate) -> Unit,
    onEmptyTap: (LocalDateTime) -> Unit,
    onEventTap: (EventInstance) -> Unit,
    onHeaderDateTap: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    onEventMove: (EventInstance, Int, Int, Boolean) -> Unit = { _, _, _, _ -> },
) {
    val isWeek = days == 7
    // ページ番号は「この表示を開いたときの日付」を中心とした相対値（1970 年起点の通し番号は使わない）
    val anchor = remember(days) { if (isWeek) DateUtils.startOfWeek(selected) else selected }
    val count = PAGE_COUNT
    fun pageOf(d: LocalDate): Int {
        val diff = if (isWeek) java.time.temporal.ChronoUnit.WEEKS.between(anchor, DateUtils.startOfWeek(d))
        else java.time.temporal.ChronoUnit.DAYS.between(anchor, d)
        return (CENTER + diff).toInt().coerceIn(0, PAGE_COUNT - 1)
    }
    fun firstDateOf(p: Int): LocalDate {
        val diff = (p - CENTER).toLong()
        return if (isWeek) anchor.plusWeeks(diff) else anchor.plusDays(diff)
    }

    val pager = rememberPagerState(initialPage = CENTER) { count }
    val weekAutoFlow = (androidx.compose.ui.platform.LocalContext.current.applicationContext as io.github.barista9409.calendar.CalendarApp)
        .container.prefs.weekAutoWidth
    val weekAuto by weekAutoFlow.collectAsState()
    val hourHeight = if (isWeek) 52.dp else 60.dp
    val density = LocalDensity.current
    // 縦スクロール位置はページ間で共有（ScrollState 自体は各ページで別々に持つ）
    val savedScroll = remember { androidx.compose.runtime.mutableIntStateOf(with(density) { (hourHeight * 7).roundToPx() }) }
    val cc = LocalCalendarColors.current
    val scheme = MaterialTheme.colorScheme

    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { onPageChange(firstDateOf(it)) }
    }
    LaunchedEffect(jumpRequests) {
        jumpRequests.collect { d -> pager.animateScrollToPage(pageOf(d)) }
    }
    // 外から選択日が変わった場合だけ、そのページへ（スワイプ中は何もしない）
    LaunchedEffect(selected) {
        val p = pageOf(selected)
        if (p != pager.settledPage && !pager.isScrollInProgress) pager.scrollToPage(p)
    }
    val now by produceState(LocalDateTime.now()) {
        while (true) { delay(60_000); value = LocalDateTime.now() }
    }

    Row(modifier.fillMaxSize()) {
        // 左の時間軸はスワイプしても動かない（縦スクロールだけ追従）
        Column(Modifier.width(GUTTER).fillMaxHeight()) {
            Box(Modifier.height(HEADER_H))
            Box(Modifier.height(ALLDAY_H).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("終日", fontSize = 9.sp, color = scheme.onSurfaceVariant)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(cc.gridLine))
            Box(Modifier.fillMaxSize().clipToBounds()) {
                Column(
                    Modifier.wrapContentHeight(Alignment.Top, unbounded = true)
                        .offset { androidx.compose.ui.unit.IntOffset(0, -savedScroll.intValue) }
                ) {
                    for (h in 0 until 24) {
                        Box(Modifier.height(hourHeight).fillMaxWidth()) {
                            if (h > 0) Text("$h:00", fontSize = 10.sp, color = scheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.TopEnd).offset(y = (-7).dp).padding(end = 6.dp))
                        }
                    }
                }
            }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxHeight(), beyondViewportPageCount = 1, key = { it }) { page ->
            val first = firstDateOf(page)
            val dates = remember(first, days) { (0 until days).map { first.plusDays(it.toLong()) } }
            val scroll = rememberScrollState(savedScroll.intValue)
            val isCurrent = page == pager.currentPage
            LaunchedEffect(scroll, isCurrent) {
                if (isCurrent) snapshotFlow { scroll.value }.collect { savedScroll.intValue = it }
                else snapshotFlow { savedScroll.intValue }.collect { if (scroll.value != it) scroll.scrollTo(it) }
            }
            // 予定が重なる日ほど列を広く（設定でオンのとき）
            val weights = remember(dates, eventsByDay, weekAuto) {
                dates.associateWith { d ->
                    if (!weekAuto || days == 1) 1f else {
                        val start = d.atStartOfDay(zone).toInstant().toEpochMilli()
                        val items = eventsByDay[d].orEmpty().filter { !it.isLong }.map {
                            EventLayout.Item(((it.begin - start) / 60_000).toInt().coerceIn(0, 1440), ((it.end - start) / 60_000).toInt().coerceIn(0, 1440))
                        }
                        val cols = EventLayout.layout(items).maxOfOrNull { it.columns } ?: 1
                        (1f + 0.6f * (cols - 1)).coerceAtMost(3f)
                    }
                }
            }
            TimelinePage(dates, today, eventsByDay, holidays, zone, hourHeight, scroll, now, highlight, selectedEventId,
                onEmptyTap, onEventTap, onHeaderDateTap, onEventMove, weights)
        }
    }
}

private const val PAGE_COUNT = 20_000
private const val CENTER = PAGE_COUNT / 2

private val GUTTER = 44.dp
private val HEADER_H = 56.dp
private val ALLDAY_H = 24.dp

@Composable
private fun TimelinePage(
    dates: List<LocalDate>,
    today: LocalDate,
    eventsByDay: Map<LocalDate, List<EventInstance>>,
    holidays: Set<LocalDate>,
    zone: ZoneId,
    hourHeight: Dp,
    scroll: ScrollState,
    now: LocalDateTime,
    highlight: SlotHighlight?,
    selectedEventId: Long?,
    onEmptyTap: (LocalDateTime) -> Unit,
    onEventTap: (EventInstance) -> Unit,
    onHeaderDateTap: (LocalDate) -> Unit,
    onEventMove: (EventInstance, Int, Int, Boolean) -> Unit,
    weights: Map<LocalDate, Float>,
) {
    val cc = LocalCalendarColors.current
    val scheme = MaterialTheme.colorScheme
    val single = dates.size == 1
    // 月の境目（週の途中で 1 日になる列）には区切り線
    fun Modifier.monthEdge(d: LocalDate): Modifier =
        if (d.dayOfMonth == 1) this.drawBehind {
            drawLine(scheme.primary, Offset(0f, 0f), Offset(0f, size.height), 3.dp.toPx())
        } else this

    Column(Modifier.fillMaxSize()) {
        // 日付ヘッダー（固定の高さ）
        Row(Modifier.fillMaxWidth().height(HEADER_H)) {
            for (d in dates) {
                val isToday = d == today
                val color = when {
                    isToday -> scheme.onPrimary
                    d in holidays || d.dayOfWeek == DayOfWeek.SUNDAY -> cc.sunday
                    d.dayOfWeek == DayOfWeek.SATURDAY -> cc.saturday
                    else -> scheme.onSurface
                }
                val newMonth = d.dayOfMonth == 1
                Column(
                    Modifier.weight(weights[d] ?: 1f).fillMaxHeight().monthEdge(d)
                        .then(if (newMonth) Modifier.background(scheme.primaryContainer.copy(alpha = 0.5f)) else Modifier)
                        .clickable { onHeaderDateTap(d) }
                        .padding(start = if (single) 8.dp else 0.dp, top = 4.dp),
                    horizontalAlignment = if (single) Alignment.Start else Alignment.CenterHorizontally,
                ) {
                    if (newMonth) Text("${d.monthValue}月", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
                    else Text(DateUtils.weekday(d), fontSize = 11.sp, color = scheme.onSurfaceVariant)
                    Box(Modifier.size(30.dp).clip(CircleShape).background(if (isToday) scheme.primary else Color.Transparent),
                        contentAlignment = Alignment.Center) {
                        Text("${d.dayOfMonth}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
                    }
                }
            }
        }
        // 終日予定（固定の高さ。複数ある日は「+n」）
        Row(Modifier.fillMaxWidth().height(ALLDAY_H)) {
            for (d in dates) {
                val list = eventsByDay[d].orEmpty().filter { it.isLong }
                Row(Modifier.weight(weights[d] ?: 1f).fillMaxHeight().monthEdge(d).padding(horizontal = 1.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    val shown = if (single) list.take(3) else list.take(1)
                    shown.forEach { e ->
                        Text(e.displayTitle + if (!single && list.size > 1) " +${list.size - 1}" else "",
                            maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 10.sp, color = ColorUtils.onColor(e.color),
                            modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(Color(e.color))
                                .clickable { if (!single && list.size > 1) onHeaderDateTap(d) else onEventTap(e) }
                                .padding(horizontal = 4.dp))
                    }
                    if (single && list.size > 3) Text("+${list.size - 3}", fontSize = 10.sp, color = scheme.onSurfaceVariant)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(cc.gridLine))
        // 時間軸の本体
        Row(Modifier.fillMaxSize().verticalScroll(scroll)) {
            for (d in dates) {
                DayColumn(
                    d, eventsByDay[d].orEmpty().filter { !it.isLong }, zone, hourHeight,
                    now.takeIf { d == today }?.toLocalTime(),
                    highlight?.takeIf { it.start.toLocalDate() == d }, selectedEventId, onEmptyTap, onEventTap,
                    Modifier.weight(weights[d] ?: 1f).monthEdge(d), onEventMove, single,
                )
            }
        }
    }
}

@Composable
private fun DayColumn(
    date: LocalDate,
    events: List<EventInstance>,
    zone: ZoneId,
    hourHeight: Dp,
    nowTime: LocalTime?,
    highlight: SlotHighlight?,
    selectedEventId: Long?,
    onEmptyTap: (LocalDateTime) -> Unit,
    onEventTap: (EventInstance) -> Unit,
    modifier: Modifier,
    onEventMove: (EventInstance, Int, Int, Boolean) -> Unit,
    single: Boolean,
) {
    val cc = LocalCalendarColors.current
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val hourPx = with(density) { hourHeight.toPx() }
    val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val items = events.map { e ->
        val s = ((e.begin - dayStart) / 60_000).toInt().coerceIn(0, 1440)
        val en = ((e.end - dayStart) / 60_000).toInt().coerceIn(0, 1440)
        EventLayout.Item(s, en)
    }
    val placements = EventLayout.layout(items)

    BoxWithConstraints(
        modifier.height(hourHeight * 24)
            .drawBehind {
                for (h in 1 until 24) drawLine(cc.gridLine, Offset(0f, h * hourPx), Offset(size.width, h * hourPx), 1f)
                drawLine(cc.gridLine, Offset(0f, 0f), Offset(0f, size.height), 1f)
            }
            .pointerInput(date) {
                detectTapGestures { off ->
                    val minutes = ((off.y / hourPx) * 60).toInt().coerceIn(0, 1439) / 30 * 30
                    onEmptyTap(date.atTime(minutes / 60, minutes % 60))
                }
            }
    ) {
        val colW = maxWidth
        if (highlight != null) {
            val s = highlight.start.toLocalTime().toSecondOfDay() / 60f
            val e = if (highlight.end.toLocalDate() != date) 1440f else highlight.end.toLocalTime().toSecondOfDay() / 60f
            Box(
                Modifier.offset(y = hourHeight * (s / 60f)).fillMaxWidth().height(hourHeight * (((e - s).coerceAtLeast(30f)) / 60f))
                    .padding(horizontal = 1.dp).clip(RoundedCornerShape(6.dp))
                    .background(scheme.primary.copy(alpha = 0.14f)).border(2.dp, scheme.primary, RoundedCornerShape(6.dp))
            )
        }
        events.forEachIndexed { i, e ->
            val it = items[i]; val p = placements[i]
            val top = hourHeight * (it.start / 60f)
            val h = (hourHeight * ((it.end - it.start).coerceAtLeast(20) / 60f))
            val w = colW / p.columns
            val selected = e.eventId == selectedEventId
            val fg = ColorUtils.onColor(e.color)
            // 長押しでドラッグ移動（15 分単位、週表示では日もまたげる）。選択中は下端のドラッグで長さを変更
            var drag by remember(e.instanceId) { androidx.compose.runtime.mutableStateOf(Offset.Zero) }
            var resize by remember(e.instanceId) { androidx.compose.runtime.mutableFloatStateOf(0f) }
            val colPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val dragging = drag != Offset.Zero || resize != 0f
            Column(
                Modifier.offset(x = w * p.column, y = top)
                    .offset { androidx.compose.ui.unit.IntOffset(drag.x.toInt(), drag.y.toInt()) }
                    .zIndex(if (dragging) 1f else 0f)
                    .width(w).height(h + with(density) { resize.toDp() }).padding(horizontal = 1.dp, vertical = 1.dp)
                    .clip(RoundedCornerShape(6.dp)).background(Color(e.color).copy(alpha = if (dragging) 0.85f else 1f))
                    .then(if (selected || dragging) Modifier.border(2.dp, scheme.onSurface, RoundedCornerShape(6.dp)) else Modifier)
                    .pointerInput(e.instanceId) {
                        detectDragGesturesAfterLongPress(
                            onDrag = { change, amount -> change.consume(); drag += amount },
                            onDragEnd = {
                                val minutes = Math.round(drag.y / hourPx * 60f / 15f) * 15
                                val dayShift = if (single) 0 else Math.round(drag.x / colPx)
                                drag = Offset.Zero
                                onEventMove(e, dayShift, minutes, false)
                            },
                            onDragCancel = { drag = Offset.Zero },
                        )
                    }
                    .clickable { onEventTap(e) }
                    .semantics { contentDescription = "${e.title}、${DateUtils.time(e.startLocal(zone))}から" }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(e.displayTitle, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, color = fg,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (it.end - it.start >= 45) {
                    Text(DateUtils.time(e.startLocal(zone)), fontSize = 10.sp, color = fg.copy(alpha = 0.85f), maxLines = 1)
                }
                if (selected) {
                    Spacer(Modifier.weight(1f))
                    // 下端のつまみ：上下にドラッグして終了時刻を変更
                    Box(
                        Modifier.fillMaxWidth().height(14.dp)
                            .pointerInput(e.instanceId) {
                                detectVerticalDragGestures(
                                    onVerticalDrag = { change, dy -> change.consume(); resize += dy },
                                    onDragEnd = {
                                        val minutes = Math.round(resize / hourPx * 60f / 15f) * 15
                                        resize = 0f
                                        onEventMove(e, 0, minutes, true)
                                    },
                                    onDragCancel = { resize = 0f },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) { Box(Modifier.size(width = 24.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(fg.copy(alpha = 0.7f))) }
                }
            }
        }
        if (nowTime != null) {
            val y = hourHeight * (nowTime.toSecondOfDay() / 3600f)
            Box(Modifier.offset(y = y - 1.dp).fillMaxWidth().height(2.dp).background(NowRed))
            Box(Modifier.offset(x = (-4).dp, y = y - 5.dp).size(10.dp).clip(CircleShape).background(NowRed))
        }
    }
}
