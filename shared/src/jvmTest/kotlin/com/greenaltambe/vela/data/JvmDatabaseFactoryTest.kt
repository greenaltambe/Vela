package com.greenaltambe.vela.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.greenaltambe.vela.data.db.VelaDatabase
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JvmDatabaseFactoryTest {
    private val dir = Files.createTempDirectory("vela-db-test").toFile()
    private val file = File(dir, "vela.db").path

    @AfterTest
    fun cleanUp() { dir.deleteRecursively() }

    private fun SqlDriver.scalar(sql: String): Long = executeQuery(null, sql, { c ->
        QueryResult.Value(if (c.next().value) c.getLong(0) ?: 0L else 0L)
    }, 0).value

    @Test
    fun fileDatabaseSurvivesBeingOpenedTwice() {
        val first = createDatabase(JvmDatabaseDriverFactory(file))
        first.subscriptionQueries.upsert("a1", "c", "Chan", null, 1)
        val second = createDatabase(JvmDatabaseDriverFactory(file))

        assertEquals(1, second.subscriptionQueries.selectForAccount("a1").executeAsList().size)
    }

    @Test
    fun freshDatabaseIsStampedWithTheSchemaVersion() {
        val driver = JvmDatabaseDriverFactory(file).create()
        assertEquals(VelaDatabase.Schema.version, driver.scalar("PRAGMA user_version"))
    }

    @Test
    fun foreignKeysAreOn() {
        assertEquals(1, JvmDatabaseDriverFactory().create().scalar("PRAGMA foreign_keys"))
        assertEquals(1, JvmDatabaseDriverFactory(file).create().scalar("PRAGMA foreign_keys"))
    }

    /** Takes a current database back to what version 4 looked like: no resume plan or offline page columns. */
    private fun SqlDriver.dropVersion5Columns() {
        execute(null, "ALTER TABLE Download DROP COLUMN planJson", 0)
        listOf("descriptionText", "infoJson", "thumbnailPath").forEach { execute(null, "ALTER TABLE DownloadOfflineData DROP COLUMN $it", 0) }
    }

    /** ...back to version 3: also no API cache or segment store. */
    private fun SqlDriver.dropVersion4Tables() {
        dropVersion5Columns()
        listOf("ApiCache", "SegmentCache").forEach { execute(null, "DROP TABLE $it", 0) }
    }

    /** ...and back to version 2: also no search history, playlists or feed cache. */
    private fun SqlDriver.dropVersion3Tables() {
        dropVersion4Tables()
        listOf("SearchHistory", "PlaylistItem", "Playlist", "FeedItem").forEach { execute(null, "DROP TABLE $it", 0) }
    }

    @Test
    fun olderDatabaseIsMigratedForward() {
        // Build a version-1 database by hand: the original tables, no Setting table, no per-video index.
        val v1 = JvmDatabaseDriverFactory(file).create()
        v1.dropVersion3Tables()
        v1.execute(null, "DROP TABLE Setting", 0)
        v1.execute(null, "DROP INDEX WatchHistory_account_video_time", 0)
        v1.execute(null, "PRAGMA user_version = 1", 0)
        v1.execute(null, "INSERT INTO Subscription VALUES ('a1','c','Chan',NULL,1)", 0)
        v1.close()

        val db = createDatabase(JvmDatabaseDriverFactory(file))
        assertEquals(1, db.subscriptionQueries.selectForAccount("a1").executeAsList().size, "existing rows are kept")
        SqlDelightSettingsStore(db).put("k", com.greenaltambe.vela.core.settings.SettingLayer.Global, "v")
        assertEquals(VelaDatabase.Schema.version, JvmDatabaseDriverFactory(file).create().scalar("PRAGMA user_version"))
        // The version-3 tables arrived in the same pass and work.
        db.playlistQueries.insert("local", "P", null, 1, 1)
        db.searchHistoryQueries.upsert("local", "q", 1)
    }

    @Test
    fun versionTwoDatabaseGainsLibraryTablesAndKeepsItsData() {
        val v2 = JvmDatabaseDriverFactory(file).create()
        v2.dropVersion3Tables()
        v2.execute(null, "PRAGMA user_version = 2", 0)
        v2.execute(null, "INSERT INTO Subscription VALUES ('a1','c','Chan',NULL,1)", 0)
        v2.close()

        val db = createDatabase(JvmDatabaseDriverFactory(file))
        assertEquals(1, db.subscriptionQueries.selectForAccount("a1").executeAsList().size)
        val playlists = PlaylistRepository(db, "a1")
        val id = playlists.create("Mine", kotlin.time.Instant.fromEpochSeconds(5))
        playlists.add(id, listOf(com.greenaltambe.vela.extractor.VideoSummary("v", "t", "c", "n")), kotlin.time.Instant.fromEpochSeconds(6))
        assertEquals(1, playlists.items(id).size)
        assertEquals(VelaDatabase.Schema.version, JvmDatabaseDriverFactory(file).create().scalar("PRAGMA user_version"))
    }

    @Test
    fun versionThreeDatabaseGainsTheModuleTablesAndKeepsItsData() {
        val v3 = JvmDatabaseDriverFactory(file).create()
        v3.dropVersion4Tables()
        v3.execute(null, "PRAGMA user_version = 3", 0)
        v3.execute(null, "INSERT INTO Subscription VALUES ('a1','c','Chan',NULL,1)", 0)
        v3.close()

        val db = createDatabase(JvmDatabaseDriverFactory(file))
        assertEquals(1, db.subscriptionQueries.selectForAccount("a1").executeAsList().size)
        db.apiCacheQueries.upsertEntry("k", 200, "body", 5)
        db.apiCacheQueries.upsertSegments("vid", "[]", 5)
        assertEquals("body", db.apiCacheQueries.selectEntry("k").executeAsOne().body)
        assertEquals(VelaDatabase.Schema.version, JvmDatabaseDriverFactory(file).create().scalar("PRAGMA user_version"))
    }

    @Test
    fun versionFourDatabaseGainsTheDownloadColumnsAndKeepsItsRows() {
        val v4 = JvmDatabaseDriverFactory(file).create()
        v4.dropVersion5Columns()
        v4.execute(null, "PRAGMA user_version = 4", 0)
        v4.execute(null, "INSERT INTO Download(accountId, videoId, channelId, title, state, kind, qualityLabel, createdAt, updatedAt) VALUES ('a1','v','c','T','COMPLETED','VIDEO','720p',1,1)", 0)
        v4.execute(null, "INSERT INTO DownloadOfflineData(downloadId, accountId, sponsorBlockJson) VALUES (1,'a1','[]')", 0)
        v4.close()

        val db = createDatabase(JvmDatabaseDriverFactory(file))
        val row = db.downloadQueries.selectById(1).executeAsOne()
        assertEquals("T", row.title)
        assertEquals(null, row.planJson)
        db.downloadQueries.updatePlan("{}", "720p", 5, 2, 1)
        assertEquals("{}", db.downloadQueries.selectById(1).executeAsOne().planJson)
        db.downloadQueries.upsertOfflineData(1, "a1", "[]", null, null, null, "desc", "{}", "/t.jpg")
        assertEquals("/t.jpg", db.downloadQueries.selectOfflineData(1).executeAsOne().thumbnailPath)
        assertEquals(5L, JvmDatabaseDriverFactory(file).create().scalar("PRAGMA user_version"))
    }

    @Test
    fun failedMigrationRollsBackCompletely() {
        // Version 1 plus a clashing index: the migration creates Setting, then fails on the index.
        val v1 = JvmDatabaseDriverFactory(file).create()
        v1.dropVersion3Tables()
        v1.execute(null, "DROP TABLE Setting", 0)
        v1.execute(null, "PRAGMA user_version = 1", 0)
        v1.close()

        assertFailsWith<Exception> { JvmDatabaseDriverFactory(file).create() }

        val after = java.sql.DriverManager.getConnection("jdbc:sqlite:$file").use { c ->
            fun scalar(sql: String) = c.createStatement().use { st -> st.executeQuery(sql).use { it.next(); it.getLong(1) } }
            scalar("SELECT COUNT(*) FROM sqlite_master WHERE name = 'Setting'") to scalar("PRAGMA user_version")
        }
        assertEquals(0L to 1L, after, "Setting table must not survive and the version must stay 1")
    }

    @Test
    fun databaseFromTheFutureIsRefused() {
        val driver = JvmDatabaseDriverFactory(file).create()
        driver.execute(null, "PRAGMA user_version = ${VelaDatabase.Schema.version + 1}", 0)
        driver.close()
        assertFailsWith<IllegalStateException> { JvmDatabaseDriverFactory(file).create() }
    }
}
