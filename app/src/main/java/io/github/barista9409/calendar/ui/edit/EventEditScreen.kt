package io.github.barista9409.calendar.ui.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PersonAdd
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
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.barista9409.calendar.model.CalendarInfo
import io.github.barista9409.calendar.model.EventColor
import io.github.barista9409.calendar.model.EventDraft
import io.github.barista9409.calendar.util.DateUtils
import io.github.barista9409.calendar.util.RRule
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

private val REMINDERS = listOf<Pair<Int?, String>>(null to "通知なし", 0 to "予定の時刻", 5 to "5分前", 10 to "10分前",
    30 to "30分前", 60 to "1時間前", 1440 to "1日前")

/**
 * 予定の作成・編集（全画面）。Google カレンダーと同じく、× と「保存」、タイトル、日時、各項目を 1 画面で入力する。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EventEditScreenContent(
    draft: EventDraft,
    calendars: List<CalendarInfo>,
    loadColors: suspend (Long) -> List<EventColor>,
    onChange: (EventDraft) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    var initial by remember { mutableStateOf(draft) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf<Boolean?>(null) }
    var pickTime by remember { mutableStateOf<Boolean?>(null) }
    var guestInput by remember { mutableStateOf("") }
    val cal = calendars.firstOrNull { it.id == draft.calendarId }
    val colors by produceState(emptyList<EventColor>(), draft.calendarId) {
        value = draft.calendarId?.let { runCatching { loadColors(it) }.getOrDefault(emptyList()) }.orEmpty()
    }
    LaunchedEffect(Unit) { if (draft.eventId == null && draft.title.isEmpty()) runCatching { focus.requestFocus() } }
    val dirty = draft != initial

    fun close() { if (dirty) confirmDiscard = true else onClose() }
    BackHandler { close() }

    Surface(Modifier.fillMaxSize(), color = scheme.surface) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            // 上部バー：× と保存
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { close() }) { Text("キャンセル", fontSize = 16.sp) }
                Spacer(Modifier.weight(1f))
                Button(onClick = onSave, enabled = draft.calendarId != null, modifier = Modifier.padding(end = 8.dp)) { Text("保存") }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                TextField(
                    value = draft.title, onValueChange = { onChange(draft.copy(title = it)) },
                    placeholder = { Text("タイトルを追加", fontSize = 24.sp) },
                    textStyle = TextStyle(fontSize = 24.sp),
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth().padding(start = 52.dp, end = 12.dp).focusRequester(focus),
                )
                Divider()
                // 終日
                ItemRow(Icons.Outlined.Schedule) {
                    Text("終日", Modifier.weight(1f), fontSize = 16.sp)
                    Switch(checked = draft.allDay, onCheckedChange = {
                        onChange(draft.copy(allDay = it, reminderMinutes = if (it) null else 30))
                    })
                }
                DateTimeLine(draft.start.toLocalDate(), draft.start.toLocalTime(), draft.allDay, true,
                    { pickDate = true }, { pickTime = true })
                DateTimeLine(draft.end.toLocalDate(), draft.end.toLocalTime(), draft.allDay, true,
                    { pickDate = false }, { pickTime = false })
                // 繰り返し（Google カレンダーと同じ候補＋カスタム。既存の繰り返し予定は保存時に範囲を選ぶ）
                run {
                    var open by remember { mutableStateOf(false) }
                    var custom by remember { mutableStateOf(false) }
                    val sd = draft.start.toLocalDate()
                    val current = RRule.parse(draft.rrule)
                    Box {
                        ItemRow(Icons.Outlined.Repeat, onClick = { open = true }) { Text(current?.label(sd) ?: "繰り返さない", fontSize = 16.sp) }
                        DropdownMenu(open, { open = false }) {
                            DropdownMenuItem(text = { Text("繰り返さない") }, onClick = { open = false; onChange(draft.copy(rrule = null)) })
                            RRule.presets(sd).forEach { r ->
                                DropdownMenuItem(text = { Text(r.label(sd)) },
                                    onClick = { open = false; onChange(draft.copy(rrule = r.toRule(sd, draft.allDay))) })
                            }
                            DropdownMenuItem(text = { Text("カスタム…") }, onClick = { open = false; custom = true })
                        }
                    }
                    if (custom) {
                        CustomRepeatDialog(sd, current, onDone = { r -> custom = false; onChange(draft.copy(rrule = r.toRule(sd, draft.allDay))) },
                            onDismiss = { custom = false })
                    }
                }
                Divider()
                // 通知
                run {
                    var open by remember { mutableStateOf(false) }
                    Box {
                        ItemRow(Icons.Outlined.NotificationsNone, onClick = { open = true }) {
                            Text(REMINDERS.firstOrNull { it.first == draft.reminderMinutes }?.second ?: "${draft.reminderMinutes}分前", fontSize = 16.sp)
                        }
                        DropdownMenu(open, { open = false }) {
                            REMINDERS.forEach { (m, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { open = false; onChange(draft.copy(reminderMinutes = m)) }) }
                        }
                    }
                }
                Divider()
                // 場所
                ItemRow(Icons.Outlined.LocationOn) {
                    TextField(draft.location, { onChange(draft.copy(location = it)) }, placeholder = { Text("場所を追加") }, singleLine = true,
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                        modifier = Modifier.weight(1f))
                }
                Divider()
                // 説明
                ItemRow(Icons.AutoMirrored.Outlined.Notes, top = true) {
                    TextField(draft.description, { onChange(draft.copy(description = it)) }, placeholder = { Text("説明を追加") },
                        minLines = 2,
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                        modifier = Modifier.weight(1f))
                }
                Divider()
                // 添付ファイル（選んだファイルは保存したあとにアップロードする）
                AttachRow(draft, onChange)
                Divider()
                // カレンダー
                run {
                    var open by remember { mutableStateOf(false) }
                    Box {
                        ItemRow(Icons.Outlined.CalendarMonth, onClick = { if (draft.eventId == null || !draft.isRecurring) open = true }) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(Color(cal?.color ?: 0xFF888888.toInt())))
                            Column(Modifier.padding(start = 10.dp)) {
                                Text(cal?.name ?: "カレンダーを選択", fontSize = 16.sp)
                                cal?.let { Text(it.accountName, fontSize = 12.sp, color = scheme.onSurfaceVariant) }
                            }
                        }
                        DropdownMenu(open, { open = false }) {
                            calendars.forEach { c ->
                                DropdownMenuItem(
                                    text = { Column { Text(c.name); Text(c.accountName, fontSize = 11.sp, color = scheme.onSurfaceVariant) } },
                                    leadingIcon = { Box(Modifier.size(14.dp).clip(CircleShape).background(Color(c.color))) },
                                    onClick = { open = false; onChange(draft.copy(calendarId = c.id, colorKey = null)) },
                                )
                            }
                        }
                    }
                }
                Divider()
                // ゲスト
                ItemRow(Icons.Outlined.PersonAdd) {
                    TextField(guestInput, { guestInput = it }, placeholder = { Text("ゲストを追加（メールアドレス）") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            val e = guestInput.trim()
                            if (e.contains("@") && e !in draft.guests) onChange(draft.copy(guests = draft.guests + e))
                            guestInput = ""
                        }),
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                        modifier = Modifier.weight(1f))
                }
                if (draft.guests.isNotEmpty()) {
                    FlowRow(Modifier.padding(start = 56.dp, end = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        draft.guests.forEach { g ->
                            InputChip(selected = false, onClick = { onChange(draft.copy(guests = draft.guests - g)) }, label = { Text(g) },
                                trailingIcon = { Icon(Icons.Outlined.Close, contentDescription = "削除", modifier = Modifier.size(16.dp)) })
                        }
                    }
                }
                Divider()
                // 予定の色（タップで色の一覧を開く）
                run {
                    var open by remember { mutableStateOf(false) }
                    val selected = colors.firstOrNull { it.key == draft.colorKey }
                    ItemRow(Icons.Outlined.Palette, onClick = { open = true }) {
                        Box(Modifier.size(18.dp).clip(CircleShape).background(Color(selected?.color ?: cal?.color ?: 0xFF888888.toInt())))
                        Text(if (selected == null) "カレンダーの色" else "予定の色", Modifier.padding(start = 12.dp), fontSize = 16.sp)
                    }
                    if (open) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { open = false },
                            title = { Text("予定の色") },
                            text = {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ColorDot(cal?.color ?: 0xFF888888.toInt(), draft.colorKey == null, "カレンダーの色") { open = false; onChange(draft.copy(colorKey = null)) }
                                    colors.forEach { c -> ColorDot(c.color, draft.colorKey == c.key, "予定の色") { open = false; onChange(draft.copy(colorKey = c.key)) } }
                                }
                            },
                            confirmButton = { androidx.compose.material3.TextButton(onClick = { open = false }) { Text("閉じる") } },
                        )
                    }
                }
                Divider()
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("変更を破棄しますか？") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onClose() }) { Text("破棄") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("編集を続ける") } },
        )
    }

    pickDate?.let { isStart ->
        val base = if (isStart) draft.start.toLocalDate() else draft.end.toLocalDate()
        val st = rememberDatePickerState(initialSelectedDateMillis = base.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = null },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        onChange(if (isStart) {
                            val dur = Duration.between(draft.start, draft.end)
                            val ns = d.atTime(draft.start.toLocalTime())
                            draft.copy(start = ns, end = ns.plus(dur), rrule = RRule.parse(draft.rrule)?.toRule(d, draft.allDay) ?: draft.rrule)
                        } else {
                            val ne = d.atTime(draft.end.toLocalTime())
                            draft.copy(end = if (ne.isBefore(draft.start)) draft.start else ne)
                        })
                    }
                    pickDate = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = null }) { Text("キャンセル") } },
        ) { DatePicker(st) }
    }
    pickTime?.let { isStart ->
        val base = if (isStart) draft.start.toLocalTime() else draft.end.toLocalTime()
        val st = rememberTimePickerState(base.hour, base.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = null },
            confirmButton = {
                TextButton(onClick = {
                    val t = LocalTime.of(st.hour, st.minute)
                    onChange(if (isStart) {
                        val dur = Duration.between(draft.start, draft.end)
                        val ns = draft.start.toLocalDate().atTime(t); draft.copy(start = ns, end = ns.plus(dur))
                    } else {
                        var ne = draft.end.toLocalDate().atTime(t)
                        if (!ne.isAfter(draft.start)) ne = if (draft.end.toLocalDate() == draft.start.toLocalDate()) ne.plusDays(1) else draft.start
                        draft.copy(end = ne)
                    })
                    pickTime = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickTime = null }) { Text("キャンセル") } },
            text = { TimePicker(st) },
        )
    }
}

@Composable
private fun Divider() = HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)

@Composable
private fun ItemRow(
    icon: ImageVector, onClick: (() -> Unit)? = null, top: Boolean = false,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 12.dp),
        verticalAlignment = if (top) Alignment.Top else Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = if (top) 16.dp else 0.dp).size(24.dp))
        Spacer(Modifier.width(16.dp))
        content()
    }
}

@Composable
private fun DateTimeLine(date: LocalDate, time: LocalTime, allDay: Boolean, enabled: Boolean, onDate: () -> Unit, onTime: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 56.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(DateUtils.date(date), fontSize = 16.sp,
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(enabled = enabled, onClick = onDate).padding(vertical = 10.dp))
        if (!allDay) Text(DateUtils.time(time), fontSize = 16.sp,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = enabled, onClick = onTime).padding(10.dp))
    }
}

@Composable
private fun ColorDot(argb: Int, selected: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(30.dp).clip(CircleShape).background(Color(argb))
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
    )
}


/** ポップアップの配色で表示 */
@Composable
fun EventEditScreen(draft: EventDraft, calendars: List<CalendarInfo>, loadColors: suspend (Long) -> List<EventColor>, onChange: (EventDraft) -> Unit, onSave: () -> Unit, onClose: () -> Unit) {
    io.github.barista9409.calendar.ui.theme.PopupTheme { EventEditScreenContent(draft, calendars, loadColors, onChange, onSave, onClose) }
}

