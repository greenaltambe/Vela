package com.greenaltambe.vela.ui.screens

import androidx.compose.runtime.staticCompositionLocalOf
import com.greenaltambe.vela.extractor.Comment
import com.greenaltambe.vela.extractor.VideoInfo
import com.greenaltambe.vela.extractor.VideoSummary
import kotlin.time.Instant

/**
 * What a video page last loaded (details, related videos, the first comments), for the few videos watched most recently. The page is rebuilt
 * whenever it is opened again (from the mini player, after going back), so without this it showed its loading skeletons every time.
 */
class VideoPageCache(private val max: Int = 8) {
    class Entry(val info: VideoInfo?, val related: List<VideoSummary>, val comments: List<Comment>, val commentsNext: String?, val savedAt: Instant)

    private val entries = LinkedHashMap<String, Entry>()

    fun get(videoId: String, now: Instant): Entry? = entries[videoId]?.takeIf { (now - it.savedAt).inWholeMilliseconds < MAX_AGE_MS }

    fun put(videoId: String, entry: Entry) {
        entries.remove(videoId)
        entries[videoId] = entry
        while (entries.size > max) entries.remove(entries.keys.first())
    }

    companion object { const val MAX_AGE_MS = 30 * 60 * 1000L }
}

val LocalVideoPageCache = staticCompositionLocalOf<VideoPageCache?> { null }
