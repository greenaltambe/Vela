package com.greenaltambe.vela.home

import com.greenaltambe.vela.data.WatchRecord
import com.greenaltambe.vela.extractor.VideoSummary
import kotlin.time.Instant

/** The quick filters at the top of Home. They work on what is already loaded, so they cost no request. */
enum class HomeFilter(val label: String) {
    ALL("All"), UNWATCHED("Unwatched"), SHORT("Under 10 min"), LIVE("Live"), MUSIC("Music");

    fun matches(v: VideoSummary, watched: Set<String>): Boolean = when (this) {
        ALL -> true
        UNWATCHED -> v.id !in watched
        SHORT -> v.durationSeconds?.let { it in 1 until SHORT_SECONDS } == true
        LIVE -> v.isLive
        MUSIC -> isMusic(v)
    }

    companion object { const val SHORT_SECONDS = 10 * 60L }
}

private val musicWords = listOf("official video", "official audio", "official music", "music video", "lyrics", "lyric video", "audio", "remix", "feat.", "ft.", "live at", "acoustic", "cover", "full album", "playlist", "mix", "soundtrack", "ost")

/** Music by what the listing says (the title's usual words, an artist's "- Topic" channel, VEVO): a guess, not a category YouTube gives. */
fun isMusic(v: VideoSummary): Boolean {
    val channel = v.channelName.lowercase()
    if (channel.endsWith("- topic") || channel.endsWith("vevo")) return true
    // Whole words only: "ost" is in "most" and "mix" in "mixed"; a letter or digit next to the word means it is another word.
    val title = " " + v.title.lowercase().map { if (it.isLetterOrDigit() || it == '.') it else ' ' }.joinToString("") + " "
    return musicWords.any { " $it " in title }
}

/** A topic chip (one of the viewer's interests): the word is in the title or the channel's name. */
fun matchesTopic(v: VideoSummary, topic: String): Boolean = topic.isNotBlank() && (topic.lowercase() in v.title.lowercase() || topic.lowercase() in v.channelName.lowercase())

/** What the viewer sent away from Home: single videos ("Not interested") and whole channels ("Hide this channel"). */
data class HomeDismissed(val videos: Set<String> = emptySet(), val channels: Set<String> = emptySet()) {
    fun hides(v: VideoSummary) = v.id in videos || (v.channelId.isNotBlank() && v.channelId in channels)
}

fun List<VideoSummary>.withoutDismissed(d: HomeDismissed): List<VideoSummary> = if (d.videos.isEmpty() && d.channels.isEmpty()) this else filterNot(d::hides)

/**
 * "Because you watched": the related lists of the last few videos, merged. A video that several of them recommend ranks higher; one that is
 * high in a list ranks above one that is low; nothing already watched (or one of the seeds itself) is shown; and no channel fills the shelf,
 * at most [perChannel] videos of each.
 */
fun becauseYouWatched(lists: List<List<VideoSummary>>, exclude: Set<String>, perChannel: Int = 2, limit: Int = 20): List<VideoSummary> {
    class Entry(val video: VideoSummary, var score: Double, val first: Int)
    val entries = LinkedHashMap<String, Entry>()
    lists.forEachIndexed { li, list ->
        list.forEachIndexed { rank, v ->
            if (v.id in exclude || v.isShort) return@forEachIndexed
            val gain = 10.0 + 1.0 / (rank + 1)          // appearing in a list is worth far more than any position in it
            val e = entries[v.id]
            if (e == null) entries[v.id] = Entry(v, gain, li * 1000 + rank) else e.score += gain
        }
    }
    val perChannelCount = HashMap<String, Int>()
    return entries.values.sortedWith(compareByDescending<Entry> { it.score }.thenBy { it.first })
        .filter { e ->
            val key = e.video.channelId.ifBlank { e.video.channelName }
            val n = perChannelCount.getOrElse(key) { 0 }
            if (n >= perChannel) false else { perChannelCount[key] = n + 1; true }
        }
        .take(limit).map { it.video }
}

/** The channels watched at least [minWatches] times (several videos, not several sessions of one) that the viewer does not subscribe to, most watched first. */
fun channelsWatchedALot(history: List<WatchRecord>, subscribed: Set<String>, minWatches: Int = 2, n: Int = 3, hidden: Set<String> = emptySet()): List<String> =
    history.filter { it.channelId.isNotBlank() && it.channelId !in subscribed && it.channelId !in hidden }
        .groupBy { it.channelId }
        .mapValues { (_, rows) -> rows.map { it.videoId }.distinct().size }
        .filterValues { it >= minWatches }
        .entries.sortedByDescending { it.value }.take(n).map { it.key }

private val stopWords = setOf(
    "the", "and", "for", "with", "this", "that", "from", "your", "you", "are", "was", "how", "what", "why", "when", "who", "his", "her", "its", "not", "but", "all", "can", "will", "just",
    "have", "has", "had", "they", "them", "their", "into", "out", "about", "more", "most", "best", "new", "full", "video", "videos", "official", "part", "episode", "ep", "live", "vs", "top",
    "2020", "2021", "2022", "2023", "2024", "2025", "2026", "first", "last", "ever", "make", "made", "get", "got", "one", "two", "way", "need", "everything", "explained", "reaction", "trailer",
)

/** The words that come up most across [titles] (four letters or more, no filler): the viewer's interests, as search words. */
fun topKeywords(titles: List<String>, n: Int = 3, minCount: Int = 3): List<String> {
    val counts = HashMap<String, Int>()
    titles.forEach { t ->
        t.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 4 && it !in stopWords && !it.all(Char::isDigit) }.distinct().forEach { counts[it] = (counts[it] ?: 0) + 1 }
    }
    return counts.entries.filter { it.value >= minCount }.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).take(n).map { it.key }
}

/** "New since you last opened": uploads published after [lastOpened] (when the time is unknown, nothing is marked). */
fun isNewSince(v: VideoSummary, lastOpened: Instant?): Boolean = lastOpened != null && v.uploadedAt?.let { it > lastOpened } == true

/** Interleaves the lists one video at a time, so no list leads the shelf: [[a1,a2],[b1,b2]] gives a1,b1,a2,b2. */
fun <T> interleave(lists: List<List<T>>): List<T> {
    val out = ArrayList<T>()
    var i = 0
    while (lists.any { i < it.size }) { lists.forEach { if (i < it.size) out += it[i] }; i++ }
    return out
}
