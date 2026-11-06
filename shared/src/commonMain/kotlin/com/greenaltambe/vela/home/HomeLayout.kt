package com.greenaltambe.vela.home

import com.greenaltambe.vela.core.settings.HomeShelf

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
