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

    @Test
    fun olderDatabaseIsMigratedForward() {
        // Build a version-1 database by hand: the original tables, no Setting table, no per-video index.
        val v1 = JvmDatabaseDriverFactory(file).create()
        v1.execute(null, "DROP TABLE Setting", 0)
        v1.execute(null, "DROP INDEX WatchHistory_account_video_time", 0)
        v1.execute(null, "PRAGMA user_version = 1", 0)
        v1.execute(null, "INSERT INTO Subscription VALUES ('a1','c','Chan',NULL,1)", 0)
        v1.close()

        val db = createDatabase(JvmDatabaseDriverFactory(file))
        assertEquals(1, db.subscriptionQueries.selectForAccount("a1").executeAsList().size, "existing rows are kept")
        SqlDelightSettingsStore(db).put("k", com.greenaltambe.vela.core.settings.SettingLayer.Global, "v")
        assertEquals(VelaDatabase.Schema.version, JvmDatabaseDriverFactory(file).create().scalar("PRAGMA user_version"))
    }

    @Test
    fun failedMigrationRollsBackCompletely() {
        // Version 1 plus a clashing index: the migration creates Setting, then fails on the index.
        val v1 = JvmDatabaseDriverFactory(file).create()
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
