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
    /** The unfiltered list: its videos and YouTube's groups among them. */
    var list = com.greenaltambe.vela.home.HomeList()
    val videos: PagedList<VideoSummary> get() = list.videos
    var showing: String = "Trending"
    var note: String? = null
    var loadedAt: Instant? = null
    /** Replaced (not reset) when the list it belongs to is thrown away: the old list saves its position as it goes, which must not land on the new one. */
    var scroll = ScrollMemory()
        private set

    /** The chips of the last unfiltered load, the chosen one (its label; null = All) and the chips that answered with nothing: per account, cleared when it changes. */
    var chips: List<com.greenaltambe.vela.auth.HomeChip> = emptyList()
    var selectedChip: String? = null
    val emptyChips = HashSet<String>()
    private var chipsAccount: String? = null
    /** One chip's own list and when it came; All keeps [list] / [loadedAt]. */
    class ChipEntry(var list: com.greenaltambe.vela.home.HomeList = com.greenaltambe.vela.home.HomeList(), var loadedAt: Instant? = null)
    private val chipEntries = HashMap<String, ChipEntry>()
    fun chipEntry(label: String) = chipEntries.getOrPut(label) { ChipEntry() }
    fun chipUsable(label: String, now: Instant) = chipEntry(label).let { it.list.videos.items.isNotEmpty() && it.loadedAt?.let { at -> (now - at).inWholeMilliseconds < MAX_AGE_MS } == true }
    /** Called by Home on every visit: another account starts with no chips and no chosen one. */
    fun forAccount(account: String) {
        if (chipsAccount == account) return
        chipsAccount = account; chips = emptyList(); selectedChip = null; emptyChips.clear(); chipEntries.clear()
    }
    private var region: String? = null
    /** Called by Home on every visit: what was loaded for another region (feed, chips, shelves, scroll position) is not shown again. */
    fun forRegion(next: String) {
        if (region == next) return
        val changed = region != null
        region = next
        if (!changed) return
        accountId = null; list = com.greenaltambe.vela.home.HomeList(); loadedAt = null; note = null
        chips = emptyList(); selectedChip = null; emptyChips.clear(); chipEntries.clear(); shelves.clear()
        scroll = ScrollMemory()
    }
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
