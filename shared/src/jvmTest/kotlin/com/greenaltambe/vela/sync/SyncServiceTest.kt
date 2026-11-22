package com.greenaltambe.vela.sync

import com.greenaltambe.vela.auth.CredentialStore
import com.greenaltambe.vela.auth.InMemorySecretStore
import com.greenaltambe.vela.core.settings.SettingLayer
import com.greenaltambe.vela.data.*
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.feed.Blocklist
import com.greenaltambe.vela.feed.SubscriptionGroups
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant

class SyncServiceTest {
    private var t = 1_800_000_000_000L
    private val clock = object : Clock { override fun now(): Instant { t += 1000; return Instant.fromEpochMilliseconds(t) } }
    private val at = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private fun v(id: String) = VideoSummary(id, "T $id", "UC$id", "Chan $id", null, 61)
    private val folder: File = Files.createTempDirectory("vsync").toFile()

    private class Device(val name: String, folder: File, val clock: Clock, code: String) {
        val db = createDatabase(JvmDatabaseDriverFactory())
        val store = SqlDelightSettingsStore(db)
        val blocklist = Blocklist(store)
        val service = SyncService(db, store, CredentialStore(InMemorySecretStore()), null, null, null, blocklist, null, name, clock, backendOverride = { FolderSyncBackend(File(it)) }).also {
            it.setFolder(folder.path); it.setCode(code)
        }
    }

    @Test fun twoRealDevicesConvergeThroughAnEncryptedFolder() = runTest {
        val a = Device("Phone", folder, clock, "AAAA-BBBB-CCCC-DDDD"); val b = Device("PC", folder, clock, "aaaa bbbb cccc dddd")
        SubscriptionRepository(a.db).subscribe("UC1", "One", null, at)
        PlaylistRepository(a.db).apply { add(create("Road trip", at), listOf(v("a"), v("b")), at); add(watchLaterId(), listOf(v("w")), at) }
        SubscriptionGroups(a.store, LOCAL_ACCOUNT).create("Music", setOf("UC1"))
        a.blocklist.addKeyword("spoiler")
        SqlDelightWatchHistoryRepository(a.db).also { val id = it.begin(LOCAL_ACCOUNT, "a", "UCa", "T a", at, 42_000, 100_000, 40_000); it.update(id, at, 42_000, 100_000, 40_000) }
        a.store.put("playback.speed", SettingLayer.Global, "1.5")
        a.store.put("downloads.folder", SettingLayer.Global, "/phone/only")
        SubscriptionRepository(b.db).subscribe("UC2", "Two", null, at)

        a.service.syncNow(); b.service.syncNow(); a.service.syncNow()

        assertEquals(setOf("UC1", "UC2"), SubscriptionRepository(b.db).all().map { it.channelId }.toSet())
        assertEquals(setOf("UC1", "UC2"), SubscriptionRepository(a.db).all().map { it.channelId }.toSet())
        val lists = PlaylistRepository(b.db)
        assertEquals(listOf("a", "b"), lists.items(lists.all().single { it.name == "Road trip" }.id).map { it.videoId })
        assertEquals(listOf("w"), lists.items(lists.watchLaterId()).map { it.videoId })
        assertEquals(setOf("UC1"), SubscriptionGroups(b.store, LOCAL_ACCOUNT).groups().single().channelIds)
        assertEquals(listOf("spoiler"), b.blocklist.keywords())
        assertEquals(42_000, HistoryLibrary(b.db).recent().single().positionMs)
        assertEquals("1.5", b.store.get("playback.speed", SettingLayer.Global))
        assertNull(b.store.get("downloads.folder", SettingLayer.Global), "device-specific settings stay home")
        assertTrue(folder.listFiles()!!.none { "UC1" in it.readText(Charsets.ISO_8859_1) }, "the folder holds only ciphertext")

        // a deletion on one device reaches the other, and a stable state stays stable
        SubscriptionRepository(b.db).unsubscribe("UC1"); b.blocklist.removeKeyword("spoiler")
        b.service.syncNow(); a.service.syncNow()
        assertEquals(setOf("UC2"), SubscriptionRepository(a.db).all().map { it.channelId }.toSet())
        assertTrue(a.blocklist.keywords().isEmpty())
        assertTrue(a.service.syncNow() is SyncStatus.Idle)
    }

