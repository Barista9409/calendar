package io.github.barista9409.calendar.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import android.provider.CalendarContract.Attendees
import io.github.barista9409.calendar.model.CalendarInfo
import io.github.barista9409.calendar.model.EventDraft
import io.github.barista9409.calendar.model.EventInstance
import io.github.barista9409.calendar.ui.SheetSize
import io.github.barista9409.calendar.util.DateUtils
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** 予定詳細の日付行（複数日の予定は timeRange に日付が入るので、日付を重ねない） */
fun eventDateLine(e: EventInstance, zone: ZoneId): String {
    val r = timeRange(e, zone)
    return if (e.startDate(zone) == e.lastDate(zone)) "${DateUtils.date(e.startDate(zone))} $r" else r
}

fun timeRange(e: EventInstance, zone: ZoneId): String {
    val s = e.startDate(zone); val l = e.lastDate(zone)
    if (e.allDay) return if (s == l) "終日" else "${DateUtils.date(s)} – ${DateUtils.date(l)}（終日）"
    val st = e.startLocal(zone); val en = e.endLocal(zone)
    return if (st.toLocalDate() == en.toLocalDate() || en.toLocalTime() == LocalTime.MIDNIGHT && en.toLocalDate() == st.toLocalDate().plusDays(1))
        "${DateUtils.time(st)} – ${DateUtils.time(en)}"
    else "${DateUtils.date(st.toLocalDate())} ${DateUtils.time(st)} – ${DateUtils.date(en.toLocalDate())} ${DateUtils.time(en)}"
}

/** 下から出るシートの外枠。ハンドルを上下にドラッグ（またはタップ）して高さを切り替える */
@Composable
fun SheetPanel(
    size: SheetSize,
    onSizeChange: (SheetSize) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    canGrow: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    // シートの中身を上下にスワイプしたときも境界を動かす：
    // 上へ → まずシートを広げる（全画面になったら中身をスクロール）
    // 下へ → 中身が一番上まで戻ったら、シートを縮める
    val latestGrow by androidx.compose.runtime.rememberUpdatedState(canGrow)
    val latestDrag by androidx.compose.runtime.rememberUpdatedState(onDrag)
    val latestEnd by androidx.compose.runtime.rememberUpdatedState(onDragEnd)
    val nested = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            var moved = false
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset,
                                     source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput && available.y < 0 && latestGrow) {
                    moved = true; latestDrag(available.y); return androidx.compose.ui.geometry.Offset(0f, available.y)
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
            override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset,
                                      source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput && available.y > 0) {
                    moved = true; latestDrag(available.y); return androidx.compose.ui.geometry.Offset(0f, available.y)
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                if (moved) { moved = false; latestEnd(); return available }
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }
    // ハンドルを上下にドラッグすると、カレンダーとの境界が指に追従して動く
    val dragState = rememberDraggableState { onDrag(it) }
    io.github.barista9409.calendar.ui.theme.PopupTheme {
    Surface(
        modifier = modifier.fillMaxWidth().shadow(16.dp, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth()) {
            Box(
                Modifier.fillMaxWidth().height(40.dp)
                    .draggable(dragState, Orientation.Vertical, onDragStopped = { onDragEnd() })
                    .clickable { onSizeChange(if (size == SheetSize.FULL) SheetSize.HALF else SheetSize.FULL) }
                    .semantics { contentDescription = if (size == SheetSize.FULL) "シートを縮める" else "シートを広げる" },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outline))
            }
            // 閉じるボタン（右上）
            androidx.compose.material3.IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(end = 4.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = "閉じる", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            }
            Column(Modifier.fillMaxSize().nestedScroll(nested).imePadding(), content = content)
        }
    }
    }
}

// ---------------- 日のシート（月表示） ----------------

