package com.greenaltambe.vela

import com.greenaltambe.vela.auth.AccountLogin
import com.greenaltambe.vela.auth.AccountManager
import com.greenaltambe.vela.auth.CredentialStore
import com.greenaltambe.vela.auth.InMemorySecretStore
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
    /** The accounts and which is active; defaults to a local-only manager whose credentials live in memory. */
    val accounts: AccountManager = AccountManager(database, CredentialStore(InMemorySecretStore())),
    /** Validates and adds Google accounts; null where the platform has no login yet. */
    val login: AccountLogin? = null,
    /** Starts a web sign-in on platforms that have one (Android's WebView); null elsewhere. */
    val webLogin: (() -> Unit)? = null,
    /** Pulls account data (subscriptions) into the local tables; null where there is no login. */
    val sync: com.greenaltambe.vela.auth.AccountSync? = null,
) {
    private val db = database
    private val feeds = HashMap<String, FeedService>()

    /** The repositories below follow the active account: each read builds one for the id that is active right now. */
    val activeId: String get() = accounts.active.value.id
    val subscriptions get() = SubscriptionRepository(db, activeId)
    val history get() = HistoryLibrary(db, activeId)
    val searchHistory get() = SearchHistoryRepository(db, activeId)
    val playlists get() = PlaylistRepository(db, activeId)

    /** One feed service per account, so each keeps its own entries and refresh state. */
    val feed: FeedService
        get() = activeId.let { id -> feeds.getOrPut(id) { FeedService(extractor, SubscriptionRepository(db, id), FeedRepository(db, id), clock) } }
}
