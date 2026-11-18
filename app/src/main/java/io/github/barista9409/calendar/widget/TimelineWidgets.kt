package io.github.barista9409.calendar.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.layout.ContentScale
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.barista9409.calendar.container
import io.github.barista9409.calendar.model.EventInstance
import io.github.barista9409.calendar.ui.timeline.EventLayout
import io.github.barista9409.calendar.util.DateUtils
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Glance のコンテナは子要素数に上限があるため、10 個ずつ入れ子にして縦に積む */
@Composable
private fun VStack(modifier: GlanceModifier = GlanceModifier, items: List<@Composable () -> Unit>) {
    Column(modifier) {
        if (items.size <= 10) items.forEach { it() }
        else items.chunked(10).forEach { chunk -> Column(GlanceModifier.fillMaxWidth()) { chunk.forEach { it() } } }
    }
}

class DayWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = DayWidget() }

class DayWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent { Content() }

    @Composable
    private fun Content() {
        val context = LocalContext.current
        val t = widgetTheme(context)
        val prefs = currentState<Preferences>()
        val tick = prefs[TICK_KEY] ?: 0L
        // 見出しタップの更新中（念のため 6 秒で消す）
        val refreshing = (prefs[REFRESHING_KEY] ?: 0L).let { it > 0 && System.currentTimeMillis() - it < 6000 }
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val data by produceState<Map<LocalDate, List<EventInstance>>?>(null, tick) {
            value = WidgetData.load(context, today, today.plusDays(30)) ?: emptyMap()
        }
        val nowMs = System.currentTimeMillis()
        // 今日（終わった予定は除く）と、予定がある今後の日
        val days = (0 until 30).map { today.plusDays(it.toLong()) }.mapNotNull { day ->
            val list = data.orEmpty()[day].orEmpty().filter { day != today || it.isLong || it.end > nowMs }
            if (list.isEmpty() && day != today) null else day to list
        }

        Column(GlanceModifier.fillMaxSize().background(t.bg).cornerRadius(20.dp).padding(horizontal = 8.dp, vertical = 8.dp)) {
            CenteredHeader(context, t, MonthWidget.title(today, narrow = LocalSize.current.width < 300.dp), refreshing)
            LazyColumn(GlanceModifier.fillMaxSize()) {
                items(days, itemId = { it.first.toEpochDay() }) { (day, list) ->
                    DayRow(context, t, day, day == today, list, zone)
                }
            }
        }
    }

    @Composable
    private fun DayRow(context: Context, t: WTheme, day: LocalDate, isToday: Boolean, list: List<EventInstance>, zone: ZoneId) {
        Row(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(GlanceModifier.width(44.dp).clickable(openDateAction(context, day)), horizontalAlignment = Alignment.CenterHorizontally) {
                if (isToday) {
                    Column(GlanceModifier.size(40.dp).background(t.primary).cornerRadius(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                        Text(DateUtils.weekday(day), style = TextStyle(fontSize = 10.sp, color = t.onPrimary))
                        Text("${day.dayOfMonth}", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = t.onPrimary))
                    }
                } else {
                    Text(DateUtils.weekday(day), style = TextStyle(fontSize = 11.sp, color = t.dow(day, false)))
                    Text("${day.dayOfMonth}", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = t.dow(day)))
                }
            }
            val cards = if (list.isEmpty()) listOf<@Composable () -> Unit>({
                Text("予定はありません", GlanceModifier.padding(top = 10.dp, start = 4.dp), style = TextStyle(fontSize = 13.sp, color = t.sub))
            }) else list.flatMap { e ->
                listOf<@Composable () -> Unit>(
                    { EventCard(context, day, e, zone) },
                    { Spacer(GlanceModifier.height(4.dp)) },
                )
            }
            VStack(GlanceModifier.defaultWeight().padding(start = 6.dp), cards)
        }
    }

    @Composable
    private fun EventCard(context: Context, day: LocalDate, e: EventInstance, zone: ZoneId) {
        val fg = onEventColor(e.color)
        Column(GlanceModifier.fillMaxWidth().background(ColorProvider(Color(e.color))).cornerRadius(14.dp)
            .padding(horizontal = 12.dp, vertical = 7.dp).clickable(openEventAction(context, e))) {
            val fs = context.container.prefs.widgetFontSize
            Text(e.title, maxLines = 2, style = TextStyle(fontSize = (fs + 4).sp, fontWeight = FontWeight.Medium, color = fg))
            if (!e.isLong) {
                Text("${DateUtils.time(e.startLocal(zone))}〜${DateUtils.time(e.endLocal(zone))}", maxLines = 1,
                    style = TextStyle(fontSize = (fs + 2).sp, color = fg))
            }
        }
    }
}
