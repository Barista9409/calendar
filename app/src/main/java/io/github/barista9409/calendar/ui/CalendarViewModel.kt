package io.github.barista9409.calendar.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.barista9409.calendar.container
import io.github.barista9409.calendar.model.CalendarInfo
import io.github.barista9409.calendar.model.EventDraft
import io.github.barista9409.calendar.model.EventInstance
import io.github.barista9409.calendar.model.ViewMode
import io.github.barista9409.calendar.util.DateUtils
import io.github.barista9409.calendar.widget.WidgetUpdater
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong

sealed interface Sheet {
    data class Day(val date: LocalDate) : Sheet
    data class Event(val instance: EventInstance) : Sheet
    data class Edit(val draft: EventDraft) : Sheet
}

enum class SheetSize { HALF, FULL }

/** 予定詳細の添付ファイル欄の状態 */
sealed interface AttachState {
    data object Loading : AttachState
    data object NotGoogle : AttachState
    /**
     * items：予定の説明に書かれた添付ファイル（誰でも見られる）
     * canEdit：予定を編集できるカレンダーか ／ linked：Google と連携済み（添付を追加するのに必要）
     */
    data class Ready(val items: List<io.github.barista9409.calendar.google.Attachment>, val canEdit: Boolean,
                     val linked: Boolean, val account: String = "") : AttachState
    data class Error(val message: String) : AttachState
}

/** ファイルを開く要求（画面側で Intent を起動） */
sealed interface OpenRequest {
    data class File(val uri: android.net.Uri, val mime: String?) : OpenRequest
    data class Url(val url: String) : OpenRequest
    data class Preview(val file: java.io.File, val uri: android.net.Uri, val mime: String?, val title: String) : OpenRequest
}

data class PendingDelete(val token: Long, val instance: EventInstance, val wholeSeries: Boolean, val following: Boolean = false,
                         /** 予定と一緒にドライブのゴミ箱へ移す添付ファイル */
                         val trash: List<io.github.barista9409.calendar.google.Attachment> = emptyList(), val account: String = "")

/** 予定を消す前に「添付ファイルも消すか」を聞く */
data class AskTrash(val instance: EventInstance, val scope: io.github.barista9409.calendar.model.RecurScope,
                    val items: List<io.github.barista9409.calendar.google.Attachment>, val account: String)

sealed interface UiMessage {
    data class Info(val text: String) : UiMessage
    data class Deleted(val pending: PendingDelete) : UiMessage
}

class CalendarViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = app.container.repository
    private val prefs = app.container.prefs
    private val google = app.container.google
    private val attachments = app.container.attachments

    private val _attach = MutableStateFlow<AttachState>(AttachState.Loading)
    val attachState: StateFlow<AttachState> = _attach.asStateFlow()
    private val _guests = MutableStateFlow<List<io.github.barista9409.calendar.data.CalendarRepository.Guest>>(emptyList())
    val guests: StateFlow<List<io.github.barista9409.calendar.data.CalendarRepository.Guest>> = _guests.asStateFlow()
    private val _uploading = MutableStateFlow<Float?>(null)
    /** アップロード中の進み具合（null = アップロードしていない） */
    val uploading: StateFlow<Float?> = _uploading.asStateFlow()
    private val _openRequests = MutableSharedFlow<OpenRequest>(extraBufferCapacity = 2)
    val openRequests: SharedFlow<OpenRequest> = _openRequests.asSharedFlow()
    private var attachJob: kotlinx.coroutines.Job? = null
    val zone: ZoneId = ZoneId.systemDefault()

    private val _calendars = MutableStateFlow<List<CalendarInfo>>(emptyList())
    val calendars: StateFlow<List<CalendarInfo>> = _calendars.asStateFlow()
    val hiddenCalendars: StateFlow<Set<Long>> = prefs.hiddenCalendars
    val accountFilter: StateFlow<String?> = prefs.accountFilter

    private val _events = MutableStateFlow<List<EventInstance>>(emptyList())
    private var loadedFrom: LocalDate? = null
    private var loadedTo: LocalDate? = null

    private val _viewMode = MutableStateFlow(prefs.viewMode)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    private val _today = MutableStateFlow(LocalDate.now())
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    private val _selected = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selected.asStateFlow()

    private val _sheet = MutableStateFlow<Sheet?>(null)
    val sheet: StateFlow<Sheet?> = _sheet.asStateFlow()
    private val _sheetSize = MutableStateFlow(SheetSize.HALF)
    val sheetSize: StateFlow<SheetSize> = _sheetSize.asStateFlow()

    /** シートの高さ（画面に対する割合）。ユーザーが動かした位置を覚えておく */
    private val _sheetFraction = MutableStateFlow(prefs.sheetFraction)
    val sheetFraction: StateFlow<Float> = _sheetFraction.asStateFlow()

    fun dragSheet(deltaFraction: Float) {
        val base = if (_sheetSize.value == SheetSize.FULL) 1f else _sheetFraction.value
        _sheetSize.value = SheetSize.HALF
        _sheetFraction.value = (base - deltaFraction).coerceIn(0.08f, 1f)
    }

    fun endSheetDrag() {
        val f = _sheetFraction.value
        when {
            f > 0.92f -> { _sheetSize.value = SheetSize.FULL; _sheetFraction.value = prefs.sheetFraction }
            f < 0.18f -> { _sheetFraction.value = prefs.sheetFraction; closeSheet() }
            else -> prefs.sheetFraction = f
        }
    }

    private val _jump = MutableSharedFlow<LocalDate>(extraBufferCapacity = 4)
    /** 「今日」ボタンなどでビューをスクロールさせる要求 */
    val jumpRequests: SharedFlow<LocalDate> = _jump.asSharedFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    private val pending = MutableStateFlow<Map<Long, PendingDelete>>(emptyMap())
    private val tokenGen = AtomicLong(1)

    val visibleCalendars: StateFlow<List<CalendarInfo>> =
        combine(_calendars, prefs.hiddenCalendars, prefs.accountFilter) { cals, hidden, acc ->
            cals.filter { it.id !in hidden && (acc == null || it.accountName == acc) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val writableCalendars: StateFlow<List<CalendarInfo>> =
        combine(_calendars, prefs.accountFilter) { cals, acc ->
            cals.filter { it.writable && (acc == null || it.accountName == acc) }.ifEmpty { cals.filter { it.writable } }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 祝日カレンダーに予定がある日（日付を赤く表示する） */
    val holidays: StateFlow<Set<LocalDate>> = combine(_events, _calendars) { evs, cals ->
        val ids = cals.filter { it.ownerAccount.orEmpty().contains("holiday", ignoreCase = true) }.map { it.id }.toSet()
        evs.filter { it.calendarId in ids }.map { it.startDate(zone) }.toSet()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val eventsByDay: StateFlow<Map<LocalDate, List<EventInstance>>> =
        combine(_events, visibleCalendars, pending) { evs, vis, pend ->
            val ids = vis.map { it.id }.toSet()
            val hiddenInstances = pend.values.filter { !it.wholeSeries && !it.following }.map { it.instance.instanceId }.toSet()
            val hiddenSeries = pend.values.filter { it.wholeSeries }.map { it.instance.eventId }.toSet()
            val hiddenFollowing = pend.values.filter { it.following }.map { it.instance.eventId to it.instance.begin }
            val map = HashMap<LocalDate, MutableList<EventInstance>>()
            for (e in evs) {
                if (e.calendarId !in ids || e.instanceId in hiddenInstances || e.eventId in hiddenSeries) continue
                if (hiddenFollowing.any { (id, b) -> e.eventId == id && e.begin >= b }) continue
                var d = e.startDate(zone)
                val last = e.lastDate(zone)
                var guard = 0
                while (!d.isAfter(last) && guard++ < 62) {
                    map.getOrPut(d) { mutableListOf() }.add(e)
                    d = d.plusDays(1)
                }
            }
            map.mapValues { (_, v) -> v.sortedWith(compareByDescending<EventInstance> { it.isLong }.thenBy { it.begin }.thenBy { it.title }) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    init {
        refresh()
        @OptIn(FlowPreview::class)
        viewModelScope.launch { repo.changes().debounce(400).collect { refresh() } }
        @OptIn(FlowPreview::class)
        viewModelScope.launch { prefs.hiddenAccounts.debounce(200).collect { refresh(); WidgetUpdater.updateAllAsync(getApplication()) } }
    }

    fun calendarById(id: Long): CalendarInfo? = _calendars.value.firstOrNull { it.id == id }

    /** Google と今すぐ同期して再表示（自動同期がオフでも 1 回は同期される） */
    fun syncNow() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { getApplication<Application>().container.sync.requestAll(upload = true, manual = true) }
        refresh()
    }

    val syncRun = app.container.sync.run
    val syncFinished = app.container.sync.finished

    /** ウィジェットや通知から開いたとき、メニューを閉じて予定を見せる */
    private val _closeDrawer = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(replay = 1)
    val closeDrawer: kotlinx.coroutines.flow.SharedFlow<Unit> = _closeDrawer
    fun requestCloseDrawer() { _closeDrawer.tryEmit(Unit) }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun drawerClosed() { _closeDrawer.resetReplayCache() }

    /** アプリを開いたとき：しばらく同期していなければ同期を頼む */
    fun syncIfStale() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { getApplication<Application>().container.sync.requestIfStale() }
    }

    fun refresh() {
        _today.value = LocalDate.now()
        viewModelScope.launch {
            _calendars.value = repo.calendars()
            val from = loadedFrom; val to = loadedTo
            if (from != null && to != null) loadEvents(from, to)
        }
    }

    /** ビューが表示しようとしている範囲を伝える。読み込み済み範囲外なら再読み込み */
    fun ensureRange(from: LocalDate, to: LocalDate) {
        val lf = loadedFrom; val lt = loadedTo
        if (lf != null && lt != null && !from.isBefore(lf) && !to.isAfter(lt)) return
        val newFrom = from.minusWeeks(6)
        val newTo = to.plusWeeks(10)
        loadedFrom = newFrom; loadedTo = newTo
        viewModelScope.launch { loadEvents(newFrom, newTo) }
    }

    private suspend fun loadEvents(from: LocalDate, to: LocalDate) {
        val s = from.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val e = to.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        _events.value = repo.instances(s, e)
    }

    // ---- 表示 ----
    fun setViewMode(mode: ViewMode) {
        _viewMode.value = mode
        prefs.viewMode = mode
        if (_sheet.value is Sheet.Day && mode != ViewMode.MONTH) closeSheet()
    }

    fun select(date: LocalDate) { _selected.value = date }

    fun goToday() {
        val t = LocalDate.now()
        _today.value = t
        _selected.value = t
        _jump.tryEmit(t)
    }

    fun openDate(date: LocalDate) {
        _selected.value = date
        _jump.tryEmit(date)
        openDay(date)
    }

    fun setCalendarVisible(id: Long, visible: Boolean) {
        prefs.setCalendarVisible(id, visible)
    }
    fun setAccountFilter(account: String?) = prefs.setAccountFilter(account)

    // ---- シート ----
    fun openDay(date: LocalDate) {
        _selected.value = date
        _sheet.value = Sheet.Day(date)
        _sheetSize.value = SheetSize.HALF
    }

    fun openEvent(e: EventInstance) {
        _sheet.value = Sheet.Event(e)
        _sheetSize.value = SheetSize.HALF
        loadAttachments(e)
        _guests.value = emptyList()
        viewModelScope.launch { _guests.value = repo.guests(e.eventId) }
    }

    // ---- 添付ファイル ----
    fun loadAttachments(e: EventInstance) {
        attachJob?.cancel()
        val cal = calendarById(e.calendarId)
        _attach.value = if (cal == null || !cal.isGoogle) AttachState.NotGoogle else AttachState.Loading
        if (cal == null || !cal.isGoogle) return
        attachJob = viewModelScope.launch {
            _attach.value = try {
                AttachState.Ready(attachments.list(e), cal.writable, google.isLinked(cal.accountName), cal.accountName)
            } catch (ex: kotlinx.coroutines.CancellationException) {
                throw ex
            } catch (ex: Exception) {
                AttachState.Error(ex.message ?: "添付ファイルを読み込めません")
            }
        }
    }

    fun notify(text: String) { _messages.tryEmit(UiMessage.Info(text)) }

    fun linkAccount(account: String) {
        viewModelScope.launch {
            try {
                google.link(account)
                (sheet.value as? Sheet.Event)?.let { loadAttachments(it.instance) }
            } catch (ex: Exception) { _messages.tryEmit(UiMessage.Info(ex.message ?: "連携できませんでした")) }
        }
    }

    fun attach(e: EventInstance, uri: android.net.Uri) {
        val cal = calendarById(e.calendarId) ?: return
        viewModelScope.launch {
            _uploading.value = 0f
            try {
                attachments.attach(cal, e, uri) { p -> _uploading.value = p }
                _messages.tryEmit(UiMessage.Info("添付しました"))
            } catch (ex: Exception) {
                _messages.tryEmit(UiMessage.Info("添付できませんでした：${ex.message}"))
            } finally {
                _uploading.value = null
                if ((sheet.value as? Sheet.Event)?.instance?.eventId == e.eventId) loadAttachments(e)
            }
        }
    }

    /** 画像・PDF のサムネイル（初回はドライブから取得してキャッシュ） */
    suspend fun attachmentThumbnail(account: String, att: io.github.barista9409.calendar.google.Attachment, sizePx: Int): android.graphics.Bitmap? {
        if (att.fileId == null || !io.github.barista9409.calendar.util.Thumbnails.supports(att.mimeType, att.title)) return null
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { io.github.barista9409.calendar.util.Thumbnails.load(attachments.localFile(account, att), sizePx) }.getOrNull()
        }
    }

    fun openAttachment(e: EventInstance, att: io.github.barista9409.calendar.google.Attachment) {
        val cal = calendarById(e.calendarId) ?: return
        viewModelScope.launch {
            try {
                if (!attachments.isCached(att)) _messages.tryEmit(UiMessage.Info("ダウンロードしています…"))
                val file = attachments.localFile(cal.accountName, att)
                val uri = androidx.core.content.FileProvider.getUriForFile(getApplication(), getApplication<Application>().packageName + ".files", file)
                _openRequests.tryEmit(
                    if (io.github.barista9409.calendar.ui.preview.canPreview(att.mimeType, att.title)) OpenRequest.Preview(file, uri, att.mimeType, att.title)
                    else OpenRequest.File(uri, att.mimeType)
                )
            } catch (ex: io.github.barista9409.calendar.google.OpenInBrowserException) {
                _openRequests.tryEmit(OpenRequest.Url(ex.url))
            } catch (ex: Exception) {
                _messages.tryEmit(UiMessage.Info("開けませんでした：${ex.message}"))
            }
        }
    }

    fun removeAttachment(e: EventInstance, att: io.github.barista9409.calendar.google.Attachment) {
        val cal = calendarById(e.calendarId) ?: return
        viewModelScope.launch {
            try {
                attachments.remove(cal, e, att)
                _messages.tryEmit(UiMessage.Info("添付を削除しました"))
            } catch (ex: Exception) {
                _messages.tryEmit(UiMessage.Info("削除できませんでした：${ex.message}"))
            } finally { loadAttachments(e) }
        }
    }

    fun setSheetSize(size: SheetSize) { _sheetSize.value = size }

    // ---- ドラッグでの移動・長さ変更 ----
    /** deltaDays 日・deltaMinutes 分ずらす。resize=true なら終了時刻だけを変える */
    fun moveEvent(e: EventInstance, deltaDays: Int, deltaMinutes: Int, resize: Boolean) {
        if (e.isLong || (deltaDays == 0 && deltaMinutes == 0)) return
        val cal = calendarById(e.calendarId) ?: return
        if (!cal.writable) { _messages.tryEmit(UiMessage.Info("このカレンダーは閲覧のみです")); return }
        val s0 = e.startLocal(zone); val e0 = e.endLocal(zone)
        val shift = java.time.Duration.ofDays(deltaDays.toLong()).plusMinutes(deltaMinutes.toLong())
        val ns = if (resize) s0 else s0.plus(shift)
        var ne = e0.plus(shift)
        if (!ne.isAfter(ns)) ne = ns.plusMinutes(15)
        viewModelScope.launch {
            if (e.isRecurring) {
                val reminder = repo.reminderMinutes(e.eventId)
                _pendingRecur.value = EventDraft(
                    eventId = e.eventId, title = e.title, calendarId = e.calendarId, start = ns, end = ne,
                    location = e.location.orEmpty(), description = e.description.orEmpty(), reminderMinutes = reminder,
                    isRecurring = true, rrule = e.rrule, instanceBegin = e.begin, colorKey = repo.colorKey(e.eventId),
                    guests = repo.guests(e.eventId).map { it.email },
                )
                return@launch
            }
            try {
                repo.updateTimes(e.eventId, ns.atZone(zone).toInstant().toEpochMilli(), ne.atZone(zone).toInstant().toEpochMilli())
                _messages.tryEmit(UiMessage.Info("${DateUtils.date(ns.toLocalDate())} ${DateUtils.time(ns)}〜${DateUtils.time(ne)} に変更しました"))
                refresh()
                WidgetUpdater.updateAll(getApplication())
            } catch (ex: Exception) {
                _messages.tryEmit(UiMessage.Info("変更できませんでした：${ex.message}"))
            }
        }
    }

    // ---- 検索 ----
    /** タイトル・場所・説明で検索（1 年前〜2 年後） */
    suspend fun search(q: String): List<EventInstance> {
        if (q.isBlank()) return emptyList()
        val now = java.time.LocalDate.now()
        val all = repo.instances(now.minusYears(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            now.plusYears(2).atStartOfDay(zone).toInstant().toEpochMilli())
        val vis = visibleCalendars.value.map { it.id }.toSet()
        return all.filter { e ->
            e.calendarId in vis && listOf(e.title, e.location.orEmpty(), e.description.orEmpty()).any { it.contains(q, ignoreCase = true) }
        }
    }

    // ---- 他のアプリからの共有 ----
    private val _shareUri = MutableStateFlow<android.net.Uri?>(null)
    val shareUri: StateFlow<android.net.Uri?> = _shareUri.asStateFlow()
    fun startShare(uri: android.net.Uri) { _shareUri.value = uri }
    fun cancelShare() { _shareUri.value = null }

    /** 添付できる予定（Google のカレンダーで書き込み可）。空欄なら今日から 60 日分 */
    suspend fun searchAttachTargets(q: String): List<EventInstance> {
        val ok = calendars.value.filter { it.isGoogle && it.writable }.map { it.id }.toSet()
        val list = if (q.isBlank()) {
            val from = java.time.LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
            repo.instances(from, from + 60L * 86_400_000L)
        } else search(q)
        return list.filter { it.calendarId in ok }
    }

    fun attachShared(e: EventInstance, uri: android.net.Uri) {
        _shareUri.value = null
        openEventFull(e.eventId, e.begin)
        attach(e, uri)
    }

    /** ウィジェットや通知から：その予定を全画面で開く */
    fun openEventFull(eventId: Long, begin: Long) {
        viewModelScope.launch {
            val list = repo.instances(begin - 60_000, begin + 86_400_000L)
            val e = list.firstOrNull { it.eventId == eventId && it.begin == begin } ?: list.firstOrNull { it.eventId == eventId } ?: return@launch
            val d = e.startDate(zone)
            _selected.value = d
            _jump.tryEmit(d)
            openEvent(e)
            _sheetSize.value = SheetSize.FULL
        }
    }
    fun closeSheet() { _sheet.value = null; _sheetSize.value = SheetSize.HALF }

    private fun defaultCalendarId(): Long? {
        val writable = writableCalendars.value
        val visible = visibleCalendars.value.map { it.id }.toSet()
        val last = prefs.lastCalendarId
        writable.firstOrNull { it.id == last && it.id in visible }?.let { return it.id }
        return (writable.firstOrNull { it.isPrimary && it.id in visible } ?: writable.firstOrNull { it.id in visible }
            ?: writable.firstOrNull())?.id
    }

    fun newEvent(start: LocalDateTime = DateUtils.defaultStart(_selected.value), title: String = "", allDay: Boolean = false) {
        _selected.value = start.toLocalDate()
        _sheet.value = Sheet.Edit(
            EventDraft(
                title = title, calendarId = defaultCalendarId(), allDay = allDay,
                start = start, end = start.plusHours(1), reminderMinutes = if (allDay) null else 30,
            )
        )
        _sheetSize.value = SheetSize.HALF
    }

    fun editEvent(e: EventInstance, asCopy: Boolean = false) {
        viewModelScope.launch {
            val reminder = repo.reminderMinutes(e.eventId)
            val guests = repo.guests(e.eventId).map { it.email }
            val series = if (e.isRecurring) repo.seriesInfo(e.eventId) else null
            val colorKey = repo.colorKey(e.eventId)
            val start = if (e.allDay) e.startDate(zone).atStartOfDay() else e.startLocal(zone)
            val end = if (e.allDay) e.lastDate(zone).atStartOfDay() else e.endLocal(zone)
            _sheet.value = Sheet.Edit(
                EventDraft(
                    eventId = if (asCopy) null else e.eventId,
                    title = e.title, calendarId = e.calendarId, allDay = e.allDay, start = start, end = end,
                    location = e.location.orEmpty(),
                    description = io.github.barista9409.calendar.google.AttachmentText.body(e.description),
                    attachmentBlock = io.github.barista9409.calendar.google.AttachmentText.block(e.description),
                    reminderMinutes = reminder, isRecurring = !asCopy && e.isRecurring,
                    guests = guests, colorKey = colorKey,
                    rrule = if (asCopy) null else series?.rrule ?: e.rrule,
                    instanceBegin = if (e.isRecurring && !asCopy) e.begin else null,
                )
            )
            _sheetSize.value = SheetSize.FULL
        }
    }

    suspend fun eventColors(calendarId: Long): List<io.github.barista9409.calendar.model.EventColor> {
        val c = calendarById(calendarId) ?: return emptyList()
        return repo.eventColors(c.accountName, c.accountType)
    }

    fun updateDraft(d: EventDraft) {
        if (_sheet.value is Sheet.Edit) _sheet.value = Sheet.Edit(d)
    }

    /** 繰り返し予定の変更範囲を選ぶ待ち（画面でダイアログを出す） */
    private val _pendingRecur = MutableStateFlow<EventDraft?>(null)
    val pendingRecur: StateFlow<EventDraft?> = _pendingRecur.asStateFlow()
    fun cancelRecur() { _pendingRecur.value = null }

    fun applyRecur(scope: io.github.barista9409.calendar.model.RecurScope) {
        val d = _pendingRecur.value ?: return
        _pendingRecur.value = null
        save(d, scope)
    }

    fun save(d: EventDraft, scope: io.github.barista9409.calendar.model.RecurScope? = null) {
        val calId = d.calendarId ?: run { _messages.tryEmit(UiMessage.Info("保存先のカレンダーがありません")); return }
        if (d.isRecurring && d.eventId != null && scope == null) { _pendingRecur.value = d; return }
        viewModelScope.launch {
            try {
                val id = d.eventId
                val begin = d.instanceBegin
                // 保存した予定の ID（添付はこの予定に付ける。繰り返しはシリーズ全体）
                val savedId: Long = if (id == null) repo.insert(d, zone) else {
                    when {
                        !d.isRecurring || begin == null -> repo.update(d, zone)
                        scope == io.github.barista9409.calendar.model.RecurScope.THIS -> repo.saveOccurrence(d, id, begin, zone)
                        scope == io.github.barista9409.calendar.model.RecurScope.FOLLOWING -> repo.saveFollowing(d, id, begin, zone)
                        else -> repo.saveAll(d, id, begin, zone)
                    }
                    id
                }
                prefs.lastCalendarId = calId
                closeSheet()
                _messages.tryEmit(UiMessage.Info(if (d.eventId == null) "予定を保存しました" else "予定を更新しました"))
                refresh()
                WidgetUpdater.updateAll(getApplication())
                if (d.pendingFiles.isNotEmpty()) uploadPending(calId, savedId, d.pendingFiles)
            } catch (e: Exception) {
                _messages.tryEmit(UiMessage.Info("保存できませんでした：${e.message}"))
            }
        }
    }

    /** 入力画面で選んだファイルを、保存した予定にアップロードして添付する */
    private suspend fun uploadPending(calId: Long, eventId: Long, files: List<io.github.barista9409.calendar.model.PendingFile>) {
        val cal = calendarById(calId) ?: _calendars.value.firstOrNull { it.id == calId } ?: return
        try {
            if (!google.isLinked(cal.accountName)) google.link(cal.accountName)
        } catch (e: Exception) {
            _messages.tryEmit(UiMessage.Info("添付できませんでした（Google と連携してください）：${e.message}")); return
        }
        var done = 0
        for ((i, f) in files.withIndex()) {
            _uploading.value = 0f
            try {
                attachments.attach(cal, eventId, android.net.Uri.parse(f.uri)) { p -> _uploading.value = (i + p) / files.size }
                done++
            } catch (e: Exception) {
                _messages.tryEmit(UiMessage.Info("「${f.name}」を添付できませんでした：${e.message}"))
            }
        }
        _uploading.value = null
        if (done > 0) _messages.tryEmit(UiMessage.Info("${done} 件のファイルを添付しました"))
        refresh()
    }

    // ---- 削除（取り消し可能） ----
    private val _askTrash = MutableStateFlow<AskTrash?>(null)
    val askTrash: StateFlow<AskTrash?> = _askTrash.asStateFlow()

    /**
     * 予定を削除。予定そのものが消えるとき（単発・すべて）に自分が添付したファイルがあれば、
     * ドライブのファイルも消すかを先に聞く（初期は「残す」）。
     */
    fun requestDelete(e: EventInstance, scope: io.github.barista9409.calendar.model.RecurScope) {
        val whole = !e.isRecurring || scope == io.github.barista9409.calendar.model.RecurScope.ALL
        val cal = calendarById(e.calendarId)
        if (whole && cal != null && google.isLinked(cal.accountName)) {
            viewModelScope.launch {
                val items = runCatching { attachments.list(e) }.getOrDefault(emptyList()).filter { it.fileId != null }
                if (items.isEmpty()) deleteNow(e, scope)
                else _askTrash.value = AskTrash(e, scope, items, cal.accountName)
            }
            return
        }
        deleteNow(e, scope)
    }

    /** 添付ファイルの確認の答え（null ＝ やめる） */
    fun answerTrash(trash: Boolean?) {
        val a = _askTrash.value ?: return
        _askTrash.value = null
        if (trash == null) return
        deleteNow(a.instance, a.scope, if (trash) a.items else emptyList(), a.account)
    }

    private fun deleteNow(e: EventInstance, scope: io.github.barista9409.calendar.model.RecurScope,
                          trash: List<io.github.barista9409.calendar.google.Attachment> = emptyList(), account: String = "") {
        val p = PendingDelete(tokenGen.getAndIncrement(), e,
            wholeSeries = scope == io.github.barista9409.calendar.model.RecurScope.ALL,
            following = scope == io.github.barista9409.calendar.model.RecurScope.FOLLOWING && e.isRecurring,
            trash = trash, account = account)
        pending.value = pending.value + (p.token to p)
        closeSheet()
        _messages.tryEmit(UiMessage.Deleted(p))
    }

    fun undoDelete(token: Long) { pending.value = pending.value - token }

    fun commitDelete(token: Long) {
        val p = pending.value[token] ?: return
        viewModelScope.launch {
            try {
                if (p.following) repo.deleteFollowing(p.instance)
                else if (p.wholeSeries || !p.instance.isRecurring) repo.deleteEvent(p.instance.eventId)
                else repo.deleteOccurrence(p.instance)
                if (p.trash.isNotEmpty()) attachments.trashFiles(p.account, p.trash)
                loadedFrom?.let { f -> loadedTo?.let { t -> loadEvents(f, t) } }
                WidgetUpdater.updateAll(getApplication())
            } catch (e: Exception) {
                _messages.tryEmit(UiMessage.Info("削除できませんでした：${e.message}"))
            } finally {
                pending.value = pending.value - token
            }
        }
    }
}