    private class AccDevice(val name: String, folder: File, clock: Clock, code: String) {
        val db = createDatabase(JvmDatabaseDriverFactory())
        val store = SqlDelightSettingsStore(db)
        val blocklist = Blocklist(store)
        val credentials = CredentialStore(InMemorySecretStore())
        val accounts = com.greenaltambe.vela.auth.AccountManager(db, credentials)
        val service = SyncService(db, store, credentials, null, null, null, blocklist, accounts, name, clock, backendOverride = { FolderSyncBackend(File(it)) }).also {
            it.setFolder(folder.path); it.setCode(code); it.setKind(SyncKind.ACCOUNTS, true)
        }
    }

    @Test fun aSignedInAccountMovesToTheOtherDeviceWithItsOwnData() = runTest {
        val pc = AccDevice("PC", folder, clock, "AAAA-BBBB-CCCC-DDDD"); val phone = AccDevice("Phone", folder, clock, "AAAA-BBBB-CCCC-DDDD")
        val acct = pc.accounts.addGoogle(com.greenaltambe.vela.auth.GoogleIdentity("Test Person", "t@gmail.com", null, "UCtest"), com.greenaltambe.vela.auth.CookieJar(listOf(com.greenaltambe.vela.auth.Cookie("SAPISID", "secret-value", ".youtube.com"))))
        SubscriptionRepository(pc.db, acct.id).subscribe("UC1", "One", null, at)
        SubscriptionRepository(pc.db).subscribe("UCLOCAL", "Local one", null, at)     // the local profile stays separate
        pc.service.syncNow(); phone.service.syncNow()
        val got = phone.accounts.get(acct.id)
        assertNotNull(got, "the phone now has the same account id")
        assertTrue(got.isSignedIn)
        assertNotNull(phone.accounts.cookies(acct.id), "the phone received the sign-in")
        assertEquals(acct.id, phone.accounts.active.value.id, "the first Google account on a device becomes the active one")
        assertEquals(listOf("UC1"), SubscriptionRepository(phone.db, acct.id).all().map { it.channelId }, "the account's data followed the account")
        assertEquals(listOf("UCLOCAL"), SubscriptionRepository(phone.db).all().map { it.channelId })
        assertTrue(!folder.listFiles()!!.any { "secret-value" in it.readText(Charsets.ISO_8859_1) }, "the sign-in is only in the encrypted file")
        // nothing is deleted by the account appearing, and it settles
        pc.service.syncNow(); phone.service.syncNow(); pc.service.syncNow()
        assertEquals(listOf("UC1"), SubscriptionRepository(pc.db, acct.id).all().map { it.channelId })
    }

    @Test fun signInIsNotSharedUnlessSwitchedOn() = runTest {
        val pc = AccDevice("PC", folder, clock, "AAAA-BBBB-CCCC-DDDD"); pc.service.setKind(SyncKind.ACCOUNTS, false)
        val phone = AccDevice("Phone", folder, clock, "AAAA-BBBB-CCCC-DDDD")
        val acct = pc.accounts.addGoogle(com.greenaltambe.vela.auth.GoogleIdentity("Test Person", "t@gmail.com", null, "UCtest"), com.greenaltambe.vela.auth.CookieJar(listOf(com.greenaltambe.vela.auth.Cookie("SAPISID", "v", ".youtube.com"))))
        pc.service.syncNow(); phone.service.syncNow()
        assertNull(phone.accounts.get(acct.id))
    }

