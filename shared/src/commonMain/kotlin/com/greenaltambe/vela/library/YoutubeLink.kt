package com.greenaltambe.vela.library

/** What a YouTube address points at, for opening it in the app (Android "open with", the desktop command line, a shared link). */
sealed interface YoutubeLink {
    data class Video(val id: String, val startSeconds: Long? = null) : YoutubeLink
    data class Playlist(val id: String) : YoutubeLink
    data class Channel(val id: String) : YoutubeLink
}

object YoutubeLinks {
    private val hosts = setOf("youtube.com", "youtu.be", "youtube-nocookie.com")
    private val videoId = Regex("[A-Za-z0-9_-]{11}")
    private val channelId = Regex("UC[A-Za-z0-9_-]{22}")
    private val playlistId = Regex("[A-Za-z0-9_-]{10,64}")

    /** The first YouTube address found in [text] (a shared message is often "title https://youtu.be/…"), or null. */
    fun find(text: String): YoutubeLink? =
        text.split(' ', '\n', '\t').asSequence().map { it.trim() }.filter { it.startsWith("http", ignoreCase = true) }.mapNotNull(::parse).firstOrNull()

    fun parse(raw: String): YoutubeLink? {
        val s = raw.trim()
        val afterScheme = when {
            s.startsWith("https://", true) -> s.substring(8)
            s.startsWith("http://", true) -> s.substring(7)
            else -> return null
        }.substringBefore('#')
        val hostAndPath = afterScheme.substringBefore('?')
        val host = hostAndPath.substringBefore('/').lowercase().substringBefore(':').removePrefix("www.").removePrefix("m.").removePrefix("music.")
        if (host !in hosts) return null
        val path = hostAndPath.substringAfter('/', "").trim('/').split('/').filter { it.isNotEmpty() }
        val query = afterScheme.substringAfter('?', "").split('&').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
        val start = (query["t"] ?: query["start"])?.let(::seconds)
        fun video(id: String?) = id?.takeIf { videoId.matches(it) }?.let { YoutubeLink.Video(it, start) }
        if (host == "youtu.be") return video(path.firstOrNull())
        return when (path.firstOrNull()) {
            "watch" -> video(query["v"]) ?: query["list"]?.takeIf { playlistId.matches(it) }?.let { YoutubeLink.Playlist(it) }
            "shorts", "live", "embed", "v" -> video(path.getOrNull(1))
            "playlist" -> query["list"]?.takeIf { playlistId.matches(it) }?.let { YoutubeLink.Playlist(it) }
            "channel" -> path.getOrNull(1)?.takeIf { channelId.matches(it) }?.let { YoutubeLink.Channel(it) }
            else -> null
        }
    }

    /** "83", "83s", "1m23s", "1h2m3s" as seconds; null if it is none of those. */
    private fun seconds(t: String): Long? {
        t.toLongOrNull()?.let { return it }
        val m = Regex("(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?").matchEntire(t) ?: return null
        if (t.isEmpty()) return null
        val (h, mi, se) = m.destructured
        return (h.toLongOrNull() ?: 0) * 3600 + (mi.toLongOrNull() ?: 0) * 60 + (se.toLongOrNull() ?: 0)
    }
}
