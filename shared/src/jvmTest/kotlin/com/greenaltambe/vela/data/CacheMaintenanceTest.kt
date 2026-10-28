package com.greenaltambe.vela.data

import com.greenaltambe.vela.core.settings.SettingLayer
import com.greenaltambe.vela.extractor.VideoSummary
import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.*
import kotlin.time.Clock

class CacheMaintenanceTest {
    private val dir = Files.createTempDirectory("vela-cache").toFile()
    private val path = File(dir, "vela.db").path
    @AfterTest fun clean() { dir.deleteRecursively() }

    private fun fill(db: com.greenaltambe.vela.data.db.VelaDatabase) {
        val now = Clock.System.now()
        SubscriptionRepository(db, "a1").subscribe("UC1", "Chan", null, now)
        PlaylistRepository(db, "a1").let { p -> val id = p.create("Mine", now); p.add(id, listOf(VideoSummary("v1", "T", "UC1", "Chan")), now) }
        SqlDelightSettingsStore(db).apply { put("playback.speed", SettingLayer.Global, "1.5"); put("health.results", SettingLayer.Global, "x"); put("release.NewPipe", SettingLayer.Global, "y") }
        db.apiCacheQueries.upsertEntry("k", 200, "{}", 1L)
        db.apiCacheQueries.upsertSegments("v1", "[]", 1L)
    }

    @Test fun rebuildClearsOnlyCachesAndNeverAccountsDownloadsPlaylistsHistoryOrSettings() {
        val db = createDatabase(JvmDatabaseDriverFactory(path)); fill(db)
        CacheMaintenance.rebuild(db)
        assertNull(db.apiCacheQueries.selectEntry("k").executeAsOneOrNull()); assertNull(db.apiCacheQueries.selectSegments("v1").executeAsOneOrNull())
        val s = SqlDelightSettingsStore(db)
        assertNull(s.get("health.results", SettingLayer.Global)); assertNull(s.get("release.NewPipe", SettingLayer.Global))
        assertEquals("1.5", s.get("playback.speed", SettingLayer.Global), "ordinary settings survive")
        assertTrue(SubscriptionRepository(db, "a1").isSubscribed("UC1")); assertEquals(1, PlaylistRepository(db, "a1").all().count { it.name == "Mine" })
    }

    @Test fun salvageKeepsEverythingButTheCachesWhenOnlyCachePagesAreDamaged() {
        run { val db = createDatabase(JvmDatabaseDriverFactory(path)); fill(db)
            // Enough cache data that it fills the pages at the END of the file; the tables created first sit at the front.
            repeat(300) { db.apiCacheQueries.upsertEntry("big$it", 200, "x".repeat(20_000), 1L) } }
        val f = File(path); val bytes = f.readBytes()
        for (i in (bytes.size * 0.45).toInt()..(bytes.size * 0.9).toInt()) bytes[i] = 0x7F   // damage the cache pages
        f.writeBytes(bytes)
        val e = assertFailsWith<DatabaseStartupException> { JvmDatabaseDriverFactory(path).create() }
        assertTrue("damaged" in e.message!! || "could not be read" in e.message!!, e.message)
        val r = DatabaseRecovery.salvage(path)
        assertTrue(r.movedTo.isFile, "the damaged file is kept")
        val db = createDatabase(JvmDatabaseDriverFactory(path))
        assertTrue(SubscriptionRepository(db, "a1").isSubscribed("UC1"), r.describe())
        assertEquals("1.5", SqlDelightSettingsStore(db).get("playback.speed", SettingLayer.Global))
        assertEquals(1, PlaylistRepository(db, "a1").all().count { it.name == "Mine" })
        assertNull(SqlDelightSettingsStore(db).get("health.results", SettingLayer.Global), "cache rows are not carried over")
    }
}
