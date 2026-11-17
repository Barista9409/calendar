package io.github.barista9409.calendar.ui.calendars

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.barista9409.calendar.container
import io.github.barista9409.calendar.data.SyncSummary
import io.github.barista9409.calendar.google.GoogleSync
import io.github.barista9409.calendar.model.CalendarInfo
import io.github.barista9409.calendar.util.ColorUtils

/** カレンダーの管理：表示・非表示、Google 連携（添付ファイル用）、名前・色・共有は Google カレンダーの設定画面へ */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarsScreen(onBack: () -> Unit, autoFix: Boolean = false, vm: CalendarsViewModel = viewModel()) {
    val calendars by vm.calendars.collectAsStateWithLifecycle()
    val linked by vm.linked.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val hidden by LocalContext.current.container.prefs.hiddenCalendars.collectAsStateWithLifecycle()
    val hiddenAccounts by vm.hiddenAccounts.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val unsynced by vm.unsynced.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val run by vm.syncRun.collectAsStateWithLifecycle()
    val fixStep by vm.fixStep.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var open by remember { mutableStateOf<CalendarInfo?>(null) }
    var trouble by remember { mutableStateOf(false) }
    var topMenu by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    // 初回の案内から来たときは、すぐに直す
    LaunchedEffect(autoFix) { if (autoFix) vm.fixAll() }
    // アカウントを見えるようにする（Android 標準の「アカウントの選択」）
    val chooser = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { vm.chosen() }
    LaunchedEffect(fixStep) {
        val st = fixStep
        if (st is CalendarsViewModel.FixStep.Choose)
            runCatching { chooser.launch(context.container.sync.chooseAccountIntent(st.account)) }.onFailure { vm.chosen() }
    }
    fun browse(url: String) = runCatching {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
    }
    fun openAccountSettings(account: String) {
        // そのアカウントの「アカウントの同期」画面を直接開く（パスワードなどの画面に迷わないように）
        val direct = android.content.Intent("android.settings.ACCOUNT_SYNC_SETTINGS")
            .putExtra("account", android.accounts.Account(account, "com.google"))
        runCatching { context.startActivity(direct) }.onFailure {
            runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_SYNC_SETTINGS)) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("カレンダーと同期") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "戻る") }
            }, actions = {
                TextButton(onClick = { context.startActivity(io.github.barista9409.calendar.ui.addAccountIntent()) }) {
                    Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(4.dp)); Text("アカウント")
                }
                Box {
                    IconButton(onClick = { topMenu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "その他") }
                    DropdownMenu(topMenu, { topMenu = false }) {
                        DropdownMenuItem(text = { Text("困ったとき（詳しい状態）") }, onClick = { topMenu = false; trouble = true })
                    }
                }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (busy || run.running) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.height(4.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                item(key = "summary") {
                    SummaryCard(summary, busy, onSync = vm::syncNow, onFix = vm::fixAll)
                }
                // 同期していないカレンダーしかないアカウントも出す
                val groups = calendars.groupBy { it.accountName } + unsynced.map { it.third }.distinct()
                    .filter { a -> calendars.none { it.accountName == a } }.associateWith { emptyList<CalendarInfo>() }
                groups.forEach { (account, list) ->
                    val isGoogle = list.any { it.isGoogle } || unsynced.any { it.third == account }
                    val isLinked = account in linked
                    val accHidden = account in hiddenAccounts
                    val st = sync[account]
                    item(key = "h$account") {
                        AccountHeader(account, isGoogle, isLinked, busy, accHidden,
                            problem = st?.takeIf { !accHidden && (it.isError || !it.access) }?.let {
                                if (it.isError) "同期が止まっています" else "同期の状態を確認できません"
                            },
                            onLink = { vm.link(account) }, onUnlink = { vm.unlink(account) },
                            onHide = { vm.setAccountHidden(account, it) },
                            onRemoveFromDevice = {
                                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_SYNC_SETTINGS))
                            })
                    }
                    if (!accHidden) items(list, key = { it.id }) { c ->
                        CalendarRow(c, c.id !in hidden, onToggle = { vm.setVisible(c, it) }, onClick = { open = c })
                    }
                    if (isGoogle && !accHidden) item(key = "u$account") {
                        AddCalendars(unsynced.filter { it.third == account }, busy,
                            onEnable = { (cid, name, acc) -> vm.enableSync(cid, name, acc) },
                            onCreate = { browse(GoogleSync.createCalendarUrl(account)) })
                    }
                    item(key = "d$account") { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                }
            }
        }
    }

    when (fixStep) {
        CalendarsViewModel.FixStep.AskMaster -> AlertDialog(
            onDismissRequest = { vm.answerMaster(false) },
            title = { Text("スマホ全体の自動同期をオンにします") },
            text = { Text("Google カレンダーの変更が自動で届くようになります。Gmail など、ほかのアプリの自動同期もオンになります。") },
            confirmButton = { TextButton(onClick = { vm.answerMaster(true) }) { Text("オンにする") } },
            dismissButton = { TextButton(onClick = { vm.answerMaster(false) }) { Text("オンにしない") } },
        )
        else -> {}
    }

    if (trouble) TroubleDialog(sync.values.filter { it.account !in hiddenAccounts }, onClose = { trouble = false },
        onOpenSettings = { openAccountSettings(it) })

    open?.let { cal ->
        ModalBottomSheet(onDismissRequest = { open = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            CalendarSettings(cal, onOpenGoogle = { browse(GoogleSync.calendarSettingsUrl(cal)) }, onClose = { open = null })
        }
    }
}

