package io.github.barista9409.calendar.update

import org.json.JSONObject

/** calendar-releases/update.json の内容 */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val minSdk: Int,
    val apkUrl: String,
    val sha256: String,
    val releaseNotes: String,
    val mandatory: Boolean,
    val publishedAt: String,
) {
    companion object {
        fun parse(json: String): UpdateInfo {
            val latest = JSONObject(json).getJSONObject("latest")
            val url = latest.getString("apkUrl")
            require(url.startsWith("https://github.com/barista9409/calendar-releases/") ||
                url.startsWith("https://objects.githubusercontent.com/")) { "配布元が不正です: $url" }
            val sha = latest.getString("sha256").lowercase()
            require(sha.matches(Regex("[0-9a-f]{64}"))) { "SHA-256 の形式が不正です" }
            return UpdateInfo(
                versionCode = latest.getInt("versionCode"),
                versionName = latest.getString("versionName"),
                minSdk = latest.optInt("minSdk", 26),
                apkUrl = url,
                sha256 = sha,
                releaseNotes = latest.optString("releaseNotes", ""),
                mandatory = latest.optBoolean("mandatory", false),
                publishedAt = latest.optString("publishedAt", ""),
            )
        }

    }
}
