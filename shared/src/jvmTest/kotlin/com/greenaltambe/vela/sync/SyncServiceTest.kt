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
        val service = SyncService(db, store, CredentialStore(InMemorySecretStore()), null, null, blocklist, null, name, clock, backendOverride = { FolderSyncBackend(File(it)) }).also {
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
        val service = SyncService(db, store, credentials, null, null, blocklist, accounts, name, clock, backendOverride = { FolderSyncBackend(File(it)) }).also {
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
        val svc = SyncService(db, s, CredentialStore(InMemorySecretStore()), null, null, Blocklist(s), null, "X", clock)
        assertEquals(SyncStatus.NotSetUp, svc.syncNow())
    }
}
