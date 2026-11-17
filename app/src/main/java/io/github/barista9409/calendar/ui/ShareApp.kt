package io.github.barista9409.calendar.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.barista9409.calendar.BuildConfig

/** このアプリを家族などに紹介するときの文面 */
object ShareApp {
    /** 最新版の APK を直接ダウンロードするリンク（リリースのたびに calendar.apk を置いている） */
    const val DOWNLOAD_URL = "https://github.com/Barista9409/calendar-releases/releases/latest/download/calendar.apk"

    fun text(version: String = BuildConfig.VERSION_NAME): String = """
Calendar（Android 用カレンダーアプリ・バージョン $version）

Google カレンダーの予定をそのまま見たり書いたりでき、家族と共有しているカレンダーの予定に写真や PDF も添付できるアプリです。月・日のウィジェットもあります。

■ ダウンロード（リンクを開くと、いちばん新しいアプリが直接ダウンロードされます）
$DOWNLOAD_URL
ダウンロードした「calendar.apk」を開いてインストールしてください。初めてのときは「この提供元のアプリを許可」をオンにします。

■ はじめに
1. アプリを開き、カレンダーへのアクセスを許可
2. スマホに Google アカウントがあれば、予定がそのまま表示されます
3. 写真や PDF を添付するときは、メニューの「カレンダーと同期」から連携（予定を見るだけなら不要）

アップデートは、アプリのメニューの「アップデート」からできます。
""".trim()

    fun copy(context: Context) {
        context.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Calendar の紹介", text()))
    }

    fun send(context: Context) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "カレンダーアプリの紹介").putExtra(Intent.EXTRA_TEXT, text())
        context.startActivity(Intent.createChooser(send, "このアプリを共有").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** 「このアプリを共有」：文面を見せて、コピーまたは LINE・メールなどで送る */
@Composable
fun ShareAppDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("このアプリを共有") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ダウンロード先と簡単な説明です。コピーして LINE やメールに貼り付けられます。", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                SelectionContainer {
                    Text(ShareApp.text(), fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { ShareApp.copy(context); copied = true }) { Text(if (copied) "コピーしました" else "コピー") }
        },
        dismissButton = {
            TextButton(onClick = { ShareApp.send(context); onDismiss() }) { Text("送る…") }
        },
    )
}