/** 一番上のまとめ：最新か・同期中か・送信待ちか・止まっているか、だけを出す */
@Composable
private fun SummaryCard(s: io.github.barista9409.calendar.data.SyncSummary, busy: Boolean, onSync: () -> Unit, onFix: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bad = s.kind == SyncSummary.Kind.STOPPED
    val bg = when (s.kind) { SyncSummary.Kind.STOPPED -> scheme.errorContainer; SyncSummary.Kind.CHECK -> scheme.tertiaryContainer; else -> scheme.surfaceVariant }
    val fg = when (s.kind) { SyncSummary.Kind.STOPPED -> scheme.onErrorContainer; SyncSummary.Kind.CHECK -> scheme.onTertiaryContainer; else -> scheme.onSurface }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(16.dp))
            .background(bg).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            when (s.kind) {
                SyncSummary.Kind.SYNCING -> androidx.compose.material3.CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                SyncSummary.Kind.OK -> Icon(Icons.Outlined.CheckCircle, null, tint = scheme.primary)
                SyncSummary.Kind.PENDING -> Icon(Icons.Outlined.CloudUpload, null, tint = scheme.primary)
                else -> Icon(Icons.Outlined.SyncProblem, null, tint = fg)
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(s.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = fg)
            if (s.sub.isNotBlank()) Text(s.sub, fontSize = 12.sp, color = fg.copy(alpha = 0.8f))
        }
        if (s.needsFix) androidx.compose.material3.Button(onClick = onFix, enabled = !busy) { Text("直す") }
        else OutlinedButton(onClick = onSync, enabled = !busy && s.kind != SyncSummary.Kind.SYNCING) { Text("今すぐ同期") }
    }
    if (bad) Spacer(Modifier.height(0.dp))
}

/** 困ったとき：アカウントごとの詳しい状態（問い合わせのときに見る） */
@Composable
private fun TroubleDialog(list: List<io.github.barista9409.calendar.data.SyncStatus>, onClose: () -> Unit, onOpenSettings: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("困ったとき（詳しい状態）") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("予定のやりとりは、スマホの Google の同期機能が行います。うまくいかないときは、このスクリーンショットを送ってください。",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                list.forEach { st ->
                    Text(st.account, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(st.message, fontSize = 12.sp)
                    Text(st.lastLine(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    st.details.forEach { (k, v) ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(k, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text(v, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    TextButton(onClick = { onOpenSettings(st.account) }) { Text("スマホの同期の設定を開く") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("閉じる") } },
    )
}

/** ほかのカレンダーを追加（Google カレンダーにはあるが、スマホに同期していないもの）・新しく作る */
@Composable
private fun AddCalendars(items: List<Triple<Long, String, String>>, busy: Boolean,
                         onEnable: (Triple<Long, String, String>) -> Unit, onCreate: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        if (items.isNotEmpty()) {
            TextButton(onClick = { open = !open }, modifier = Modifier.padding(start = 12.dp)) {
                Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp))
                Text("ほかのカレンダーを追加（" + items.joinToString("・") { it.second } + "）")
            }
            if (open) Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Google カレンダーにはあるけれど、このスマホに入れていないカレンダーです。［追加］するとこのアプリにも表示されます。",
                    fontSize = 12.sp, color = scheme.onSurfaceVariant)
                items.forEach { item ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(item.second, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { onEnable(item) }, enabled = !busy) { Text("追加") }
                    }
                }
            }
        }
        TextButton(onClick = onCreate, modifier = Modifier.padding(start = 12.dp)) {
            Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text("新しいカレンダー（Google カレンダーで作成）")
        }
    }
}

