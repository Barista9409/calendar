package io.github.barista9409.calendar.ui.timeline

/** 時間軸上で重なる予定を横に並べるための配置計算（純粋関数） */
object EventLayout {
    data class Item(val start: Int, val end: Int)
    data class Placement(val column: Int, val columns: Int)

    /** start/end は分（0..1440）。結果は入力と同じ順序 */
    fun layout(items: List<Item>): List<Placement> {
        if (items.isEmpty()) return emptyList()
        val order = items.indices.sortedWith(compareBy({ items[it].start }, { -items[it].end }))
        val result = arrayOfNulls<Placement>(items.size)
        var cluster = mutableListOf<Int>()
        val colEnds = mutableListOf<Int>()
        val colOf = IntArray(items.size)
        var clusterEnd = -1

        fun flush() {
            val n = colEnds.size
            for (i in cluster) result[i] = Placement(colOf[i], n)
            cluster = mutableListOf(); colEnds.clear(); clusterEnd = -1
        }

        for (i in order) {
            val it = items[i]
            val end = maxOf(it.end, it.start + 15)
            if (cluster.isNotEmpty() && it.start >= clusterEnd) flush()
            var col = colEnds.indexOfFirst { e -> e <= it.start }
            if (col < 0) { col = colEnds.size; colEnds.add(end) } else colEnds[col] = end
            colOf[i] = col
            cluster.add(i)
            clusterEnd = maxOf(clusterEnd, end)
        }
        flush()
        return result.map { it!! }
    }
}
