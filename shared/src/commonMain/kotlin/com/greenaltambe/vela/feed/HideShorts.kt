package com.greenaltambe.vela.feed

import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.ShortsMode
import com.greenaltambe.vela.extractor.VideoSummary

/** Drops Shorts from this feed unless the (per-channel) shortsMode is SHOW; SEPARATE_TAB shows them elsewhere. */
object HideShorts : FeedStep {
    override fun apply(items: List<VideoSummary>, context: FeedContext): List<VideoSummary> =
        items.filter { item ->
            !item.isShort || context.resolve(Settings.shortsMode, item.channelId) == ShortsMode.SHOW
        }
}
