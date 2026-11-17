package io.github.barista9409.calendar.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.barista9409.calendar.model.EventInstance
import io.github.barista9409.calendar.ui.sheet.timeRange
import io.github.barista9409.calendar.util.DateUtils
import kotlinx.coroutines.delay
import java.time.ZoneId

/**
 * 予定の検索（タイトル・場所・説明）。picking=true のときは「添付先の予定を選ぶ」画面として使う。
 */
@Composable
private fun SearchScreenContent(
    title: String,
    search: suspend (String) -> List<EventInstance>,
    zone: ZoneId,
    onPick: (EventInstance) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val scheme = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<EventInstance>?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(query) {
        delay(250)
        results = null
        results = search(query.trim())
    }
    Surface(Modifier.fillMaxSize(), color = scheme.surface) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "戻る") }
                TextField(query, { query = it }, placeholder = { Text(title) }, singleLine = true,
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                    modifier = Modifier.weight(1f).focusRequester(focus))
            }
            if (results == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            val list = results.orEmpty()
            if (results != null && list.isEmpty()) {
                Text(if (query.isBlank()) "予定がありません" else "「$query」に一致する予定はありません",
                    Modifier.padding(24.dp), color = scheme.onSurfaceVariant)
            }
            // 月が変わる行に見出しを付ける
            val rows = list.mapIndexed { i, e ->
                val m = DateUtils.yearMonth(e.startDate(zone))
                Triple(e, m, i == 0 || DateUtils.yearMonth(list[i - 1].startDate(zone)) != m)
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(rows, key = { "${it.first.instanceId}-${it.first.begin}" }) { (e, month, header) ->
                    val d = e.startDate(zone)
                    Column {
                        if (header) {
                            Text(month, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
                        }
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(e) }.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Column(Modifier.width(56.dp)) {
                                Text(DateUtils.weekday(d), fontSize = 11.sp, color = scheme.onSurfaceVariant)
                                Text("${d.monthValue}/${d.dayOfMonth}", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(Modifier.width(4.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(Color(e.color)))
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(e.title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(timeRange(e, zone) + (e.location?.takeIf { it.isNotBlank() }?.let { " ・ $it" } ?: ""),
                                    fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}


/** ポップアップの配色で表示 */
@Composable
fun SearchScreen(title: String, search: suspend (String) -> List<EventInstance>, zone: ZoneId, onPick: (EventInstance) -> Unit, onClose: () -> Unit) {
    io.github.barista9409.calendar.ui.theme.PopupTheme { SearchScreenContent(title, search, zone, onPick, onClose) }
}
