package com.greenaltambe.vela.library

import com.greenaltambe.vela.data.WatchRecord
import kotlin.time.Instant

/** A day heading of the history list: Today, Yesterday, or a calendar date. */
data class HistoryDay(val label: String, val records: List<WatchRecord>)

/**
 * Groups [records] (newest first) by local calendar day. [offsetMinutes] is the viewer's UTC offset, injected so the
 * result does not depend on the machine running the tests. A video watched several times in a day shows once, at its
 * latest watch.
 */
fun groupHistoryByDay(records: List<WatchRecord>, now: Instant, offsetMinutes: Int, distinctPerDay: Boolean = true): List<HistoryDay> {
    fun dayOf(i: Instant) = (i.toEpochMilliseconds() + offsetMinutes * 60_000L).floorDiv(86_400_000L)
    val today = dayOf(now)
    return records.sortedByDescending { it.watchedAt }
        .groupBy { dayOf(it.watchedAt) }
        .map { (day, list) ->
            val shown = if (distinctPerDay) list.distinctBy { it.videoId } else list
            HistoryDay(dayLabel(day, today), shown)
        }
        .sortedByDescending { group -> dayOf(group.records.first().watchedAt) }
}

private fun dayLabel(day: Long, today: Long): String = when (day) {
    today -> "Today"
    today - 1 -> "Yesterday"
    else -> isoDate(day)
}

/** yyyy-MM-dd for a count of days since 1970-01-01 (civil-from-days, no platform date API needed in common code). */
internal fun isoDate(daysSinceEpoch: Long): String {
    val z = daysSinceEpoch + 719468
    val era = z.floorDiv(146097L)
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    val year = if (m <= 2) y + 1 else y
    return "$year-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
}
