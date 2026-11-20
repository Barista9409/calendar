package io.github.barista9409.calendar.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.github.barista9409.calendar.container
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.barista9409.calendar.model.CalendarInfo
import io.github.barista9409.calendar.model.ViewMode
import io.github.barista9409.calendar.ui.agenda.AgendaView
import io.github.barista9409.calendar.ui.month.MonthView
import io.github.barista9409.calendar.ui.sheet.DaySheetContent
import io.github.barista9409.calendar.ui.sheet.EventSheetContent
import io.github.barista9409.calendar.ui.sheet.SheetPanel
import io.github.barista9409.calendar.ui.timeline.SlotHighlight
import io.github.barista9409.calendar.ui.timeline.TimelineView
import io.github.barista9409.calendar.util.DateUtils
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun CalendarScreen(vm: CalendarViewModel, onOpenUpdate: () -> Unit, onOpenSync: () -> Unit, onOpenSettings: () -> Unit,
                   onSetupSync: () -> Unit = onOpenSync) {
    // 初めて開いたとき：予定の通知をどちらで受け取るかを聞く（家族に入れてもらったときに通知が来ない、を防ぐ）
    run {
        val prefs = androidx.compose.ui.platform.LocalContext.current.container.prefs
        var ask by remember { mutableStateOf(!prefs.notifyChoiceAsked && !prefs.appReminders) }
        if (ask) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = {},
                title = { Text("予定の通知をどちらで受け取りますか？") },
                text = {
                    Text("このアプリで受け取ると、バイブの長さや「止めるまで鳴らす」などを設定できます。\n" +
                        "その場合、二重に通知されないよう、Google カレンダーアプリの通知はオフにしてください。\n" +
                        "あとから 設定 → 通知 で変えられます。")
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        prefs.appReminders = true; prefs.notifyChoiceAsked = true; ask = false
                    }) { Text("このアプリで受け取る") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        prefs.appReminders = false; prefs.notifyChoiceAsked = true; ask = false
                    }) { Text("Google カレンダーで受け取る") }
                },
            )
        }
    }
    // 初めて開いたとき（通知の質問の後）：同期の準備が要るアカウントがあれば、1 回だけ案内する
    run {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val prefs = ctx.container.prefs
        var need by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            if (prefs.syncSetupAsked) return@LaunchedEffect
            need = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val sc = ctx.container.sync
                // 案内するのは、確実に分かる「スマホ全体の自動同期がオフ」のときだけ
                sc.googleAccounts().isNotEmpty() && !sc.facts().masterOn
            }
            if (!need) prefs.syncSetupAsked = true
        }
        if (need && prefs.notifyChoiceAsked) androidx.compose.material3.AlertDialog(
            onDismissRequest = {},
            title = { Text("Google カレンダーと同期する準備") },
            text = { Text("スマホ全体の自動同期がオフになっています。このままでは Google カレンダーの変更が自動では届きません。\n" +
                "［準備する］を押すと、オンにするか確認します。") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = {
                prefs.syncSetupAsked = true; need = false; onSetupSync()
            }) { Text("準備する") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = {
                prefs.syncSetupAsked = true; need = false
            }) { Text("あとで") } },
        )
    }
    val mode by vm.viewMode.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val selected by vm.selectedDate.collectAsStateWithLifecycle()
    val eventsByDay by vm.eventsByDay.collectAsStateWithLifecycle()
    val holidays by vm.holidays.collectAsStateWithLifecycle()
    val sheet by vm.sheet.collectAsStateWithLifecycle()
    val sheetSize by vm.sheetSize.collectAsStateWithLifecycle()
    val calendars by vm.calendars.collectAsStateWithLifecycle()
    val hidden by vm.hiddenCalendars.collectAsStateWithLifecycle()
    val writable by vm.writableCalendars.collectAsStateWithLifecycle()
    val attachState by vm.attachState.collectAsStateWithLifecycle()
    val uploading by vm.uploading.collectAsStateWithLifecycle()
    val guests by vm.guests.collectAsStateWithLifecycle()
    val attachPreview by androidx.compose.ui.platform.LocalContext.current.container.prefs.attachPreview.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> val s = sheet; if (uri != null && s is Sheet.Event) vm.attach(s.instance, uri) }

    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var header by remember { mutableStateOf(selected) }
    var preview by remember { mutableStateOf<OpenRequest.Preview?>(null) }
    var searching by remember { mutableStateOf(false) }
    val shareUri by vm.shareUri.collectAsStateWithLifecycle()
    val pendingRecur by vm.pendingRecur.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        vm.openRequests.collect { r ->
            if (r is OpenRequest.Preview) { preview = r; return@collect }
            val intent = when (r) {
                is OpenRequest.Preview -> return@collect
                is OpenRequest.File -> android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(r.uri, r.mime ?: "*/*")
                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                is OpenRequest.Url -> android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(r.url))
            }
            try { context.startActivity(intent) }
            catch (e: Exception) { snackbar.showSnackbar("開けるアプリが見つかりません") }
        }
    }

    LaunchedEffect(Unit) {
        vm.messages.collect { m ->
            when (m) {
                is UiMessage.Info -> launch { snackbar.showSnackbar(m.text) }
                is UiMessage.Deleted -> launch {
                    val r = snackbar.showSnackbar("「${m.pending.instance.title}」を削除しました", actionLabel = "元に戻す",
                        duration = SnackbarDuration.Short)
                    if (r == SnackbarResult.ActionPerformed) vm.undoDelete(m.pending.token) else vm.commitDelete(m.pending.token)
                }
            }
        }
    }
    BackHandler(enabled = sheet != null) {
        if (sheetSize == SheetSize.FULL && sheet !is Sheet.Edit) vm.setSheetSize(SheetSize.HALF) else vm.closeSheet()
    }
    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    // 予定を消す前に、添付ファイルも消すか聞く
    val askTrash by vm.askTrash.collectAsStateWithLifecycle()
    askTrash?.let { a ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { vm.answerTrash(null) },
            title = { Text("添付ファイルも削除しますか？") },
            text = { Text("この予定には添付ファイルが ${a.items.size} 件あります。\n" +
                a.items.joinToString("\n") { "・" + it.title } +
                "\n\n削除すると Google ドライブのゴミ箱に移ります（30 日間は戻せます）。") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { vm.answerTrash(false) }) { Text("ファイルは残す") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { vm.answerTrash(true) }) { Text("ファイルも削除") } },
        )
    }
    // ウィジェット・通知から開いたら、メニューを閉じて予定を見せる
    LaunchedEffect(Unit) { vm.closeDrawer.collect { drawer.snapTo(DrawerValue.Closed); vm.drawerClosed() } }
    // 同期の進み具合と完了
    val syncRun by vm.syncRun.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        vm.syncFinished.collect { r ->
            if (r.manual) launch { snackbar.showSnackbar(io.github.barista9409.calendar.ui.calendars.syncDoneMessage(r)) }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            AppDrawer(calendars, hidden,
                onToggle = vm::setCalendarVisible, onToggleAccount = vm::setAccountVisible,
                onSync = { vm.syncNow() },
                onOpenSync = { scope.launch { drawer.close() }; onOpenSync() },
                onUpdate = { scope.launch { drawer.close() }; onOpenUpdate() },
                onSettings = { scope.launch { drawer.close() }; onOpenSettings() })
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            floatingActionButton = {
                if (sheet == null) FloatingActionButton(onClick = { vm.newEvent() }, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Outlined.Add, contentDescription = "予定を追加")
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                TopBar(
                    title = when (mode) {
                        ViewMode.MONTH -> DateUtils.yearMonth(header)
                        ViewMode.WEEK -> DateUtils.weekTitle(DateUtils.startOfWeek(selected))
                        else -> DateUtils.yearMonth(selected)
                    },
                    onMenu = { scope.launch { drawer.open() } },
                    onToday = vm::goToday,
                    onSearch = { searching = true },
                    syncing = syncRun.running,
                )
                ViewSwitcher(mode, vm::setViewMode)
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val fraction by vm.sheetFraction.collectAsStateWithLifecycle()
                    var dragging by remember { mutableStateOf(false) }
                    val target = when {
                        sheet == null || sheet is Sheet.Edit -> 0.dp
                        sheetSize == SheetSize.FULL -> maxHeight
                        sheet is Sheet.Edit && !dragging -> maxHeight * maxOf(fraction, 0.55f)
                        else -> maxHeight * fraction
                    }
                    val animated by animateDpAsState(target, label = "sheet")
                    // ドラッグ中は指に追従（アニメーションなし）
                    val sheetH = if (dragging) target else animated
                    val totalPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxHeight.toPx() }
                    Column(Modifier.fillMaxSize()) {
                        // 下に引っぱると Google と同期（Google カレンダーと同じ操作）
                        PullSync(syncRun.running && syncRun.manual, onRefresh = vm::syncNow, modifier = Modifier.fillMaxWidth().weight(1f)) {
                            val highlight = (sheet as? Sheet.Edit)?.draft?.takeIf { it.eventId == null && !it.allDay }
                                ?.let { SlotHighlight(it.start, it.end) }
                            val selectedEventId = (sheet as? Sheet.Event)?.instance?.eventId
                            when (mode) {
                                ViewMode.MONTH -> MonthView(
                                    today, if (sheet != null) selected else null, eventsByDay, holidays, vm.jumpRequests,
                                    onDayClick = vm::openDay,
                                    onVisibleChange = { top ->
                                        header = top.plusDays(6)
                                        vm.ensureRange(top.minusWeeks(2), top.plusWeeks(10))
                                    },
                                )
                                ViewMode.WEEK, ViewMode.DAY -> androidx.compose.runtime.key(mode) { TimelineView(
                                    days = if (mode == ViewMode.WEEK) 7 else 1,
                                    selected = selected, today = today, eventsByDay = eventsByDay, holidays = holidays,
                                    zone = vm.zone, highlight = highlight, selectedEventId = selectedEventId,
                                    jumpRequests = vm.jumpRequests,
                                    onPageChange = { first ->
                                        if (mode == ViewMode.WEEK) {
                                            // 週の先頭は日曜。曜日（日=0…土=6）を保ったまま移動する
                                            if (DateUtils.startOfWeek(selected) != first) vm.select(first.plusDays((selected.dayOfWeek.value % 7).toLong()))
                                        } else if (selected != first) vm.select(first)
                                        vm.ensureRange(first.minusWeeks(1), first.plusWeeks(2))
                                    },
                                    onEmptyTap = { t -> vm.newEvent(t) },
                                    onEventTap = vm::openEvent,
                                    onEventMove = vm::moveEvent,
                                    onHeaderDateTap = { d -> vm.select(d); vm.setViewMode(ViewMode.DAY) },
                                ) }
                                ViewMode.AGENDA -> {
                                    LaunchedEffect(today) { vm.ensureRange(today, today.plusMonths(6)) }
                                    AgendaView(today, eventsByDay, vm.zone, vm.jumpRequests, vm::openEvent,
                                        onDayTap = { d -> vm.select(d); vm.setViewMode(ViewMode.DAY) })
                                }
                            }
                        }
                        if (sheetH > 0.dp) {
                            SheetPanel(sheetSize, vm::setSheetSize, vm::closeSheet, Modifier.height(sheetH),
                                onDrag = { dy -> dragging = true; vm.dragSheet(dy / totalPx) },
                                onDragEnd = { dragging = false; vm.endSheetDrag() },
                                canGrow = sheetSize != SheetSize.FULL && fraction < 0.999f) {
                                when (val s = sheet) {
                                    is Sheet.Day -> DaySheetContent(
                                        s.date, eventsByDay[s.date].orEmpty(), vm.zone,
                                        calendarName = { id -> vm.calendarById(id)?.name.orEmpty() },
                                        onQuickAdd = { title -> vm.newEvent(DateUtils.defaultStart(s.date), title) },
                                        onEventClick = vm::openEvent,
                                    )
                                    is Sheet.Event -> EventSheetContent(
                                        s.instance, vm.calendarById(s.instance.calendarId), vm.zone,
                                        onEdit = { vm.editEvent(s.instance) },
                                        onDuplicate = { vm.editEvent(s.instance, asCopy = true) },
                                        onDelete = { scope -> vm.requestDelete(s.instance, scope) },
                                        guests = guests,
                                        attachments = {
                                            io.github.barista9409.calendar.ui.sheet.AttachmentsSection(
                                                attachState, uploading, s.instance.isRecurring,
                                                onPick = { picker.launch(arrayOf("*/*")) },
                                                onOpen = { a -> vm.openAttachment(s.instance, a) },
                                                onRemove = { a -> vm.removeAttachment(s.instance, a) },
                                                onLink = vm::linkAccount,
                                                thumbnail = vm::attachmentThumbnail,
                                                showPreview = attachPreview,
                                            )
                                        },
                                    )
                                    is Sheet.Edit, null -> {}
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    // 繰り返し予定の変更範囲
    if (pendingRecur != null) {
        io.github.barista9409.calendar.ui.sheet.RecurScopeDialog("繰り返しの予定の変更", onPick = vm::applyRecur, onDismiss = vm::cancelRecur)
    }
    // 検索
    if (searching) {
        io.github.barista9409.calendar.ui.search.SearchScreen("予定を検索", vm::search, vm.zone,
            onPick = { e -> searching = false; vm.openEventFull(e.eventId, e.begin) }, onClose = { searching = false })
    }
    // 他のアプリから共有されたファイル：添付先の予定を選ぶ
    shareUri?.let { uri ->
        io.github.barista9409.calendar.ui.search.SearchScreen("添付先の予定を検索（空欄で今後の予定）", vm::searchAttachTargets, vm.zone,
            onPick = { e -> vm.attachShared(e, uri) }, onClose = vm::cancelShare)
    }
    // 添付のプレビュー（PDF・画像）
    preview?.let { p ->
        io.github.barista9409.calendar.ui.preview.PreviewScreen(
            p.file, p.mime, p.title, onClose = { preview = null },
            onOpenExternal = {
                runCatching {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(p.uri, p.mime ?: "*/*")
                        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }
            },
            onShare = {
                runCatching {
                    context.startActivity(android.content.Intent.createChooser(
                        android.content.Intent(android.content.Intent.ACTION_SEND).setType(p.mime ?: "*/*")
                            .putExtra(android.content.Intent.EXTRA_STREAM, p.uri)
                            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION), "共有"))
                }
            },
        )
    }
    // 予定の作成・編集は全画面
    (sheet as? Sheet.Edit)?.let { s ->
        io.github.barista9409.calendar.ui.edit.EventEditScreen(
            // 選べるカレンダーは、メニューで表示しているものだけ（いま入っているカレンダーは非表示でも残す）
            draft = s.draft, calendars = io.github.barista9409.calendar.model.pickableCalendars(writable, hidden, s.draft.calendarId),
            loadColors = vm::eventColors,
            onChange = vm::updateDraft, onSave = { vm.save(s.draft) }, onClose = vm::closeSheet,
        )
    }
}

@Composable
private fun TopBar(title: String, onMenu: () -> Unit, onToday: () -> Unit, onSearch: () -> Unit, syncing: Boolean = false) {
    // 左：メニューと年月、中央：今日、右上：検索
    Box(Modifier.fillMaxWidth().height(56.dp)) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMenu) { Icon(Icons.Outlined.Menu, contentDescription = "メニュー") }
            Box(Modifier.weight(1f)) { ShrinkText(title, 20f, 13f) }
            Spacer(Modifier.width(72.dp)) // 中央の「今日」の場所
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, contentDescription = "検索") }
        }
        OutlinedButton(onClick = onToday, shape = RoundedCornerShape(8.dp), modifier = Modifier.align(Alignment.Center),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp)) {
            Text("今日", fontWeight = FontWeight.Bold)
        }
        // 同期中は下の線で知らせる
        if (syncing) androidx.compose.material3.LinearProgressIndicator(
            Modifier.fillMaxWidth().height(2.dp).align(Alignment.BottomCenter))
    }
}

