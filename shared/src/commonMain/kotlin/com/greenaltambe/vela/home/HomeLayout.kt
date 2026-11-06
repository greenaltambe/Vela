package com.greenaltambe.vela.home

import com.greenaltambe.vela.core.settings.HomeShelf

/** Where one shelf sits in the Home list: after [afterVideos] videos of the main list (0: above the first). */
data class ShelfSlot(val shelf: HomeShelf, val afterVideos: Int)

/**
 * Weaves the shelves into the list the way YouTube does, instead of stacking them above it. Continue watching goes first (it is something to
 * do, not something to be recommended); the others follow the viewer's order: the first after [FIRST_AFTER] videos, then one every [step] videos.
 * While the list has fewer videos than that (or none: it is still loading, or its source failed) a shelf waits at the end of what there is, so
 * Home is never blank and the shelves take their places as the list grows.
 */
fun planSlots(shelves: List<HomeShelf>, videoCount: Int, step: Int): List<ShelfSlot> {
    val gap = step.coerceAtLeast(2)
    val out = ArrayList<ShelfSlot>()
    var n = 0
    shelves.distinct().forEach { shelf ->
        if (shelf == HomeShelf.CONTINUE) out += ShelfSlot(shelf, 0)
        else { out += ShelfSlot(shelf, minOf(FIRST_AFTER + n * gap, videoCount)); n++ }
    }
    return out.sortedBy { it.afterVideos }
}

const val FIRST_AFTER = 3

/** A shelf needs this many videos (after the filters and the dismissed ones) to be drawn: one or two look like a mistake. Continue watching is exempt. */
const val MIN_SHELF_VIDEOS = 3
