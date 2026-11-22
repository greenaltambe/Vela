package com.greenaltambe.vela.backup

import com.greenaltambe.vela.data.*
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant

class AutoBackupsTest {
    private var now = 1_800_000_000_000L
    private val clock = object : Clock { override fun now() = Instant.fromEpochMilliseconds(now) }
    private val at = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private fun dir() = Files.createTempDirectory("bk").toFile().path

    private fun setup(external: String? = null): Triple<com.greenaltambe.vela.data.db.VelaDatabase, AutoBackups, BackupStorage> {
        val db = createDatabase(JvmDatabaseDriverFactory()); val store = SqlDelightSettingsStore(db)
        SubscriptionRepository(db).subscribe("UC1", "One", null, at)
        val internal = fileBackupStorage(dir(), "App folder")
        val folders = object : BackupFolders {
            override suspend fun choose() = external
            override fun label(descriptor: String) = "Chosen folder"
            override fun open(descriptor: String) = fileBackupStorage(descriptor, "Chosen folder")
        }
        return Triple(db, AutoBackups(db, store, internal, folders, null, clock), internal)
    }

    @Test fun stampIsReadableUtc() = assertEquals("20270115-080000", AutoBackups.stamp(1_800_000_000_000L))

    @Test fun createdBackupRestoresIntoAnEmptyDatabase() = runTest {
        val (_, ab, _) = setup()
        val names = ab.create(BackupKind.FIRST_SYNC)
        assertEquals(1, names.size); assertTrue(names[0].startsWith("vela-first-sync-"))
        val item = ab.list().single()
        assertEquals(BackupKind.FIRST_SYNC, item.kind)
        val fresh = createDatabase(JvmDatabaseDriverFactory())
        BackupService(fresh).restore(ab.read(item)!!)
        assertEquals(listOf("UC1"), SubscriptionRepository(fresh).all().map { it.channelId })
    }

    @Test fun dailyBackupRunsOncePerDayAndKeepsTheNewestSeven() = runTest {
        val (_, ab, _) = setup()
        assertTrue(ab.runIfDue()); assertFalse(ab.runIfDue(), "not again the same day")
        repeat(9) { now += 25L * 3600 * 1000; assertTrue(ab.runIfDue()) }
        assertEquals(7, ab.list().count { it.kind == BackupKind.AUTO })
        ab.enabled = false; now += 48L * 3600 * 1000
        assertFalse(ab.runIfDue())
    }

    @Test fun aSecondFolderGetsACopyAndItsFailureNeverFailsTheBackup() = runTest {
        val ext = dir()
        val (_, ab, _) = setup(ext)
        ab.setExternalFolder(ext)
        ab.create(BackupKind.AUTO)
        assertEquals(setOf("App folder", "Chosen folder"), ab.list().map { it.storage }.toSet())
        ab.setExternalFolder("/nonexistent/readonly/\u0000")
        assertEquals(1, ab.create(BackupKind.BEFORE_CHANGE).size)
    }

    @Test fun withoutAStorageCreatingFailsLoudly() = runTest {
        val db = createDatabase(JvmDatabaseDriverFactory())
        val ab = AutoBackups(db, SqlDelightSettingsStore(db), null, null, null, clock)
        assertFailsWith<BackupException> { ab.create(BackupKind.AUTO) }
        assertFalse(ab.runIfDue())
    }
}
