package io.github.barista9409.calendar.ui.month

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import io.github.barista9409.calendar.model.EventInstance
import io.github.barista9409.calendar.ui.theme.LocalCalendarColors
import io.github.barista9409.calendar.util.ColorUtils
import io.github.barista9409.calendar.util.DateUtils
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 月表示。週を1行とした無限縦スクロールで、止まると週の区切りに揃う。
 */
@Composable
fun MonthView(
    today: LocalDate,
    selected: LocalDate?,
    eventsByDay: Map<LocalDate, List<EventInstance>>,
    holidays: Set<LocalDate>,
    jumpRequests: SharedFlow<LocalDate>,
    onDayClick: (LocalDate) -> Unit,
    onVisibleChange: (topWeekStart: LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val initial = DateUtils.weekIndex((selected ?: today).withDayOfMonth(1))
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initial)
    val fling = rememberSnapFlingBehavior(lazyListState = state)

    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex }.distinctUntilChanged().collect { i ->
            onVisibleChange(DateUtils.weekStartOf(i))
        }
    }
    LaunchedEffect(jumpRequests) {
        jumpRequests.collect { d -> state.animateScrollToItem(DateUtils.weekIndex(d.withDayOfMonth(1))) }
    }
    // 選択した週が見えなくなったら（シートが開いた時など）見える位置へ
    LaunchedEffect(selected, state.layoutInfo.viewportSize) {
        val s = selected ?: return@LaunchedEffect
        val idx = DateUtils.weekIndex(s)
        val visible = state.layoutInfo.visibleItemsInfo
        val viewportEnd = state.layoutInfo.viewportEndOffset
        val item = visible.firstOrNull { it.index == idx }
        if (visible.isNotEmpty() && (item == null || item.offset + item.size > viewportEnd)) {
            state.animateScrollToItem(maxOf(idx - 1, 0))
        }
    }

    Column(modifier.fillMaxSize()) {
        WeekdayHeader()
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // 行の高さは固定：シートを開いて表示領域が狭くなっても縮めない（隠れた分はスクロールで見る）。
            // シートを閉じた状態の高さ（これまでに見た一番大きい高さ）を 6 週で割る
            var fullH by androidx.compose.runtime.saveable.rememberSaveable(stateSaver = DpSaver) { androidx.compose.runtime.mutableStateOf(0.dp) }
            // シートを開いた状態で始まったときのための目安（画面の高さから上部のバーなどを除いた高さ）
            val screenGuess = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp - 240).coerceAtLeast(300).dp
            if (fullH < screenGuess) fullH = screenGuess
            if (maxHeight > fullH) fullH = maxHeight
            val rowH = monthRowHeight(fullH)
            LazyColumn(state = state, flingBehavior = fling, modifier = Modifier.fillMaxSize()) {
                items(DateUtils.WEEK_COUNT, key = { it }) { i ->
                    WeekRow(DateUtils.weekStartOf(i), rowH, today, selected, eventsByDay, holidays, onDayClick)
                }
            }
        }
    }
}

@Composable
fun WeekdayHeader() {
    val cc = LocalCalendarColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp)) {
        var d = DateUtils.startOfWeek(LocalDate.now())
        repeat(7) {
            val color = when (d.dayOfWeek) {
                DayOfWeek.SUNDAY -> cc.sunday; DayOfWeek.SATURDAY -> cc.saturday
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(DateUtils.weekday(d), Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, color = color)
            d = d.plusDays(1)
        }
    }
}