@Composable
private fun AccountHeader(
    account: String, isGoogle: Boolean, linked: Boolean, busy: Boolean, hidden: Boolean,
    problem: String?,
    onLink: () -> Unit, onUnlink: () -> Unit,
    onHide: (Boolean) -> Unit, onRemoveFromDevice: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(account, fontWeight = FontWeight.Bold, color = if (hidden) scheme.onSurfaceVariant else scheme.onSurface)
                if (hidden) Text("このアプリでは非表示", fontSize = 12.sp, color = scheme.onSurfaceVariant)
                else if (isGoogle && linked) Text("Google と連携済み（添付ファイルが使えます）", fontSize = 12.sp, color = scheme.primary)
                if (!hidden && problem != null) Text("⚠ $problem（上の［直す］で直せます）", fontSize = 12.sp, color = scheme.error)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "アカウントのメニュー") }
                DropdownMenu(menu, { menu = false }) {
                    if (hidden) DropdownMenuItem(text = { Text("このアプリで表示する") }, onClick = { menu = false; onHide(false) })
                    else DropdownMenuItem(text = { Text("このアプリで非表示にする") }, onClick = { menu = false; onHide(true) })
                    if (isGoogle && linked) DropdownMenuItem(text = { Text("Google との連携を解除") }, onClick = { menu = false; onUnlink() })
                    DropdownMenuItem(text = { Text("端末からアカウントを削除…") }, onClick = { menu = false; confirmRemove = true })
                }
            }
        }
        if (confirmRemove) {
            AlertDialog(
                onDismissRequest = { confirmRemove = false },
                title = { Text("端末からアカウントを削除") },
                text = { Text("アカウントの削除は Android の設定画面で行います。開いた画面で「$account」を選び、「アカウントを削除」を押してください。\nこのアプリで表示しないだけなら「このアプリで非表示にする」を使ってください。") },
                confirmButton = { TextButton(onClick = { confirmRemove = false; onRemoveFromDevice() }) { Text("設定を開く") } },
                dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("キャンセル") } },
            )
        }
        if (hidden) return@Column
        if (isGoogle && !linked) {
            Text("添付ファイル（写真・PDF など）を使うときだけ、Google との連携が必要です。予定の表示・作成・編集は連携なしで使えます。",
                fontSize = 12.sp, color = scheme.onSurfaceVariant)
            Button(onClick = onLink, enabled = !busy, modifier = Modifier.padding(top = 6.dp)) { Text("Google と連携") }
        }
    }
}

@Composable
private fun CalendarRow(c: CalendarInfo, visible: Boolean, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)).background(Color(c.color)))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(c.name, fontSize = 15.sp)
            Text(roleText(c), fontSize = 12.sp, color = scheme.onSurfaceVariant)
        }
        Switch(checked = visible, onCheckedChange = onToggle, modifier = Modifier.semantics { contentDescription = "${c.name}を表示" })
    }
}

private fun roleText(c: CalendarInfo): String = buildString {
    if (c.isPrimary) append("メイン ・ ")
    append(when {
        c.isOwner -> "オーナー"
        c.writable -> "編集可"
        else -> "閲覧のみ"
    })
}

/** カレンダーを押したとき：名前・色・共有は Google カレンダーの設定画面で変える */
@Composable
private fun CalendarSettings(c: CalendarInfo, onOpenGoogle: () -> Unit, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(Color(c.color)))
            Column(Modifier.padding(start = 12.dp)) {
                Text(c.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("${c.accountName} ・ ${roleText(c)}", fontSize = 12.sp, color = scheme.onSurfaceVariant)
            }
        }
        if (!c.isGoogle) {
            Text("このカレンダーは Google のカレンダーではないため、ここでは変更できません。", color = scheme.onSurfaceVariant)
            return@Column
        }
        Text("名前・色・共有（誰と共有するか、権限）・削除は、Google カレンダーの設定画面で変更します。", fontSize = 14.sp)
        Button(onClick = onOpenGoogle, modifier = Modifier.fillMaxWidth()) { Text("Google カレンダーで設定を開く") }
        Text("変更は少しするとこのアプリにも反映されます（反映されないときは、画面の上の［今すぐ同期］）。",
            fontSize = 12.sp, color = scheme.onSurfaceVariant)
        if (!c.isOwner) Text("共有の設定は、カレンダーのオーナーだけが変更できます。", fontSize = 12.sp, color = scheme.onSurfaceVariant)
        TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) { Text("閉じる") }
    }
}

