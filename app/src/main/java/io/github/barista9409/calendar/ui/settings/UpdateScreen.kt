package io.github.barista9409.calendar.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.barista9409.calendar.container
import io.github.barista9409.calendar.update.UpdateInfo
import io.github.barista9409.calendar.update.UpdateState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val updater = c.updater
    val state by updater.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var auto by remember { mutableStateOf(c.prefs.autoUpdateCheck) }

    LaunchedEffect(Unit) {
        if (state is UpdateState.Idle) { updater.check(); c.prefs.lastUpdateCheck = System.currentTimeMillis() }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("アップデート") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "戻る") }
        })
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (val s = state) {
                        UpdateState.Idle, UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.height(24.dp)); Spacer(Modifier.height(0.dp))
                            Text("  更新を確認しています…")
                        }
                        UpdateState.UpToDate -> Text("最新のバージョンです", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        is UpdateState.Available -> {
                            InfoHeader(s.info, updater.currentVersionName)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { c.prefs.skippedVersionCode = s.info.versionCode; onBack() },
                                    modifier = Modifier.weight(1f)) { Text("スキップ") }
                                Button(onClick = { scope.launch { updater.downloadAndInstall(s.info) } },
                                    modifier = Modifier.weight(1f)) { Text("更新する") }
                            }
                        }
                        is UpdateState.Downloading -> {
                            InfoHeader(s.info, updater.currentVersionName)
                            Text("ダウンロード中… ${(s.progress * 100).toInt()}%", fontSize = 13.sp)
                            LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                        }
                        is UpdateState.Verifying -> { InfoHeader(s.info, updater.currentVersionName); Text("ファイルと署名を確認しています…") }
                        is UpdateState.Installing -> { InfoHeader(s.info, updater.currentVersionName); Text("インストールしています…（確認画面が出たら「更新」を押してください）") }
                        is UpdateState.NeedsPermission -> {
                            InfoHeader(s.info, updater.currentVersionName)
                            Text("更新には「この提供元のアプリを許可」をオンにする必要があります。", fontSize = 13.sp)
                            Button(onClick = { context.startActivity(updater.unknownSourcesSettingsIntent()) }) { Text("設定を開く") }
                            OutlinedButton(onClick = { scope.launch { updater.downloadAndInstall(s.info) } }) { Text("許可したので続ける") }
                        }
                        is UpdateState.Error -> {
                            Text(s.message, color = MaterialTheme.colorScheme.error)
                            s.info?.let { info ->
                                Button(onClick = { scope.launch { updater.downloadAndInstall(info) } }) { Text("再試行") }
                            }
                        }
                    }
                }
            }
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("自動で更新を確認")
                            Text("1日1回確認し、新しい版があれば通知します", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = auto, onCheckedChange = { auto = it; c.prefs.autoUpdateCheck = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("現在のバージョン")
                            val last = c.prefs.lastUpdateCheck.takeIf { it > 0 }?.let {
                                " ・ 最終確認 " + SimpleDateFormat("M/d HH:mm", Locale.JAPAN).format(Date(it))
                            }.orEmpty()
                            Text("${updater.currentVersionName}（${updater.currentVersionCode}）$last", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(onClick = { scope.launch { updater.check(); c.prefs.lastUpdateCheck = System.currentTimeMillis() } }) {
                            Text("今すぐ確認")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoHeader(info: UpdateInfo, current: String) {
    Text("新しいバージョンがあります", fontSize = 17.sp, fontWeight = FontWeight.Bold)
    Text("$current → ${info.versionName}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (info.releaseNotes.isNotBlank()) {
        Card(shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text("更新内容", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(info.releaseNotes, fontSize = 13.sp)
            }
        }
    }
}