/** 入力画面の「添付ファイル」：すでに付いているファイルと、保存時にアップロードするファイルを並べる */
@Composable
private fun AttachRow(draft: io.github.barista9409.calendar.model.EventDraft, onChange: (io.github.barista9409.calendar.model.EventDraft) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        val added = uris.map { uri ->
            // 保存までの間もファイルを読めるようにしておく
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull() ?: "ファイル"
            io.github.barista9409.calendar.model.PendingFile(uri.toString(), name)
        }
        if (added.isNotEmpty()) onChange(draft.copy(pendingFiles = draft.pendingFiles + added))
    }
    val existing = io.github.barista9409.calendar.google.AttachmentText.parse(draft.attachmentBlock)
    ItemRow(Icons.Outlined.AttachFile, onClick = { picker.launch(arrayOf("*/*")) }) {
        Text("ファイルを添付", fontSize = 16.sp, color = scheme.primary)
    }
    if (existing.isNotEmpty() || draft.pendingFiles.isNotEmpty()) {
        Column(Modifier.padding(start = 56.dp, end = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            existing.forEach { a ->
                Text("・${a.title}（添付済み）", fontSize = 14.sp, color = scheme.onSurfaceVariant, maxLines = 1)
            }
            draft.pendingFiles.forEach { f ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("・${f.name}", fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onChange(draft.copy(pendingFiles = draft.pendingFiles - f)) }) {
                        Icon(Icons.Outlined.Close, contentDescription = "${f.name}を外す", tint = scheme.onSurfaceVariant)
                    }
                }
            }
            if (draft.pendingFiles.isNotEmpty()) Text("［保存］を押すと Google ドライブにアップロードして添付します。", fontSize = 12.sp,
                color = scheme.onSurfaceVariant)
        }
    }
}
