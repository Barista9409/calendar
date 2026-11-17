package io.github.barista9409.calendar.google

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Google Drive API（REST）の必要な部分だけを扱う */
class ApiException(val code: Int, message: String) : Exception(message)

class DriveApi(private val auth: GoogleAuth) {

    data class DriveFile(
        val id: String, val name: String, val mimeType: String, val webViewLink: String?, val iconLink: String?, val size: Long?,
    )

    data class Permission(val id: String, val type: String, val role: String, val email: String?)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    private val base = "https://www.googleapis.com/drive/v3"

    /** トークン付きでリクエストし、401 なら 1 回だけトークンを更新して再試行 */
    private suspend fun <T> withToken(account: String, block: (String) -> Pair<Int, T>): T {
        var token = auth.accessToken(account, interactive = false)
        val first = withContext(Dispatchers.IO) { block(token) }
        if (first.first != 401) return first.second
        auth.invalidate(token)
        token = auth.accessToken(account, interactive = false)
        return withContext(Dispatchers.IO) { block(token) }.second
    }

    private fun open(url: String, method: String, token: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 60_000
            requestMethod = if (method == "PATCH") "POST" else method
            if (method == "PATCH") setRequestProperty("X-HTTP-Method-Override", "PATCH")
            setRequestProperty("Authorization", "Bearer $token")
        }

    private fun HttpURLConnection.readJson(): Pair<Int, JSONObject?> {
        val code = responseCode
        val text = (if (code in 200..299) inputStream else errorStream)?.bufferedReader()?.use { it.readText() }
        if (code == 401) return code to null
        if (code !in 200..299) {
            val msg = runCatching { JSONObject(text ?: "").getJSONObject("error").getString("message") }.getOrNull()
            throw ApiException(code, when (code) {
                403 -> "ドライブへのアクセス権がありません（${msg ?: 403}）"
                404 -> "ファイルが見つかりません"
                else -> msg ?: "ドライブとの通信に失敗しました（HTTP $code）"
            })
        }
        return code to text?.takeIf { it.isNotBlank() }?.let { JSONObject(it) }
    }

    private suspend fun json(account: String, method: String, url: String, body: JSONObject? = null): JSONObject? =
        withToken(account) { token ->
            val c = open(url, method, token)
            try {
                if (body != null) {
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    c.outputStream.use { it.write(body.toString().toByteArray()) }
                }
                c.readJson()
            } finally { c.disconnect() }
        }

    private fun parseFile(o: JSONObject) = DriveFile(
        o.getString("id"), o.optString("name"), o.optString("mimeType"),
        o.optString("webViewLink").ifBlank { null }, o.optString("iconLink").ifBlank { null },
        o.optString("size").toLongOrNull(),
    )

    data class Folder(val id: String, val name: String)

    /** 名前と親フォルダ（"root" を渡すとマイドライブ自体の ID が得られる） */
    suspend fun info(account: String, id: String): Pair<Folder, String?> {
        val o = requireNotNull(json(account, "GET", "$base/files/${enc(id)}?fields=id,name,parents"))
        return Folder(o.getString("id"), o.getString("name")) to o.optJSONArray("parents")?.optString(0)?.ifBlank { null }
    }

    /** appProperties の key=value で、このアプリが作ったフォルダを探す（parent を指定するとその中だけ） */
    suspend fun findFolder(account: String, key: String, value: String, parent: String? = null): String? {
        val q = "mimeType='application/vnd.google-apps.folder' and trashed=false and " +
            (parent?.let { "'$it' in parents and " } ?: "") +
            "appProperties has { key='$key' and value='${value.replace("'", "\\'")}' }"
        val res = json(account, "GET", "$base/files?q=${enc(q)}&fields=files(id)&spaces=drive")
        return res?.optJSONArray("files")?.takeIf { it.length() > 0 }?.getJSONObject(0)?.getString("id")
    }

    suspend fun createFolder(account: String, name: String, parent: String?, props: Map<String, String>): String {
        val body = JSONObject().put("name", name).put("mimeType", "application/vnd.google-apps.folder")
            .put("appProperties", JSONObject(props))
        if (parent != null) body.put("parents", JSONArray().put(parent))
        return requireNotNull(json(account, "POST", "$base/files?fields=id", body)).getString("id")
    }