    @Test fun aLocalChangeIsNoticedAndPublishedWithoutAManualSync() = runTest {
        val a = Device("Phone", folder, clock, "AAAA-BBBB-CCCC-DDDD"); val b = Device("PC", folder, clock, "AAAA-BBBB-CCCC-DDDD")
        a.service.syncNow(); b.service.syncNow()
        a.service.checkForChanges()                                   // nothing changed: first call only records the state
        SubscriptionRepository(a.db).subscribe("UC3", "Three", null, at)
        a.service.checkForChanges()                                   // noticed, and published
        b.service.syncNow()
        assertEquals(setOf("UC3"), SubscriptionRepository(b.db).all().map { it.channelId }.toSet())
    }

    @Test fun twoDevicesSyncThroughWebDavAndAFailedTestSaysWhy() = runTest {
        val shared = File(Files.createTempDirectory("dav").toFile().path)
        fun dev(name: String): Pair<SyncService, SqlDelightSettingsStore> {
            val db = createDatabase(JvmDatabaseDriverFactory()); val st = SqlDelightSettingsStore(db)
            val factory = WebDavFactory { url, user, pass -> check(user == "me" && pass == "pw") { "The server rejected the username or password." }; FolderSyncBackend(shared) }
            val svc = SyncService(db, st, CredentialStore(InMemorySecretStore()), null, null, factory, Blocklist(st), null, name, clock)
            return svc to st
        }
        val (a, sa) = dev("Phone"); val (b, sb) = dev("PC")
        assertEquals("The server rejected the username or password.", a.testWebDav("https://x/dav", "me", "bad"))
        assertNull(a.testWebDav("https://x/dav", "me", "pw"))
        for (s in listOf(a, b)) { s.setMode(SyncMode.WEBDAV); s.setWebDav("https://x/dav", "me", "pw"); s.setCode("AAAA-BBBB-CCCC-DDDD") }
        assertTrue(a.config.value.isSetUp)
        sa.put("playback.speed", SettingLayer.Global, "1.5")
        a.syncNow(); b.syncNow()
        assertEquals("1.5", sb.get("playback.speed", SettingLayer.Global))
        a.turnOff()
        assertFalse(a.config.value.isSetUp); assertNull(a.config.value.webDavUrl)
    }

    private fun svc(network: () -> com.greenaltambe.vela.core.network.NetworkType, failing: Boolean = false): SyncService {
        val db = createDatabase(JvmDatabaseDriverFactory()); val st = SqlDelightSettingsStore(db)
        return SyncService(db, st, CredentialStore(InMemorySecretStore()), null, null, null, Blocklist(st), null, "X", clock,
            backendOverride = { if (failing) throw java.io.IOException("server down") else FolderSyncBackend(folder) }, network = network).also { it.setFolder(folder.path); it.setCode("AAAA-BBBB-CCCC-DDDD") }
    }

    @Test fun failuresRetryWithGrowingPausesAndManualDoesNotRetry() = runTest {
        val s = svc({ com.greenaltambe.vela.core.network.NetworkType.UNMETERED }, failing = true)
        assertEquals(30, (s.syncNow() as SyncStatus.Failed).retryInSeconds)
        assertEquals(60, (s.syncNow() as SyncStatus.Failed).retryInSeconds)
        assertEquals(120, (s.syncNow() as SyncStatus.Failed).retryInSeconds)
        s.setFrequency(SyncFrequency.MANUAL)
        assertNull((s.syncNow() as SyncStatus.Failed).retryInSeconds)
    }

