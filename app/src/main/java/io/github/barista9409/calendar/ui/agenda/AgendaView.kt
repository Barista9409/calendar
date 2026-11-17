package io.github.barista9409.calendar.ui.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.barista9409.calendar.model.EventInstance
import io.github.barista9409.calendar.ui.sheet.timeRange
import io.github.barista9409.calendar.ui.theme.LocalCalendarColors
import io.github.barista9409.calendar.util.DateUtils
import kotlinx.coroutines.flow.SharedFlow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

private sealed interface Row_ { data class Month(val d: LocalDate) : Row_; data class Day(val d: LocalDate) : Row_ }

/** 予定一覧（今日から 6 か月先まで） */
@Composable
fun AgendaView(
    today: LocalDate,
    eventsByDay: Map<LocalDate, List<EventInstance>>,
    zone: ZoneId,
    jumpRequests: SharedFlow<LocalDate>,
    onEventTap: (EventInstance) -> Unit,
    onDayTap: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = remember(today, eventsByDay) {
        val out = mutableListOf<Row_>()
        var d = today
        var lastMonth = -1
        val end = today.plusMonths(6)
        while (d.isBefore(end)) {
            if (d == today || !eventsByDay[d].isNullOrEmpty()) {
                if (d.monthValue != lastMonth) { out += Row_.Month(d); lastMonth = d.monthValue }
                out += Row_.Day(d)
            }
            d = d.plusDays(1)
        }
        out
    }
    val state = rememberLazyListState()
    LaunchedEffect(jumpRequests) { jumpRequests.collect { state.animateScrollToItem(0) } }
    val scheme = MaterialTheme.colorScheme
    val cc = LocalCalendarColors.current
    LazyColumn(state = state, modifier = modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
        items(rows, key = { r -> when (r) { is Row_.Month -> "m${r.d}"; is Row_.Day -> "d${r.d}" } }) { r ->
            when (r) {
                is Row_.Month -> Text(DateUtils.yearMonth(r.d), fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp))
                is Row_.Day -> {
                    val list = eventsByDay[r.d].orEmpty()
                    val color = when (r.d.dayOfWeek) { DayOfWeek.SUNDAY -> cc.sunday; DayOfWeek.SATURDAY -> cc.saturday; else -> scheme.onSurface }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Column(Modifier.width(52.dp).clickable { onDayTap(r.d) }.padding(top = 6.dp)) {
                            Text(DateUtils.weekday(r.d), fontSize = 11.sp, color = scheme.onSurfaceVariant)
                            Text("${r.d.dayOfMonth}", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                                color = if (r.d == today) scheme.primary else color)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (list.isEmpty()) Text("予定はありません", fontSize = 13.sp, color = scheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 12.dp))
                            list.forEach { e ->
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(scheme.surfaceVariant)
                                        .clickable { onEventTap(e) }.padding(10.dp)
                                ) {
                                    Box(Modifier.width(4.dp).height(36.dp).clip(RoundedCornerShape(2.dp))
                                        .background(Color(e.color)))
                                    Column(Modifier.padding(start = 10.dp)) {
                                        Text(e.displayTitle, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(timeRange(e, zone), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