    suspend fun permissions(account: String, fileId: String): List<Permission> {
        val res = json(account, "GET", "$base/files/${enc(fileId)}/permissions?fields=permissions(id,type,role,emailAddress)")
        val arr = res?.optJSONArray("permissions") ?: JSONArray()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Permission(o.getString("id"), o.getString("type"), o.getString("role"), o.optString("emailAddress").ifBlank { null })
        }
    }

    /** 「リンクを知っている人は誰でも閲覧可」にする */
    suspend fun shareAnyoneWithLink(account: String, fileId: String) {
        json(account, "POST", "$base/files/${enc(fileId)}/permissions?fields=id",
            JSONObject().put("type", "anyone").put("role", "reader").put("allowFileDiscovery", false))
    }

    /**
     * 「リンクを知っている人は閲覧可」のファイルを、ログインなしでダウンロード（家族のアプリでプレビューするとき）。
     * 大きなファイルなどで取得できなければ例外。
     */
    suspend fun downloadPublic(fileId: String, dest: File) = withContext(Dispatchers.IO) {
        val c = (URL("https://drive.google.com/uc?export=download&id=${enc(fileId)}").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 60_000; instanceFollowRedirects = true
        }
        try {
            val code = c.responseCode
            val type = c.contentType.orEmpty()
            // 権限がない・確認画面が出る場合は HTML が返る
            if (code !in 200..299 || type.startsWith("text/html")) throw ApiException(code, "ダウンロードできませんでした")
            dest.parentFile?.mkdirs()
            val tmp = File(dest.path + ".part")
            c.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            tmp.renameTo(dest)
        } finally { c.disconnect() }
    }

    /** 再開可能アップロード（1 回の PUT で送信） */
    suspend fun upload(
        account: String, name: String, mimeType: String, parent: String, props: Map<String, String>,
        length: Long, source: () -> InputStream, onProgress: (Float) -> Unit,
    ): DriveFile = withToken(account) { token ->
        val meta = JSONObject().put("name", name).put("mimeType", mimeType).put("parents", JSONArray().put(parent))
            .put("appProperties", JSONObject(props))
        val init = open("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable" +
            "&fields=id,name,mimeType,webViewLink,iconLink,size", "POST", token)
        val location: String
        try {
            init.doOutput = true
            init.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            init.setRequestProperty("X-Upload-Content-Type", mimeType)
            if (length >= 0) init.setRequestProperty("X-Upload-Content-Length", length.toString())
            init.outputStream.use { it.write(meta.toString().toByteArray()) }
            if (init.responseCode == 401) return@withToken 401 to DriveFile("", "", "", null, null, null)
            if (init.responseCode !in 200..299) init.readJson()
            location = requireNotNull(init.getHeaderField("Location")) { "アップロードを開始できませんでした" }
        } finally { init.disconnect() }

        val put = (URL(location).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"; doOutput = true; connectTimeout = 15_000; readTimeout = 120_000
            setRequestProperty("Content-Type", mimeType)
            if (length >= 0) setFixedLengthStreamingMode(length) else setChunkedStreamingMode(256 * 1024)
        }
        try {
            source().use { input ->
                put.outputStream.use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf, 0, n); done += n
                        if (length > 0) onProgress(done.toFloat() / length)
                    }
                }
            }
            val (code, o) = put.readJson()
            code to parseFile(requireNotNull(o))
        } finally { put.disconnect() }
    }

    suspend fun download(account: String, fileId: String, dest: File) {
        withToken(account) { token ->
            val c = open("$base/files/${enc(fileId)}?alt=media", "GET", token)
            try {
                val code = c.responseCode
                if (code == 401) return@withToken 401 to Unit
                if (code !in 200..299) c.readJson()
                dest.parentFile?.mkdirs()
                val tmp = File(dest.path + ".part")
                c.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                tmp.renameTo(dest)
                code to Unit
            } finally { c.disconnect() }
        }
    }

    /** 自分のファイルならゴミ箱へ（30 日間は復元可能） */
    suspend fun trash(account: String, fileId: String) {
        json(account, "PATCH", "$base/files/${enc(fileId)}?fields=id", JSONObject().put("trashed", true))
    }

}