    @Test fun offlineAndMobileDataWaitInsteadOfFailingAndTheButtonOverrides() = runTest {
        var net = com.greenaltambe.vela.core.network.NetworkType.OFFLINE
        val s = svc({ net })
        assertTrue(s.syncNow() is SyncStatus.Waiting)
        net = com.greenaltambe.vela.core.network.NetworkType.METERED
        assertTrue(s.syncNow() is SyncStatus.Idle, "mobile data is fine unless Only on Wi-Fi is on")
        s.setWifiOnly(true)
        s.setMode(SyncMode.FOLDER)
        assertTrue(s.syncNow() is SyncStatus.Waiting)
        assertTrue(s.syncNow(manual = true) is SyncStatus.Idle)
    }

    @Test fun oldAutoOffSettingBecomesManual() = runTest {
        val s = svc({ com.greenaltambe.vela.core.network.NetworkType.UNMETERED })
        s.setAuto(false); assertEquals(SyncFrequency.MANUAL, s.config.value.frequency)
        s.setFrequency(SyncFrequency.HOURLY); assertTrue(s.config.value.auto)
    }

    @Test fun aSafetyBackupIsMadeBeforeTheFirstSyncAndFailureStopsTheSync() = runTest {
        val made = mutableListOf<String>()
        val db = createDatabase(JvmDatabaseDriverFactory()); val st = SqlDelightSettingsStore(db)
        val svc = SyncService(db, st, CredentialStore(InMemorySecretStore()), null, null, null, Blocklist(st), null, "X", clock,
            backendOverride = { FolderSyncBackend(folder) }, safetyBackup = { made += it }).also { it.setFolder(folder.path); it.setCode("AAAA-BBBB-CCCC-DDDD") }
        assertTrue(svc.syncNow() is SyncStatus.Idle)
        assertEquals(listOf("first"), made)
        svc.syncNow(); assertEquals(listOf("first"), made, "only the first sync")

        val db2 = createDatabase(JvmDatabaseDriverFactory()); val st2 = SqlDelightSettingsStore(db2)
        val failing = SyncService(db2, st2, CredentialStore(InMemorySecretStore()), null, null, null, Blocklist(st2), null, "Y", clock,
            backendOverride = { FolderSyncBackend(folder) }, safetyBackup = { throw java.io.IOException("disk full") }).also { it.setFolder(folder.path); it.setCode("AAAA-BBBB-CCCC-DDDD") }
        val r = failing.syncNow()
        assertTrue(r is SyncStatus.Failed && "safety backup" in r.message, "$r")
        assertEquals(1, folder.listFiles()!!.count { it.name.endsWith(".vsync") }, "the failing device published nothing")
    }

    @Test fun aLargeIncomingChangeMakesABackupFirst() = runTest {
        val a = Device("Phone", folder, clock, "AAAA-BBBB-CCCC-DDDD")
        repeat(30) { SubscriptionRepository(a.db).subscribe("UC$it", "C$it", null, at) }
        a.service.syncNow()
        val made = mutableListOf<String>()
        val db = createDatabase(JvmDatabaseDriverFactory()); val st = SqlDelightSettingsStore(db)
        val b = SyncService(db, st, CredentialStore(InMemorySecretStore()), null, null, null, Blocklist(st), null, "PC", clock,
            backendOverride = { FolderSyncBackend(folder) }, safetyBackup = { made += it }).also { it.setFolder(folder.path); it.setCode("AAAA-BBBB-CCCC-DDDD") }
        b.syncNow()
        assertEquals(listOf("first", "large"), made)
        assertEquals(30, SubscriptionRepository(db).all().size)
    }

    @Test fun searchHistorySyncsAndDeletesAndLaterSearchesWin() = runTest {
        val a = Device("Phone", folder, clock, "AAAA-BBBB-CCCC-DDDD"); val b = Device("PC", folder, clock, "AAAA-BBBB-CCCC-DDDD")
        SearchHistoryRepository(a.db).apply { add("lofi", at); add("Kotlin", at) }
        a.service.syncNow(); b.service.syncNow()
        assertEquals(setOf("lofi", "Kotlin"), SearchHistoryRepository(b.db).recent().toSet())
        SearchHistoryRepository(b.db).remove("lofi"); b.service.syncNow(); a.service.syncNow()
        assertEquals(listOf("Kotlin"), SearchHistoryRepository(a.db).recent(), "a delete on one device reaches the other")
        SearchHistoryRepository(a.db).add("lofi", Instant.fromEpochMilliseconds(t + 10_000_000)); a.service.syncNow(); b.service.syncNow()
        assertEquals(setOf("lofi", "Kotlin"), SearchHistoryRepository(b.db).recent().toSet(), "searching again after a delete brings it back")
    }

