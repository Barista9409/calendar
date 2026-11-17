package io.github.barista9409.calendar

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.barista9409.calendar.ui.CalendarScreen
import io.github.barista9409.calendar.ui.CalendarViewModel
import io.github.barista9409.calendar.ui.PermissionScreen
import io.github.barista9409.calendar.ui.settings.UpdateScreen
import io.github.barista9409.calendar.ui.calendars.CalendarsScreen
import io.github.barista9409.calendar.google.GoogleAuth
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import io.github.barista9409.calendar.ui.theme.CalendarTheme
import io.github.barista9409.calendar.widget.WidgetUpdater
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val vm: CalendarViewModel by viewModels()
    private var openUpdate by mutableStateOf(false)
    private var openCalendars by mutableStateOf(false)
    private var openSettings by mutableStateOf(false)
    private var autoFix by mutableStateOf(false)

    // Google の同意画面（連携時）
    private var pendingAuth: GoogleAuth.Resolution? = null
    private val authLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val res = if (r.resultCode == RESULT_OK) container.googleAuth.parseResult(r.data) else null
        pendingAuth?.result?.complete(res)
        pendingAuth = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        lifecycleScope.launch {
            container.googleAuth.resolutions.collect { res ->
                pendingAuth?.result?.complete(null)
                pendingAuth = res
                authLauncher.launch(IntentSenderRequest.Builder(res.intent.intentSender).build())
            }
        }
        setContent {
            CalendarTheme {
                Surface(Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Transparent) {
                    var granted by remember { mutableStateOf(container.repository.hasPermission()) }
                    var denied by remember { mutableStateOf(false) }
                    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
                        granted = container.repository.hasPermission()
                        denied = !granted
                        if (granted) { vm.refresh(); WidgetUpdater.updateAllAsync(applicationContext) }
                    }
                    val perms = buildList {
                        add(Manifest.permission.READ_CALENDAR); add(Manifest.permission.WRITE_CALENDAR)
                        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                    }.toTypedArray()
                    when {
                        !granted -> PermissionScreen(denied, onRequest = { launcher.launch(perms) }, onOpenSettings = {
                            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                        })
                        openUpdate -> {
                            BackHandler { openUpdate = false }
                            io.github.barista9409.calendar.ui.theme.MenuTheme { UpdateScreen(onBack = { openUpdate = false }) }
                        }
                        openCalendars -> {
                            BackHandler { openCalendars = false; autoFix = false; vm.refresh() }
                            // メニューから開く画面は、メニューと同じ配色
                            io.github.barista9409.calendar.ui.theme.MenuTheme { CalendarsScreen(onBack = { openCalendars = false; autoFix = false; vm.refresh() }, autoFix = autoFix) }
                        }
                        openSettings -> {
                            BackHandler { openSettings = false }
                            io.github.barista9409.calendar.ui.theme.MenuTheme {
                                io.github.barista9409.calendar.ui.settings.SettingsScreen(onBack = { openSettings = false },
                                    onOpenUpdate = { openSettings = false; openUpdate = true })
                            }
                        }
                        else -> CalendarScreen(vm, onOpenUpdate = { openUpdate = true }, onOpenCalendars = { openCalendars = true },
                            onSetupSync = { autoFix = true; openCalendars = true },
                            onOpenSettings = { openSettings = true })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (container.repository.hasPermission()) { vm.refresh(); vm.syncIfStale() }
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        // ウィジェット・通知・共有から開いたときは、メニューや設定画面を閉じてカレンダーを見せる
        val external = intent.hasExtra(EXTRA_DATE) || intent.hasExtra(EXTRA_EVENT_ID) ||
            intent.getBooleanExtra(EXTRA_NEW_EVENT, false) || intent.action == Intent.ACTION_SEND
        if (external) {
            openCalendars = false; openSettings = false; openUpdate = false
            vm.requestCloseDrawer()
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_UPDATE, false)) openUpdate = true
        intent.getStringExtra(EXTRA_DATE)?.let { s ->
            runCatching { LocalDate.parse(s) }.getOrNull()?.let { vm.openDate(it) }
            intent.removeExtra(EXTRA_DATE)
        }
        if (intent.getBooleanExtra(EXTRA_NEW_EVENT, false)) { vm.newEvent(); intent.removeExtra(EXTRA_NEW_EVENT) }
        if (intent.action == Intent.ACTION_SEND) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
            uri?.let { vm.startShare(it) }
            intent.action = null
        }
        if (intent.hasExtra(EXTRA_EVENT_ID)) {
            io.github.barista9409.calendar.notify.Buzz.stop(this) // 通知から開いたらバイブを止める
            vm.openEventFull(intent.getLongExtra(EXTRA_EVENT_ID, -1), intent.getLongExtra(EXTRA_EVENT_BEGIN, 0))
            intent.removeExtra(EXTRA_EVENT_ID)
        }
    }

    companion object {
        const val EXTRA_OPEN_UPDATE = "open_update"
        const val EXTRA_DATE = "open_date"
        const val EXTRA_NEW_EVENT = "new_event"
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_EVENT_BEGIN = "event_begin"
    }
}
