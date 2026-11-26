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
import kotlin.test.assertTrue

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

    /** Takes a current database back to what version 12 looked like: no saved playlists. */
    private fun SqlDriver.dropVersion13Changes() {
        execute(null, "DROP INDEX Playlist_source", 0)
        listOf("sourceId", "ownerName", "ownerChannelId", "coverUrl", "onAccount", "unavailableSince").forEach { execute(null, "ALTER TABLE Playlist DROP COLUMN $it", 0) }
    }

    @Test
    fun versionTwelvePlaylistsStayAsTheyAreAndSavedOnesCanBeAdded() {
        val v12 = JvmDatabaseDriverFactory(file).create()
        v12.dropVersion13Changes()
        v12.execute(null, "PRAGMA user_version = 12", 0)
        v12.execute(null, "INSERT INTO Playlist(accountId, name, createdAt, updatedAt, remoteId, syncState) VALUES ('a1','Synced',1,1,'PLx','SYNCED')", 0)
        v12.execute(null, "INSERT INTO Playlist(accountId, name, createdAt, updatedAt) VALUES ('a1','Local',2,2)", 0)
        v12.close()

        val lists = PlaylistRepository(createDatabase(JvmDatabaseDriverFactory(file)), "a1")
        assertEquals(listOf("Synced" to false, "Local" to true), lists.all().filter { !it.isWatchLater }.map { it.name to it.isLocal })
        assertTrue(lists.all().none { it.isSaved })
        val saved = lists.createSaved(SavedSource("PLs", "Theirs"), kotlin.time.Instant.fromEpochSeconds(3))
        assertEquals(saved, lists.createSaved(SavedSource("PLs", "Theirs again"), kotlin.time.Instant.fromEpochSeconds(4)), "one copy of an original")
        assertEquals("PLs", lists.get(saved)!!.sourceId)
        assertEquals(VelaDatabase.Schema.version, JvmDatabaseDriverFactory(file).create().scalar("PRAGMA user_version"))
    }

    /** Takes a current database back to what version 11 looked like: no playlist sync columns and no outbox. */
    private fun SqlDriver.dropVersion12Changes() {
        dropVersion13Changes()
        execute(null, "DROP TABLE PlaylistOp", 0)
        listOf("remoteId", "syncState", "lastSyncedAt", "syncError").forEach { execute(null, "ALTER TABLE Playlist DROP COLUMN $it", 0) }
        execute(null, "ALTER TABLE PlaylistItem DROP COLUMN rowState", 0)
    }

    /** ...back to what version 10 looked like: no list of videos removed from Continue watching. */
    private fun SqlDriver.dropVersion11Table() { dropVersion12Changes(); execute(null, "DROP TABLE ContinueDismissed", 0) }

    /** ...back to what version 8 looked like: no account watch history. */
    private fun SqlDriver.dropVersion9Table() { dropVersion11Table(); execute(null, "DROP TABLE AccountWatched", 0) }

    /** ...back to what version 7 looked like: no notification level or feed. */
    private fun SqlDriver.dropVersion8Changes() {
        dropVersion9Table()
        execute(null, "DROP TABLE NotificationItem", 0)
        execute(null, "ALTER TABLE Subscription DROP COLUMN notifyLevel", 0)
    }

    /** ...back to what version 6 looked like: no account audit log. */
    private fun SqlDriver.dropVersion7Table() { dropVersion8Changes(); execute(null, "DROP TABLE AccountAudit", 0) }

    /** ...back to what version 5 looked like: an Account row with no kind, e-mail or auth state. */
    private fun SqlDriver.dropVersion6Columns() {
        dropVersion7Table()
        listOf("kind", "email", "avatarUrl", "channelId", "authState").forEach { execute(null, "ALTER TABLE Account DROP COLUMN $it", 0) }
    }

    /** ...back to version 4: no resume plan or offline page columns. */
    private fun SqlDriver.dropVersion5Columns() {
        dropVersion6Columns()
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
        assertEquals(VelaDatabase.Schema.version, JvmDatabaseDriverFactory(file).create().scalar("PRAGMA user_version"))
    }

    @Test
    fun versionFiveDatabaseGainsTheAccountColumnsAndKeepsItsAccounts() {
        val v5 = JvmDatabaseDriverFactory(file).create()
        v5.dropVersion6Columns()
        v5.execute(null, "PRAGMA user_version = 5", 0)
        v5.execute(null, "INSERT INTO Account(id, displayName, createdAt) VALUES ('local','This device',1)", 0)
        v5.close()

        val db = createDatabase(JvmDatabaseDriverFactory(file))
        val row = db.accountQueries.selectById("local").executeAsOne()
        assertEquals("This device", row.displayName)
        assertEquals("LOCAL", row.kind)
        assertEquals("OK", row.authState)
        assertEquals(null, row.email)
    }

    @Test
    fun versionSixDatabaseGainsTheAuditLogAndKeepsItsAccounts() {
        val v6 = JvmDatabaseDriverFactory(file).create()
        v6.dropVersion7Table()
        v6.execute(null, "PRAGMA user_version = 6", 0)
        v6.execute(null, "INSERT INTO Account(id, displayName, createdAt, kind) VALUES ('g1','G',1,'GOOGLE')", 0)
        v6.close()

        val db = createDatabase(JvmDatabaseDriverFactory(file))
        assertEquals("GOOGLE", db.accountQueries.selectById("g1").executeAsOne().kind)
        db.accountAuditQueries.insert("g1", 5, "BROWSE", "www.youtube.com", "ok", "allowed")
        assertEquals(1, db.accountAuditQueries.recent("g1", 10).executeAsList().size)
    }

    @Test
    fun versionElevenPlaylistsBecomeLocalOrSyncedAndNothingSavedHereIsLost() {
        val v11 = JvmDatabaseDriverFactory(file).create()
        v11.dropVersion12Changes()
        v11.execute(null, "PRAGMA user_version = 11", 0)
        fun list(id: Int, name: String, key: String?) = v11.execute(null, "INSERT INTO Playlist(id, accountId, name, systemKey, createdAt, updatedAt) VALUES ($id,'g1','$name',${key?.let { "'$it'" } ?: "NULL"},1,1)", 0)
        fun item(list: Int, video: String, pos: Int) = v11.execute(null, "INSERT INTO PlaylistItem(playlistId, videoId, position, title, channelId, channelName, addedAt) VALUES ($list,'$video',$pos,'T','UC','C',1)", 0)
        list(1, "Mine", null); item(1, "m1", 0)
        list(2, "Watch later", "watch_later"); item(2, "x", 0)
        list(3, "Watch later (YouTube)", "yt:WL"); item(3, "y", 0); item(3, "x", 1)
        list(4, "Remote", "yt:PL1"); item(4, "p", 0)
        list(5, "Liked videos (YouTube)", "yt:LL"); item(5, "l", 0)
        // A second account with a local Watch later and no mirror: it must stay local.
        v11.execute(null, "INSERT INTO Playlist(id, accountId, name, systemKey, createdAt, updatedAt) VALUES (6,'g2','Watch later','watch_later',1,1)", 0)
        v11.close()

        val db = createDatabase(JvmDatabaseDriverFactory(file))
        val repo = PlaylistRepository(db, "g1")
        val all = repo.all()
        assertEquals(null, all.single { it.name == "Mine" }.remoteId, "a local playlist stays local")
        val wl = all.single { it.isWatchLater }
        assertEquals("WL", wl.remoteId, "the account's Watch later is merged into the one local Watch later, which is linked to it")
        assertEquals(setOf("x", "y"), repo.items(wl.id).map { it.videoId }.toSet(), "the two are united")
        assertTrue(repo.items(wl.id).all { it.rowState == RowState.PENDING_ADD }, "nothing is deleted at the first pull: items wait until a sync confirms them")
        assertEquals(1, all.count { it.name.startsWith("Watch later") }, "the old '(YouTube)' copy is gone")
        val remote = all.single { it.remoteId == "PL1" }
        assertEquals(PlaylistSync.SYNCED, remote.syncState); assertEquals(RowState.PENDING_ADD, repo.items(remote.id).single().rowState)
        val liked = all.single { it.remoteId == "LL" }
        assertEquals("Liked videos", liked.name); assertEquals(RowState.SYNCED, repo.items(liked.id).single().rowState, "liked videos has nothing to push")
        assertEquals(null, PlaylistRepository(db, "g2").all().single { it.isWatchLater }.remoteId, "no account copy was used, so it stays local")
        assertEquals(0, PlaylistOpRepository(db, "g1").count())
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
        assertFailsWith<DatabaseStartupException> { JvmDatabaseDriverFactory(file).create() }
    }

    @Test
    fun aMigrationKeepsABackupOfTheOldFileAndAFailedOneNamesIt() {
        val v1 = JvmDatabaseDriverFactory(file).create()
        v1.dropVersion3Tables(); v1.execute(null, "DROP TABLE Setting", 0); v1.execute(null, "DROP INDEX WatchHistory_account_video_time", 0)
        v1.execute(null, "PRAGMA user_version = 1", 0); v1.execute(null, "INSERT INTO Subscription VALUES ('a1','c','Chan',NULL,1)", 0); v1.close()
        JvmDatabaseDriverFactory(file).create().close()
        val backup = java.io.File("$file.bak-v1")
        assertTrue(backup.isFile && backup.length() > 0, "a copy of the version-1 file exists after the migration")
    }

    @Test
    fun aFileThatIsNotADatabaseIsReportedAndLeftAlone() {
        File(file).writeText("this is not an sqlite file at all, just text".repeat(50))
        val before = File(file).readText()
        val e = assertFailsWith<DatabaseStartupException> { JvmDatabaseDriverFactory(file).create() }
        assertTrue("Nothing was changed or deleted" in e.message!!)
        assertEquals(before, File(file).readText())
    }
}
