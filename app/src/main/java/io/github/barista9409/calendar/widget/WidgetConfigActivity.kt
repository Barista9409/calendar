package io.github.barista9409.calendar.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.barista9409.calendar.ui.settings.WidgetAppearance
import io.github.barista9409.calendar.ui.theme.CalendarTheme

/** ウィジェットを長押し →「設定」で開く画面（アプリの設定の「ウィジェットの見た目」と同じ内容） */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // 配置時に戻るボタンで閉じても、ウィジェットはそのまま置かれるようにする
        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        setContent {
            CalendarTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("ウィジェットの見た目", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        WidgetAppearance(appWidgetId = id.takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID })
                        Button(onClick = { WidgetUpdater.updateAllAsync(applicationContext); finish() },
                            modifier = Modifier.fillMaxWidth()) { Text("完了") }
                    }
                }
            }
        }
    }
}
