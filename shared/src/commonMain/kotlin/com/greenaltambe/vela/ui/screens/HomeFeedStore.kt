package com.greenaltambe.vela.ui.screens

import androidx.compose.runtime.staticCompositionLocalOf
import com.greenaltambe.vela.extractor.PagedList
import com.greenaltambe.vela.extractor.VideoSummary
import kotlin.time.Instant

/**
 * What Home last showed, kept for as long as the app shell lives (the page itself is rebuilt every time you come back to it). Coming back
 * to Home within [MAX_AGE_MS] shows this at once, at the same scroll position, with no loading; pulling to refresh, switching account
 * or an old or failed feed fetches again.
 */
class HomeFeedStore {
    var accountId: String? = null
    var videos: PagedList<VideoSummary> = com.greenaltambe.vela.extractor.videoPagedList()
    var showing: String = "Trending"
    var note: String? = null
    var loadedAt: Instant? = null
    val scroll = ScrollMemory()

    fun usable(account: String, now: Instant) = accountId == account && videos.items.isNotEmpty() && loadedAt?.let { (now - it).inWholeMilliseconds < MAX_AGE_MS } == true

    companion object { const val MAX_AGE_MS = 30 * 60 * 1000L }
}

val LocalHomeFeed = staticCompositionLocalOf<HomeFeedStore?> { null }