@Composable
fun DaySheetContent(
    date: LocalDate,
    events: List<EventInstance>,
    zone: ZoneId,
    calendarName: (Long) -> String,
    onQuickAdd: (String) -> Unit,
    onEventClick: (EventInstance) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            Text(DateUtils.date(date), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("予定 ${events.size}件", fontSize = 12.sp, color = scheme.onSurfaceVariant)
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(12.dp))
                .background(scheme.primaryContainer.copy(alpha = 0.5f)).clickable { onQuickAdd("") }
                .heightIn(min = 52.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Add, null, tint = scheme.primary)
            Text("この日に予定を追加", Modifier.padding(start = 12.dp), fontSize = 15.sp, color = scheme.primary, fontWeight = FontWeight.Medium)
        }
        if (events.isEmpty()) {
            Text("予定はありません", color = scheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
        }
        events.forEach { e ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp))
                    .background(scheme.surfaceVariant).clickable { onEventClick(e) }.padding(12.dp)
            ) {
                Box(Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(2.dp)).background(Color(e.color)))
                Column(Modifier.padding(start = 12.dp)) {
                    Text("${timeRange(e, zone)} ・ ${calendarName(e.calendarId)}", fontSize = 12.sp, color = scheme.onSurfaceVariant)
                    Text(e.displayTitle, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------- 予定の詳細シート ----------------

@Composable
fun EventSheetContent(
    e: EventInstance,
    calendar: CalendarInfo?,
    zone: ZoneId,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: (io.github.barista9409.calendar.model.RecurScope) -> Unit,
    attachments: @Composable () -> Unit = {},
    guests: List<io.github.barista9409.calendar.data.CalendarRepository.Guest> = emptyList(),
) {
    val scheme = MaterialTheme.colorScheme
    var askSeries by remember { mutableStateOf(false) }
    val writable = calendar?.writable == true
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 8.dp).size(14.dp).clip(RoundedCornerShape(4.dp)).background(Color(e.color)))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(e.title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(eventDateLine(e, zone),
                    fontSize = 14.sp, color = scheme.onSurfaceVariant)
                if (e.pendingUpload) Text("↑ Google へ送信待ち（同期すると届きます）", fontSize = 12.sp, color = scheme.primary)
            }
            if (writable) IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "編集") }
        }
        Spacer(Modifier.height(12.dp))
        if (e.isRecurring) InfoRow(Icons.Outlined.Repeat,
            io.github.barista9409.calendar.util.RRule.parse(e.rrule)?.label(e.startDate(zone)) ?: recurrenceLabel(e.rrule!!))
        calendar?.let { InfoRow(Icons.Outlined.CalendarMonth, it.name, sub = it.accountName.takeIf { a -> a != it.name }) }
        e.location?.takeIf { it.isNotBlank() }?.let { InfoRow(Icons.Outlined.LocationOn, it) }
        if (guests.isNotEmpty()) {
            val yes = guests.count { it.status == Attendees.ATTENDEE_STATUS_ACCEPTED }
            val no = guests.count { it.status == Attendees.ATTENDEE_STATUS_DECLINED }
            val maybe = guests.count { it.status == Attendees.ATTENDEE_STATUS_TENTATIVE }
            val wait = guests.size - yes - no - maybe
            InfoRow(Icons.Outlined.Person, "ゲスト ${guests.size}人（出席 $yes・欠席 $no・未定 $maybe・未回答 $wait）\n" +
                guests.joinToString("\n") { g ->
                    g.email + when (g.status) {
                        Attendees.ATTENDEE_STATUS_ACCEPTED -> "　✓"; Attendees.ATTENDEE_STATUS_DECLINED -> "　×"; Attendees.ATTENDEE_STATUS_TENTATIVE -> "　？"; else -> ""
                    }
                })
        }
        // 作成者は出さない（スマホのデータには正しい作成者が無く、共有カレンダーではカレンダーの ID になるため）
        // 添付ファイルのリンクは下の「添付ファイル」欄に出すので、説明からは除く
        io.github.barista9409.calendar.google.AttachmentText.body(e.description).takeIf { it.isNotBlank() }
            ?.let { InfoRow(Icons.AutoMirrored.Outlined.Notes, it) }
        attachments()
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDuplicate) { Icon(Icons.Outlined.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text("複製") }
            if (writable) OutlinedButton(onClick = { if (e.isRecurring) askSeries = true else onDelete(io.github.barista9409.calendar.model.RecurScope.THIS) }) {
                Icon(Icons.Outlined.Delete, null, tint = scheme.error); Spacer(Modifier.width(6.dp)); Text("削除", color = scheme.error)
            }
        }
        if (!writable) Text("このカレンダーは閲覧のみです", fontSize = 12.sp, color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(24.dp))
    }
    if (askSeries) {
        RecurScopeDialog("繰り返しの予定の削除", onPick = { askSeries = false; onDelete(it) }, onDismiss = { askSeries = false })
    }
}

private fun recurrenceLabel(rrule: String): String = when {
    rrule.contains("FREQ=DAILY") -> "毎日"
    rrule.contains("FREQ=WEEKLY") -> "毎週"
    rrule.contains("FREQ=MONTHLY") -> "毎月"
    rrule.contains("FREQ=YEARLY") -> "毎年"
    else -> "繰り返し"
}

@Composable
private fun InfoRow(icon: ImageVector, text: String, sub: String? = null) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Column(Modifier.padding(start = 14.dp)) {
            Text(text, fontSize = 14.sp)
            if (sub != null) Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------- 繰り返し予定の範囲 ----------------

/** 繰り返し予定の範囲（この予定／これ以降／すべて）を選ぶダイアログ */
@Composable
fun RecurScopeDialog(title: String, onPick: (io.github.barista9409.calendar.model.RecurScope) -> Unit, onDismiss: () -> Unit) {
    var choice by remember { mutableStateOf(io.github.barista9409.calendar.model.RecurScope.THIS) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                io.github.barista9409.calendar.model.RecurScope.entries.forEach { s ->
                    Row(Modifier.fillMaxWidth().clickable { choice = s }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(selected = choice == s, onClick = { choice = s })
                        Text(s.label, fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(choice) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}
