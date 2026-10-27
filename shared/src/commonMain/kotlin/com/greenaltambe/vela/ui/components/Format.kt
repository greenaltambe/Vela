package com.greenaltambe.vela.ui.components

import com.greenaltambe.vela.library.formatTimestamp

fun formatDuration(seconds: Long?): String? = seconds?.takeIf { it > 0 }?.let(::formatTimestamp)

/** 1234 -> "1.2K views", 3_400_000 -> "3.4M views". */
fun formatViews(count: Long?): String? {
    count ?: return null
    return when {
        count >= 1_000_000_000 -> "${oneDecimal(count / 1e9)}B views"
        count >= 1_000_000 -> "${oneDecimal(count / 1e6)}M views"
        count >= 1_000 -> "${oneDecimal(count / 1e3)}K views"
        else -> "$count views"
    }
}

fun formatCount(count: Long?): String? = formatViews(count)?.removeSuffix(" views")

private fun oneDecimal(v: Double): String {
    val tenths = (v * 10).toLong()
    return if (tenths % 10 == 0L) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
}

/** "3 days ago", "2 hours ago", "just now"; null when the upload time is unknown. */
fun formatAge(uploadedAt: kotlin.time.Instant?, now: kotlin.time.Instant): String? {
    uploadedAt ?: return null
    val seconds = (now - uploadedAt).inWholeSeconds
    if (seconds < 0) return null
    fun unit(n: Long, name: String) = "$n $name${if (n == 1L) "" else "s"} ago"
    return when {
        seconds < 60 -> "just now"
        seconds < 3600 -> unit(seconds / 60, "minute")
        seconds < 86_400 -> unit(seconds / 3600, "hour")
        seconds < 86_400 * 30 -> unit(seconds / 86_400, "day")
        seconds < 86_400 * 365 -> unit(seconds / (86_400 * 30), "month")
        else -> unit(seconds / (86_400 * 365), "year")
    }
}
