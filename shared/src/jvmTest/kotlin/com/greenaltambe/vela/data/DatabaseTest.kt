package com.greenaltambe.vela.data

import kotlin.test.Test
import com.greenaltambe.vela.core.settings.SettingLayer
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.core.settings.ShortsMode
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DatabaseTest {
    private val db = createDatabase(JvmDatabaseDriverFactory())

    @Test
    fun downloadsAreScopedPerAccountAndKeepOfflineData() {
        val q = db.downloadQueries
        q.insert("a1", "vid", "ch", "Title", DownloadState.QUEUED, DownloadKind.VIDEO, "1080p", 1, 1)
        val id = q.lastInsertId().executeAsOne()
        q.upsertOfflineData(id, "a1", "[]", null, null, "[{\"title\":\"x\",\"startSeconds\":0}]", null, null, null)
        q.updateState(DownloadState.PAUSED, null, 2, id)

        assertEquals(DownloadState.PAUSED, q.selectById(id).executeAsOne().state)
        assertEquals(1, q.selectForAccount("a1").executeAsList().size)
        assertEquals(0, q.selectForAccount("a2").executeAsList().size)
        assertEquals("[]", q.selectOfflineData(id).executeAsOne().sponsorBlockJson)
    }

    @Test
    fun historyAndSubscriptionsRoundTrip() {
        db.watchHistoryQueries.insert("a1", "v", "c", "T", 100, 5, 60, 5, 0)
        db.subscriptionQueries.upsert("a1", "c", "Chan", null, 1)
        assertEquals(5, db.watchHistoryQueries.totalWatchedMs("a1", 0, 200).executeAsOne())
        assertEquals(1, db.subscriptionQueries.selectForAccount("a1").executeAsList().size)
        assertEquals(0, db.subscriptionQueries.selectForAccount("a2").executeAsList().size)
    }

    @Test
    fun deletingADownloadCascadesToItsOfflineData() {
        val q = db.downloadQueries
        q.insert("a1", "vid", "ch", "Title", DownloadState.QUEUED, DownloadKind.VIDEO, "1080p", 1, 1)
        val id = q.lastInsertId().executeAsOne()
        q.upsertOfflineData(id, "a1", "[]", null, null, null, null, null, null)
        assertEquals(1, q.selectOfflineData(id).executeAsList().size)

        q.delete(id)
        assertEquals(0, q.selectOfflineData(id).executeAsList().size)
    }

    @Test
    fun orphanOfflineDataIsRejected() {
        assertFailsWith<Exception> { db.downloadQueries.upsertOfflineData(999, "a1", null, null, null, null, null, null, null) }
    }

    @Test
    fun selectLastForVideoReturnsNewestSessionForThatAccountAndVideo() {
        val h = db.watchHistoryQueries
        h.insert("a1", "v", "c", "T", 100, 1, 60, 1, 0)
        h.insert("a1", "v", "c", "T", 300, 30, 60, 30, 0)
        h.insert("a1", "v", "c", "T", 200, 10, 60, 10, 0)
        h.insert("a2", "v", "c", "T", 400, 50, 60, 50, 0)
        h.insert("a1", "other", "c", "T", 500, 5, 60, 5, 0)

        assertEquals(30, h.selectLastForVideo("a1", "v").executeAsOne().positionMs)
        assertEquals(null, h.selectLastForVideo("a1", "missing").executeAsOneOrNull())
    }

    @Test
    fun sqlDelightSettingsStoreRoundTripsEveryLayer() {
        val store = SqlDelightSettingsStore(db)
        val key = Settings.defaultPlaybackSpeed.key
        store.put(key, SettingLayer.Global, "1.25")
        store.put(key, SettingLayer.channel("c"), "1.5")
        store.put(key, SettingLayer.video("c"), "2.0") // same id in another scope must not collide
        assertEquals("1.25", store.get(key, SettingLayer.Global))
        assertEquals("1.5", store.get(key, SettingLayer.channel("c")))
        assertEquals("2.0", store.get(key, SettingLayer.video("c")))

        store.put(key, SettingLayer.Global, "3.0")
        assertEquals("3.0", store.get(key, SettingLayer.Global))
        store.remove(key, SettingLayer.channel("c"))
        assertEquals(null, store.get(key, SettingLayer.channel("c")))
        assertEquals(null, store.get("nope", SettingLayer.Global))
    }

    @Test
    fun resolverWorksOnTopOfTheSqlStore() {
        val resolver = SettingsResolver(SqlDelightSettingsStore(db))
        resolver.set(Settings.shortsMode, ShortsMode.HIDE)
        resolver.set(Settings.shortsMode, ShortsMode.SEPARATE_TAB, SettingLayer.channel("c"))
        assertEquals(ShortsMode.SEPARATE_TAB, resolver.resolve(Settings.shortsMode, channelId = "c"))
        assertEquals(ShortsMode.HIDE, resolver.resolve(Settings.shortsMode, channelId = "other"))
    }
}
