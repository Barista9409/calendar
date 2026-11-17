package io.github.barista9409.calendar.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import io.github.barista9409.calendar.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Verifying(val info: UpdateInfo) : UpdateState
    data class Installing(val info: UpdateInfo) : UpdateState
    data class NeedsPermission(val info: UpdateInfo) : UpdateState
    data class Error(val message: String, val info: UpdateInfo? = null) : UpdateState
}

/** アプリ内アップデート：確認 → ダウンロード → 検証 → インストール */
class Updater(private val context: Context) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val currentVersionName: String = BuildConfig.VERSION_NAME
    val currentVersionCode: Int = BuildConfig.VERSION_CODE

    fun reportInstallResult(status: Int, message: String?) {
        val info = (state.value as? UpdateState.Installing)?.info
        if (status != PackageInstaller.STATUS_SUCCESS) {
            _state.value = UpdateState.Error("インストールできませんでした（${message ?: status}）", info)
        }
    }

    suspend fun fetch(): UpdateInfo = withContext(Dispatchers.IO) {
        val conn = (URL(BuildConfig.UPDATE_MANIFEST_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 15_000
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            when (conn.responseCode) {
                200 -> UpdateInfo.parse(conn.inputStream.bufferedReader().readText())
                404 -> throw IllegalStateException("まだリリースがありません")
                else -> throw IllegalStateException("更新情報を取得できません（HTTP ${conn.responseCode}）")
            }
        } finally { conn.disconnect() }
    }

    fun isNewer(info: UpdateInfo): Boolean =
        info.versionCode > currentVersionCode && Build.VERSION.SDK_INT >= info.minSdk

    suspend fun check() {
        _state.value = UpdateState.Checking
        _state.value = try {
            val info = fetch()
            if (isNewer(info)) UpdateState.Available(info) else UpdateState.UpToDate
        } catch (e: Exception) {
            UpdateState.Error(e.message ?: "更新を確認できませんでした")
        }
    }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    suspend fun downloadAndInstall(info: UpdateInfo) {
        if (!canInstall()) { _state.value = UpdateState.NeedsPermission(info); return }
        try {
            val apk = download(info)
            _state.value = UpdateState.Verifying(info)
            verify(apk, info)
            _state.value = UpdateState.Installing(info)
            install(apk)
        } catch (e: Exception) {
            _state.value = UpdateState.Error(e.message ?: "更新に失敗しました", info)
        }
    }

    private suspend fun download(info: UpdateInfo): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "calendar-${info.versionName}.apk")
        var url = URL(info.apkUrl)
        var conn: HttpURLConnection
        var redirects = 0
        while (true) {
            conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false; connectTimeout = 20_000; readTimeout = 30_000
            }
            val code = conn.responseCode
            if (code in 300..399 && redirects++ < 5) {
                val next = URL(url, conn.getHeaderField("Location"))
                conn.disconnect()
                require(next.protocol == "https") { "安全でない転送先です" }
                url = next
                continue
            }
            if (code != 200) { conn.disconnect(); throw IllegalStateException("ダウンロードに失敗しました（HTTP $code）") }
            break
        }
        val total = conn.contentLengthLong
        try {
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) _state.value = UpdateState.Downloading(info, done.toFloat() / total)
                    }
                }
            }
        } finally { conn.disconnect() }
        out
    }

    private suspend fun verify(apk: File, info: UpdateInfo) = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        apk.inputStream().use { s ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = s.read(buf); if (n < 0) break; digest.update(buf, 0, n) }
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        if (hex != info.sha256) { apk.delete(); throw SecurityException("ファイルの整合性チェックに失敗しました") }

        val pm = context.packageManager
        val archive = archiveInfo(pm, apk.path) ?: run { apk.delete(); throw SecurityException("APK を読み取れません") }
        if (archive.packageName != context.packageName) { apk.delete(); throw SecurityException("パッケージ名が一致しません") }
        val installed = installedInfo(pm)
        if (signatures(archive) != signatures(installed)) {
            apk.delete(); throw SecurityException("署名が一致しません（配布元が異なる可能性があります）")
        }
    }

    @Suppress("DEPRECATION")
    private fun archiveInfo(pm: PackageManager, path: String): PackageInfo? =
        if (Build.VERSION.SDK_INT >= 28) pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNING_CERTIFICATES)
        else pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNATURES)

    @Suppress("DEPRECATION")
    private fun installedInfo(pm: PackageManager): PackageInfo =
        if (Build.VERSION.SDK_INT >= 28) pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        else pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)

    @Suppress("DEPRECATION")
    private fun signatures(p: PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= 28) p.signingInfo?.apkContentsSigners else p.signatures
        return sigs.orEmpty().map { s ->
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    private suspend fun install(apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallResultReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pi.intentSender)
        }
    }
}
