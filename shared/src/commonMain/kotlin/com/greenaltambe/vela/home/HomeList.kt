package com.greenaltambe.vela.home

import com.greenaltambe.vela.auth.FeedGroup
import com.greenaltambe.vela.auth.HomePage
import com.greenaltambe.vela.extractor.Page
import com.greenaltambe.vela.extractor.PagedList
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.extractor.videoPagedList

/** One list of Home (All, or a chip), page after page: the feed's videos, and the groups YouTube set among them. */
data class HomeList(val videos: PagedList<VideoSummary> = videoPagedList(), val groups: List<FeedGroup> = emptyList()) {
    val isEmpty: Boolean get() = videos.items.isEmpty() && groups.isEmpty()

    /**
     * This list with [page] after it. A video already in the feed is not added again; a group keeps only videos that are neither in the feed nor in
     * an earlier group. Each group is tied to the video it now follows: the nearest one before it on its own page that this page added, else the
     * last one the list already had (null: it comes first).
     */
    fun append(page: HomePage): HomeList {
        val next = videos.append(Page(page.videos, page.continuation))
        val added = next.items.drop(videos.items.size).mapTo(HashSet()) { it.id }
        val taken = next.items.mapTo(HashSet()) { it.id }
        groups.forEach { g -> g.videos.forEach { taken.add(it.id) } }
        val order = page.videos.map { it.id }
        val new = page.groups.mapNotNull { group ->
            val own = group.videos.filter { taken.add(it.id) }
            if (own.isEmpty()) return@mapNotNull null
            val before = group.afterVideoId?.let { order.indexOf(it) } ?: -1
            val anchor = order.take(before + 1).lastOrNull { it in added } ?: videos.items.lastOrNull()?.id
            group.copy(videos = own, afterVideoId = anchor)
        }
        return HomeList(next, groups + new)
    }
}