    @Test fun clearingEverythingOnPurposeReachesTheOtherDeviceButAnAccidentIsStopped() = runTest {
        val a = Device("Phone", folder, clock, "AAAA-BBBB-CCCC-DDDD"); val b = Device("PC", folder, clock, "AAAA-BBBB-CCCC-DDDD")
        val repo = SqlDelightWatchHistoryRepository(a.db)
        repeat(40) { i -> val id = repo.begin(LOCAL_ACCOUNT, "v$i", "UC", "T$i", Instant.fromEpochMilliseconds(1_700_000_000_000 + i), 1000, 10_000, 1000); repo.update(id, Instant.fromEpochMilliseconds(1_700_000_000_000 + i), 1000, 10_000, 1000) }
        a.service.syncNow(); b.service.syncNow()
        assertEquals(40, HistoryLibrary(b.db).recent().map { it.videoId }.distinct().size)

        HistoryLibrary(a.db).clear()                          // no intent: looks like a failed read
        val stopped = a.service.syncNow()
        assertTrue(stopped is SyncStatus.Failed && "protect" in stopped.message, "$stopped")
        assertEquals(40, HistoryLibrary(b.db).recent().map { it.videoId }.distinct().size)

        a.service.markIntentionalDelete()                     // "Clear history" was confirmed
        assertTrue(a.service.syncNow() is SyncStatus.Idle)
        b.service.syncNow()
        assertTrue(HistoryLibrary(b.db).recent().isEmpty(), "the other device cleared it too")
    }

    @Test fun deletingTheLatestWatchOfAVideoRemovesTheWholeVideo() = runTest {
        val db = createDatabase(JvmDatabaseDriverFactory()); val repo = SqlDelightWatchHistoryRepository(db)
        for (ms in listOf(1_000L, 2_000L)) { val id = repo.begin(LOCAL_ACCOUNT, "v", "UC", "T", Instant.fromEpochMilliseconds(ms), 0, 10_000, 0); repo.update(id, Instant.fromEpochMilliseconds(ms), 0, 10_000, 0) }
        val lib = HistoryLibrary(db)
        lib.delete(lib.recent().first { it.watchedAt.toEpochMilliseconds() == 2_000L }.id)
        assertTrue(lib.recent().isEmpty())
    }

    @Test fun wrongCodeFailsClearlyAndPublishesNothing() = runTest {
        val a = Device("Phone", folder, clock, "AAAA-BBBB-CCCC-DDDD")
        SubscriptionRepository(a.db).subscribe("UC1", "One", null, at)
        a.service.syncNow()
        val b = Device("PC", folder, clock, "ZZZZ-ZZZZ-ZZZZ-ZZZZ")
        val st = b.service.syncNow()
        assertTrue(st is SyncStatus.Failed && st.wrongCode, "$st")
        assertEquals(1, folder.listFiles()!!.size)
        assertTrue(SubscriptionRepository(b.db).all().isEmpty())
    }

    @Test fun notSetUpDoesNothing() = runTest {
        val db = createDatabase(JvmDatabaseDriverFactory()); val s = SqlDelightSettingsStore(db)
        val svc = SyncService(db, s, CredentialStore(InMemorySecretStore()), null, null, null, Blocklist(s), null, "X", clock)
        assertEquals(SyncStatus.NotSetUp, svc.syncNow())
    }
}
