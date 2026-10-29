package com.greenaltambe.vela.data

import com.greenaltambe.vela.data.db.VelaDatabase

/**
 * What the app can throw away and recompute. The rule is written here once, and tests pin it:
 *  - CACHES (rebuilt on demand): `ApiCache` (HTTP answers, DeArrow, RYD...), `SegmentCache` (SponsorBlock segments), `FeedItem`
 *    (the subscription feed, refetched from RSS), and the Setting rows `health.*` (canary results) and `release.*` (the daily release checks).
 *  - NEVER TOUCHED: Account, AccountAudit, Download, DownloadOfflineData, Playlist, PlaylistItem, WatchHistory, Subscription, SearchHistory,
 *    and every other Setting row. Downloaded files are not in the database at all.
 */
object CacheMaintenance {
    val cacheTables = listOf("ApiCache", "SegmentCache", "FeedItem")
    val cacheSettingPrefixes = listOf("health.", "release.")

    /** Empties the caches. Returns a short description of what was cleared. */
    fun rebuild(db: VelaDatabase): String {
        db.transaction {
            db.apiCacheQueries.clearEntries()
            db.apiCacheQueries.clearSegments()
            db.feedItemQueries.clearAllFeeds()
            cacheSettingPrefixes.forEach { SqlDelightSettingsStore(db).removePrefix(it) }
        }
        return "Cleared the HTTP/DeArrow/Return YouTube Dislike cache, SponsorBlock segments, the subscription feed and the health/release results. Accounts, downloads, playlists, history, subscriptions and settings were not touched."
    }
}