@Composable
private fun WeekRow(
    weekStart: LocalDate,
    height: androidx.compose.ui.unit.Dp,
    today: LocalDate,
    selected: LocalDate?,
    eventsByDay: Map<LocalDate, List<EventInstance>>,
    holidays: Set<LocalDate>,
    onDayClick: (LocalDate) -> Unit,
) {
    val cc = LocalCalendarColors.current
    val scheme = MaterialTheme.colorScheme
    val zone = java.time.ZoneId.systemDefault()
    // 1 段の高さ（端末の文字の大きさを反映）から、1 日に置ける段数を決める
    val lineDp = 13f * androidx.compose.ui.platform.LocalDensity.current.fontScale + 1f
    val capacity = monthCellCapacity(height.value, lineDp)
    val lanes = remember(weekStart, eventsByDay) {
        io.github.barista9409.calendar.widget.MonthLayout.lanes(
            io.github.barista9409.calendar.widget.MonthLayout.weekItems(weekStart, eventsByDay, zone))
    }
    val (shown, more) = io.github.barista9409.calendar.widget.MonthLayout.fit(lanes, capacity)

    Box(
        Modifier.fillMaxWidth().height(height).padding(horizontal = 2.dp)
            .border(width = 0.5.dp, color = cc.gridLine)
    ) {
        // 下：日ごとのマス（背景・日付・選択枠・タップ）
        Row(Modifier.fillMaxSize()) {
            for (i in 0 until 7) {
                val d = weekStart.plusDays(i.toLong())
                DayCell(d, d == today, d == selected, d in holidays, eventsByDay[d].orEmpty(), onDayClick, Modifier.weight(1f))
            }
        }
        // 上：予定の帯（複数日はつながる）。タップは下のマスに届く
        Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            for (lane in lanes.take(shown)) {
                Row(Modifier.fillMaxWidth().height((lineDp - 1f).dp)) {
                    var col = 0
                    for (p in lane) {
                        if (p.start > col) Spacer(Modifier.weight((p.start - col).toFloat()))
                        EventBar(p.item, Modifier.weight((p.end - p.start + 1).toFloat()))
                        col = p.end + 1
                    }
                    if (col < 7) Spacer(Modifier.weight((7 - col).toFloat()))
                }
            }
            if (more.any { it > 0 }) {
                Row(Modifier.fillMaxWidth()) {
                    for (i in 0 until 7) {
                        Text(if (more[i] > 0) "+${more[i]}" else "", Modifier.weight(1f).padding(start = 3.dp),
                            fontSize = 9.sp, lineHeight = 13.sp, color = scheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    isHoliday: Boolean,
    events: List<EventInstance>,
    onClick: (LocalDate) -> Unit,
    modifier: Modifier,
) {
    val cc = LocalCalendarColors.current
    val scheme = MaterialTheme.colorScheme
    val bg = if (date.monthValue % 2 == 0) cc.altMonth else Color.Transparent
    val numColor = when {
        isToday -> scheme.onPrimary
        isHoliday || date.dayOfWeek == DayOfWeek.SUNDAY -> cc.sunday
        date.dayOfWeek == DayOfWeek.SATURDAY -> cc.saturday
        else -> scheme.onSurface
    }
    val label = buildString {
        append(DateUtils.date(date))
        if (isToday) append("、今日")
        if (events.isNotEmpty()) {
            append("、予定").append(events.size).append("件：")
            append(events.joinToString("、") { it.displayTitle })
        }
    }
    Column(
        modifier.fillMaxHeight().background(bg)
            .then(if (isSelected) Modifier.border(2.dp, scheme.primary, RoundedCornerShape(6.dp)) else Modifier)
            .clickable { onClick(date) }
            .semantics { contentDescription = label }
            .padding(horizontal = 1.dp, vertical = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(20.dp).clip(CircleShape).background(if (isToday) scheme.primary else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text("${date.dayOfMonth}", fontSize = 11.sp, color = numColor,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium)
            }
            if (date.dayOfMonth == 1) {
                Text("${date.monthValue}月", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
            }
        }
    }
}

/** 予定の帯（ウィジェットと同じく、すべて予定の色で塗る） */
@Composable
private fun EventBar(e: EventInstance, modifier: Modifier) {
    Text(
        e.displayTitle, maxLines = 1, overflow = TextOverflow.Clip, fontSize = 9.sp, lineHeight = 13.sp,
        color = ColorUtils.onColor(e.color),
        modifier = modifier.fillMaxHeight().padding(horizontal = 1.dp).clip(RoundedCornerShape(3.dp))
            .background(Color(e.color)).padding(horizontal = 2.dp),
    )
}

/** 月表示の行の高さ（シートの開閉に関係なく一定） */
internal fun monthRowHeight(fullHeight: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp = max(64.dp, fullHeight / 6)

/** 1 日のマスに表示できる予定の数（日付の行 24dp を除いた高さ ÷ 1 件の高さ） */
internal fun monthCellCapacity(cellHeightDp: Float, lineDp: Float): Int = ((cellHeightDp - 24f) / lineDp).toInt().coerceAtLeast(1)

private val DpSaver = androidx.compose.runtime.saveable.Saver<androidx.compose.ui.unit.Dp, Float>(save = { it.value }, restore = { it.dp })
