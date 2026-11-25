package com.greenaltambe.vela.home

import com.greenaltambe.vela.auth.FeedGroup
import com.greenaltambe.vela.auth.FeedGroupKind
import com.greenaltambe.vela.core.settings.HomeShelf
import com.greenaltambe.vela.extractor.VideoSummary

/** Where one shelf sits in the Home list: after [afterVideos] videos of the main list (0: above the first). */
data class ShelfSlot(val shelf: HomeShelf, val afterVideos: Int)

/**
 * Weaves the shelves into the list the way YouTube does, instead of stacking them above it. Continue watching goes first (it is something to
 * do, not something to be recommended); the others follow the viewer's order: the first after [FIRST_AFTER] videos, then one every [step] videos.
 * The places depend only on the order and the density, never on how many videos have loaded, so a shelf does not move when the list grows. The list
 * puts a shelf whose place is past the end of a short list at that end, so Home is never blank.
 */
fun planSlots(shelves: List<HomeShelf>, step: Int): List<ShelfSlot> {
    val gap = step.coerceAtLeast(2)
    val out = ArrayList<ShelfSlot>()
    var n = 0
    shelves.distinct().forEach { shelf ->
        if (shelf == HomeShelf.CONTINUE) out += ShelfSlot(shelf, 0)
        else { out += ShelfSlot(shelf, FIRST_AFTER + n * gap); n++ }
    }
    return out.sortedBy { it.afterVideos }
}

const val FIRST_AFTER = 3

/** A shelf needs this many videos (after the filters and the dismissed ones) to be drawn: one or two look like a mistake. Continue watching is exempt. */
const val MIN_SHELF_VIDEOS = 3

/** Whether the settings let a group of this kind show: a row of Shorts follows the Shorts setting and nothing else, every other row the groups switch. */
fun FeedGroup.allowed(suggestedOn: Boolean, shortsOn: Boolean): Boolean = if (kind == FeedGroupKind.SHORTS) shortsOn else suggestedOn

/** A group of YouTube's that is to be drawn, with the videos left of it. [key] stays the same while filters change what is in it. */
data class ShownGroup(val key: String, val group: FeedGroup)

/**
 * The groups to draw. [passed] holds, by id, the videos that came through the lists' filters (as those left them: titles may be rewritten); a Short
 * inside another group also needs Shorts on. While a Home filter is on ([filtering]) no group is drawn, like the other shelves. A group left with
 * fewer than [MIN_SHELF_VIDEOS] is not drawn.
 */
fun visibleGroups(groups: List<FeedGroup>, passed: Map<String, VideoSummary>, suggestedOn: Boolean, shortsOn: Boolean, filtering: Boolean): List<ShownGroup> =
    if (filtering) emptyList() else groups.mapNotNull { group ->
        if (!group.allowed(suggestedOn, shortsOn)) return@mapNotNull null
        val left = group.videos.mapNotNull { passed[it.id] }.filter { shortsOn || !it.isShort }
        if (left.size < MIN_SHELF_VIDEOS) null else ShownGroup(group.videos.first().id, group.copy(videos = left))
    }

/** One thing set into the Home list after [afterVideos] of its videos. */
sealed interface HomeBlock {
    val afterVideos: Int
    data class Shelf(val shelf: HomeShelf, override val afterVideos: Int) : HomeBlock
    data class Group(val shown: ShownGroup, override val afterVideos: Int) : HomeBlock
}

/**
 * Puts the app's shelves and YouTube's groups into one list, in drawing order. A group stays where YouTube had it: after the video it followed or,
 * when the filters took that one out, after the nearest earlier one still shown. A shelf whose place a group holds moves down to the next free one,
 * so two rows never stand with no video between them (Continue watching stays first whatever happens). [order] is the whole feed's ids, [shown]
 * the ids on screen.
 */
fun placeHomeBlocks(shelves: List<ShelfSlot>, groups: List<ShownGroup>, order: List<String>, shown: List<String>): List<HomeBlock> {
    val position = HashMap<String, Int>(shown.size).apply { shown.forEachIndexed { i, id -> put(id, i + 1) } }
    val placed = groups.map { g ->
        val anchor = g.group.afterVideoId
        val at = if (anchor == null) 0 else {
            val index = order.indexOf(anchor)
            if (index < 0) shown.size else (index downTo 0).firstNotNullOfOrNull { position[order[it]] } ?: 0
        }
        HomeBlock.Group(g, at)
    }
    val held = placed.mapTo(HashSet()) { it.afterVideos }
    val moved = shelves.map { slot ->
        var at = slot.afterVideos
        if (slot.shelf != HomeShelf.CONTINUE) while (at in held) at++
        held.add(at)
        HomeBlock.Shelf(slot.shelf, at)
    }
    val (first, rest) = moved.partition { it.shelf == HomeShelf.CONTINUE }
    // A stable sort: at the same place Continue watching comes first, then YouTube's groups in their order.
    return (first + placed + rest).sortedBy { it.afterVideos }
}

