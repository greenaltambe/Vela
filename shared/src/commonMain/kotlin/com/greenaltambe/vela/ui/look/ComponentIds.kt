package com.greenaltambe.vela.ui.look

/**
 * Stable string ids of the reusable components. Each component reads its configuration from the active Look under
 * its id, and later "customise by tapping" and reorderable Home sections address components by these strings, so
 * an id never changes once shipped.
 */
object ComponentIds {
    const val VIDEO_CARD = "component.video_card"
    const val CHANNEL_ROW = "component.channel_row"
    const val SECTION_HEADER = "component.section_header"
    const val MINI_PLAYER = "component.mini_player"
    const val BOTTOM_NAV = "component.bottom_nav"
    const val SIDE_RAIL = "component.side_rail"
    const val COMMENT_ITEM = "component.comment_item"
    const val CHAPTER_ROW = "component.chapter_row"
    const val PLAYLIST_ROW = "component.playlist_row"
    const val HISTORY_ROW = "component.history_row"
    const val SEEK_BAR = "component.seek_bar"

    val all = listOf(
        VIDEO_CARD, CHANNEL_ROW, SECTION_HEADER, MINI_PLAYER, BOTTOM_NAV, SIDE_RAIL, COMMENT_ITEM, CHAPTER_ROW,
        PLAYLIST_ROW, HISTORY_ROW, SEEK_BAR,
    )
}