/** 入りきらないときは文字を小さくして 1 行に収める */
@Composable
private fun ShrinkText(text: String, maxSp: Float, minSp: Float) {
    var size by remember(text) { mutableStateOf(maxSp) }
    Text(text, fontSize = size.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
        overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
        onTextLayout = { r -> if (r.hasVisualOverflow && size > minSp) size -= 1f })
}

/** 日・週・月・予定のワンタッチ切替 */
@Composable
private fun ViewSwitcher(mode: ViewMode, onChange: (ViewMode) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceVariant).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ViewMode.entries.forEach { m ->
            val sel = m == mode
            Box(
                Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(9.dp))
                    .background(if (sel) scheme.surface else Color.Transparent)
                    .clickable { onChange(m) }
                    .semantics { role = Role.Tab; selected = sel },
                contentAlignment = Alignment.Center,
            ) {
                Text(m.label, fontSize = 14.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                    color = if (sel) scheme.primary else scheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * 左から出るメニュー：「何を見るか」（カレンダーのオン・オフ）と、同期・設定だけ。
 * アカウントの管理などは設定の「アカウントとカレンダー」にまとめる。
 */
@Composable
private fun AppDrawer(
    calendars: List<CalendarInfo>,
    hidden: Set<Long>,
    onToggle: (Long, Boolean) -> Unit,
    onToggleAccount: (String, Boolean) -> Unit,
    onSync: () -> Unit,
    onOpenSync: () -> Unit,
    onUpdate: () -> Unit,
    onSettings: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val context = androidx.compose.ui.platform.LocalContext.current
    val accounts = calendars.map { it.accountName }.distinct()
    io.github.barista9409.calendar.ui.theme.MenuTheme {
    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) {
            Text("Calendar", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            accounts.forEach { a ->
                val list = calendars.filter { it.accountName == a }
                val shown = list.count { it.id !in hidden }
                // アカウントの見出し：押すと、そのアカウントのカレンダーをまとめてオン・オフ
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { onToggleAccount(a, shown == 0) }
                        .padding(start = 8.dp, end = 16.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.TriStateCheckbox(
                        state = when (shown) { 0 -> androidx.compose.ui.state.ToggleableState.Off
                            list.size -> androidx.compose.ui.state.ToggleableState.On
                            else -> androidx.compose.ui.state.ToggleableState.Indeterminate },
                        onClick = { onToggleAccount(a, shown == 0) })
                    Text(a, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                list.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { onToggle(c.id, c.id in hidden) }.padding(start = 24.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = c.id !in hidden, onCheckedChange = { onToggle(c.id, it) },
                            colors = CheckboxDefaults.colors(checkedColor = Color(c.color), uncheckedColor = Color(c.color)))
                        Text(c.name, fontSize = 14.sp)
                    }
                }
            }
            if (calendars.isEmpty()) {
                Text("カレンダーが見つかりません。設定 →「アカウントとカレンダー」でアカウントを確認してください。",
                    fontSize = 13.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            // 同期：1 行だけ。押すと今すぐ同期、同期中は終わるまで「同期しています…」とくるくる（同期中の表示はここだけ）
            val syncCenter = context.container.sync
            var problem by remember { mutableStateOf<String?>(null) }
            var lastLine by remember { mutableStateOf("") }
            val run by syncCenter.run.collectAsStateWithLifecycle()
            LaunchedEffect(accounts, run.running) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val sum = io.github.barista9409.calendar.data.SyncSummary.of(syncCenter.facts())
                    problem = if (sum.isError) sum.title else null
                    lastLine = io.github.barista9409.calendar.data.lastSyncLine(syncCenter.lastSyncAll())
                }
            }
            NavigationDrawerItem(
                label = {
                    Column {
                        Text(if (run.running) "同期しています…" else "同期")
                        when {
                            run.running -> {}
                            problem != null -> Text("$problem（押して直す）", fontSize = 12.sp, color = scheme.error)
                            else -> Text(lastLine, fontSize = 12.sp, color = scheme.onSurfaceVariant)
                        }
                    }
                },
                icon = {
                    if (run.running) androidx.compose.material3.CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    else Icon(if (problem != null) Icons.Outlined.SyncProblem else Icons.Outlined.Sync, null,
                        tint = if (problem != null) scheme.error else scheme.onSurfaceVariant)
                },
                selected = false,
                onClick = { when { run.running -> {}; problem != null -> onOpenSync(); else -> onSync() } },
                modifier = Modifier.padding(horizontal = 12.dp))
            NavigationDrawerItem(label = { Text("設定") }, icon = { Icon(Icons.Outlined.Settings, null) },
                selected = false, onClick = onSettings, modifier = Modifier.padding(horizontal = 12.dp))
            // 新しい版があるときだけ出す（いつもの入口は 設定 →「このアプリについて」）
            val update by context.container.updater.state.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) {
                if (update is io.github.barista9409.calendar.update.UpdateState.Idle) runCatching { context.container.updater.check() }
            }
            if (update is io.github.barista9409.calendar.update.UpdateState.Available) {
                NavigationDrawerItem(label = { Text("アップデートがあります") }, icon = { Icon(Icons.Outlined.SystemUpdate, null, tint = scheme.primary) },
                    selected = false, onClick = onUpdate, modifier = Modifier.padding(horizontal = 12.dp))
            }
        }
    }
    }
}

/** 端末に Google アカウントを追加する画面 */
fun addAccountIntent(): android.content.Intent =
    android.content.Intent(android.provider.Settings.ACTION_ADD_ACCOUNT)
        .putExtra(android.provider.Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))

/** 下に引っぱって同期 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PullSync(syncing: Boolean, onRefresh: () -> Unit, modifier: Modifier, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(isRefreshing = syncing, onRefresh = onRefresh, modifier = modifier, content = content)
}
