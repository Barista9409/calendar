package io.github.barista9409.calendar.google

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class NeedsAuthorizationException(val account: String) : Exception("Google との連携が必要です（$account）")
class AuthorizationCancelledException : Exception("連携がキャンセルされました")

/**
 * Google へのアクセス許可（アカウントごと）。トークンは Google Play 開発者サービスが管理し、アプリには保存しない。
 */
class GoogleAuth(private val context: Context) {

    /** ユーザーの同意画面が必要なときに Activity へ渡す要求 */
    class Resolution(val intent: PendingIntent, val result: CompletableDeferred<AuthorizationResult?>)

    private val _resolutions = MutableSharedFlow<Resolution>(extraBufferCapacity = 1)
    val resolutions: SharedFlow<Resolution> = _resolutions.asSharedFlow()

    private fun request(account: String) = AuthorizationRequest.builder()
        .setRequestedScopes(SCOPES.map { Scope(it) })
        .setAccount(Account(account, "com.google"))
        .build()

    /** アクセストークンを取得。interactive=false で同意が必要な場合は NeedsAuthorizationException */
    suspend fun accessToken(account: String, interactive: Boolean): String {
        val res = Identity.getAuthorizationClient(context).authorize(request(account)).await()
        if (res.hasResolution()) {
            if (!interactive) throw NeedsAuthorizationException(account)
            val deferred = CompletableDeferred<AuthorizationResult?>()
            _resolutions.emit(Resolution(requireNotNull(res.pendingIntent), deferred))
            val r = deferred.await() ?: throw AuthorizationCancelledException()
            return r.accessToken ?: throw AuthorizationCancelledException()
        }
        return res.accessToken ?: throw NeedsAuthorizationException(account)
    }

    /** 期限切れなどで拒否されたトークンを破棄 */
    suspend fun invalidate(token: String) = withContext(Dispatchers.IO) {
        runCatching { GoogleAuthUtil.clearToken(context, token) }
    }

    fun parseResult(data: android.content.Intent?): AuthorizationResult? =
        runCatching { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data) }.getOrNull()

    companion object {
        /**
         * 「このアプリが作ったファイルだけ」を扱うドライブの権限。Google の審査が不要な通常の権限なので、
         * テストユーザーの登録・警告画面・7 日での期限切れがない。
         * （カレンダーの読み書きは端末のカレンダー同期を使うので、Google カレンダーの権限は使わない）
         */
        const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
        val SCOPES = listOf(SCOPE_DRIVE_FILE)
    }
}
