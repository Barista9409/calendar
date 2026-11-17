package io.github.barista9409.calendar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PermissionScreen(denied: Boolean, onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(64.dp))
        Text("カレンダーへのアクセス", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "端末に登録された Google アカウントのカレンダーを表示・編集するために、カレンダーへのアクセスを許可してください。" +
                "予定は Google カレンダーと同期され、このアプリが外部に送信することはありません。",
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onRequest, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("許可する") }
        if (denied) {
            Text("許可されていません。設定画面の「権限」からカレンダーをオンにしてください。", fontSize = 13.sp,
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("設定を開く") }
        }
    }
}
