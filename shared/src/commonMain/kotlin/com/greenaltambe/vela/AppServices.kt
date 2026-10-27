package com.greenaltambe.vela

import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.data.FeedRepository
import com.greenaltambe.vela.data.HistoryLibrary
import com.greenaltambe.vela.data.PlaylistRepository
import com.greenaltambe.vela.data.SearchHistoryRepository
import com.greenaltambe.vela.data.SubscriptionRepository
import com.greenaltambe.vela.data.db.VelaDatabase
import com.greenaltambe.vela.extractor.Extractor
import com.greenaltambe.vela.extractor.chain.DiagnosticsLog
import com.greenaltambe.vela.feed.FeedService
import com.greenaltambe.vela.downloads.Downloads
import com.greenaltambe.vela.modules.Modules
import com.greenaltambe.vela.player.PlayerHost
import kotlin.time.Clock

/**
 * Everything the screens need, built once by the platform entry point. Screens take this rather than a long list of
 * parameters, so a new feature adds a field here instead of a parameter to every composable above it.
 */
class AppServices(
    val extractor: Extractor,
    val settings: SettingsResolver,
    val log: DiagnosticsLog,
    database: VelaDatabase,
    /** The platform's player, or null when it has none (the video page then lists what would play). */
    val player: PlayerHost?,
    val clock: Clock = Clock.System,
    /** The viewer's offset from UTC in minutes, for grouping history by local day. */
    val utcOffsetMinutes: () -> Int = { 0 },
    /** SponsorBlock, Return YouTube Dislike and DeArrow. */
    val modules: Modules,
    /** Downloads and the offline library; null where the platform has none yet. */
    val downloads: Downloads? = null,
) {
    val subscriptions = SubscriptionRepository(database)
    val history = HistoryLibrary(database)
    val searchHistory = SearchHistoryRepository(database)
    val playlists = PlaylistRepository(database)
    val feed = FeedService(extractor, subscriptions, FeedRepository(database), clock)
}
