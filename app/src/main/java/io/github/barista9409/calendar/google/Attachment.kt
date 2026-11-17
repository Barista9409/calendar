package io.github.barista9409.calendar.google

/** 予定の添付ファイル（Google ドライブのファイル） */
data class Attachment(val fileId: String?, val title: String, val mimeType: String?, val fileUrl: String)

/**
 * 添付ファイルは、予定の「説明」の最後に次の形で書き込む（Google カレンダーアプリからもリンクをタップして開ける）。
 *
 *   ――― 添付ファイル ―――
 *   ・見積書.pdf：https://drive.google.com/file/d/xxxx/view
 *
 * Google カレンダーの「添付ファイル」欄を使うには審査が必要な権限がいるため、この形にしている。
 */
object AttachmentText {
    const val HEADER = "――― 添付ファイル ―――"
    private val LINE = Regex("""^・(.*)：(https://\S+)\s*$""")
    private val FILE_ID = Regex("""/file/d/([A-Za-z0-9_-]+)|[?&]id=([A-Za-z0-9_-]+)""")

    fun fileUrl(fileId: String) = "https://drive.google.com/file/d/$fileId/view"

    private fun split(description: String?): Pair<String, List<String>> {
        val text = description.orEmpty()
        val i = text.indexOf(HEADER)
        if (i < 0) return text.trimEnd() to emptyList()
        val body = text.substring(0, i).trimEnd()
        val lines = text.substring(i + HEADER.length).lines().map { it.trim() }.filter { it.isNotEmpty() }
        return body to lines
    }

    /** 説明から添付ファイルの一覧を読む */
    fun parse(description: String?): List<Attachment> = split(description).second.mapNotNull { line ->
        LINE.find(line)?.let { m ->
            val url = m.groupValues[2]
            val id = FILE_ID.find(url)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }
            Attachment(id, m.groupValues[1].trim(), null, url)
        }
    }

    /** 添付のブロックを除いた説明（画面に出す・編集する本文） */
    fun body(description: String?): String = split(description).first

    /** 添付のブロックだけ（編集して保存するときに付け直す） */
    fun block(description: String?): String {
        val lines = split(description).second
        return if (lines.isEmpty()) "" else HEADER + "\n" + lines.joinToString("\n")
    }

    /** 本文と添付のブロックをつなぐ */
    fun join(body: String, block: String): String = when {
        block.isBlank() -> body
        body.isBlank() -> block
        else -> body.trimEnd() + "\n\n" + block
    }

    private fun build(body: String, items: List<Attachment>): String =
        join(body, if (items.isEmpty()) "" else HEADER + "\n" + items.joinToString("\n") { "・${clean(it.title)}：${it.fileUrl}" })

    private fun clean(title: String) = title.replace("\n", " ").replace("：", ":")

    fun withAdded(description: String?, a: Attachment): String = build(body(description), parse(description) + a)

    fun withRemoved(description: String?, a: Attachment): String =
        build(body(description), parse(description).filterNot { it.fileUrl == a.fileUrl || (a.fileId != null && it.fileId == a.fileId) })
}
