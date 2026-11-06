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
    /** Each shelf's last answer, per account, so reopening Home shows the shelves at once (see [shelf]). */
    private val shelves = HashMap<String, Pair<Instant, com.greenaltambe.vela.home.ShelfData?>>()
    /** A shelf's last answer and when it came, however old: an old one is shown at once and refreshed (see [isFresh]); it is only replaced by a good new answer. */
    fun shelf(account: String, id: String): Pair<Instant, com.greenaltambe.vela.home.ShelfData?>? = shelves["$account|$id"]
    fun isFresh(at: Instant, now: Instant) = (now - at).inWholeMilliseconds < SHELF_MAX_AGE_MS
    fun keepShelf(account: String, id: String, at: Instant, data: com.greenaltambe.vela.home.ShelfData?) { shelves["$account|$id"] = at to data }
    fun forgetShelves() = shelves.clear()

    fun usable(account: String, now: Instant) = accountId == account && videos.items.isNotEmpty() && loadedAt?.let { (now - it).inWholeMilliseconds < MAX_AGE_MS } == true

    companion object { const val MAX_AGE_MS = 30 * 60 * 1000L; const val SHELF_MAX_AGE_MS = 20 * 60 * 1000L }
}

val LocalHomeFeed = staticCompositionLocalOf<HomeFeedStore?> { null }
