package io.github.barista9409.calendar.widget

/**
 * 月表示の 1 週分の「段」割り当て（Google カレンダーと同じく、複数日の予定は横につながる帯）。
 * items は表示順（長い予定・早い予定が先）に並べておく。col は 0..6。
 */
object MonthLayout {
    /**
     * 1 週分の予定を表示順に並べ、それぞれの開始列・終了列（0..6）を求める。
     * アプリの月表示と月ウィジェットで同じものを使う（並び方を揃えるため）。
     * 終日・複数日の予定が先、次に開始日が早い順、長い順、開始時刻の順。
     */
    fun weekItems(
        weekStart: java.time.LocalDate,
        eventsByDay: Map<java.time.LocalDate, List<io.github.barista9409.calendar.model.EventInstance>>,
        zone: java.time.ZoneId,
    ): List<Triple<io.github.barista9409.calendar.model.EventInstance, Int, Int>> {
        val weekEnd = weekStart.plusDays(6)
        fun col(d: java.time.LocalDate) = java.time.temporal.ChronoUnit.DAYS.between(weekStart, d).toInt()
        return (0..6).flatMap { eventsByDay[weekStart.plusDays(it.toLong())].orEmpty() }.distinctBy { it.instanceId }
            .sortedWith(compareByDescending<io.github.barista9409.calendar.model.EventInstance> { it.isLong }
                .thenBy { maxOf(it.startDate(zone), weekStart) }
                .thenByDescending { minOf(it.lastDate(zone), weekEnd).toEpochDay() - maxOf(it.startDate(zone), weekStart).toEpochDay() }
                .thenBy { it.begin })
            .map { e -> Triple(e, col(maxOf(e.startDate(zone), weekStart)), col(minOf(e.lastDate(zone), weekEnd))) }
    }

    data class Placed<T>(val item: T, val start: Int, val end: Int)

    fun <T> lanes(items: List<Triple<T, Int, Int>>): List<List<Placed<T>>> {
        val lanes = mutableListOf<MutableList<Placed<T>>>()
        for ((item, s, e) in items) {
            val lane = lanes.firstOrNull { l -> l.none { it.start <= e && s <= it.end } }
                ?: mutableListOf<Placed<T>>().also { lanes += it }
            lane += Placed(item, s, e)
        }
        return lanes.map { l -> l.sortedBy { it.start } }
    }

    /**
     * 件数を指定したとき：最大 max 件まで表示し、それより多い日は「•••」を出す
     * （行の高さは max 件＋「•••」の 1 行分で固定するので、最大 max 件まで必ず見える）
     */
    fun <T> fitMax(lanes: List<List<Placed<T>>>, max: Int): Pair<Int, IntArray> {
        val shown = minOf(lanes.size, max)
        val total = IntArray(7); val visible = IntArray(7)
        lanes.forEachIndexed { i, l -> l.forEach { p -> for (c in p.start..p.end) { total[c]++; if (i < shown) visible[c]++ } } }
        return shown to IntArray(7) { total[it] - visible[it] }
    }

    /** 件数を指定したときの行の高さ（日付の行＋max 件＋「•••」の行） */
    fun rowHeightFor(max: Int, laneDp: Float, numberRowDp: Float = 24f): Float = numberRowDp + 2f + laneDp * (max + 1)

    /** 表示する段数と、各日の「+N」（隠れた予定の数）。capacity は 1 日に置ける最大行数 */
    fun <T> fit(lanes: List<List<Placed<T>>>, capacity: Int): Pair<Int, IntArray> {
        val total = IntArray(7)
        lanes.forEach { l -> l.forEach { p -> for (c in p.start..p.end) total[c]++ } }
        if (lanes.size <= capacity) return lanes.size to IntArray(7)
        val shown = (capacity - 1).coerceAtLeast(0)
        val visible = IntArray(7)
        lanes.take(shown).forEach { l -> l.forEach { p -> for (c in p.start..p.end) visible[c]++ } }
        return shown to IntArray(7) { total[it] - visible[it] }
    }
}
