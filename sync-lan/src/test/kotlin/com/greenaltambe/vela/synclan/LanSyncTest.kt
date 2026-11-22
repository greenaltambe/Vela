package com.greenaltambe.vela.synclan

import com.greenaltambe.vela.auth.CredentialStore
import com.greenaltambe.vela.auth.InMemorySecretStore
import com.greenaltambe.vela.data.*
import com.greenaltambe.vela.feed.Blocklist
import com.greenaltambe.vela.sync.SyncService
import com.greenaltambe.vela.sync.SyncStatus
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Files
import kotlin.test.*
import kotlin.time.Instant

class LanSyncTest {
    private val loop = InetAddress.getByName("127.0.0.1")
    private fun transport(port: Int, peerPort: Int) = LanSyncTransport(Files.createTempDirectory("lan").toFile(), port, { listOf(InetSocketAddress(loop, peerPort)) }, announceEveryMs = 300)

    private class Dev(val name: String, val lan: LanSyncTransport, code: String) {
        val db = createDatabase(JvmDatabaseDriverFactory())
        val store = SqlDelightSettingsStore(db)
        val service = SyncService(db, store, CredentialStore(InMemorySecretStore()), null, lan, null, Blocklist(store), null, name).also { it.setCode(code) }
    }

    private fun waitFor(ms: Long = 15_000, cond: () -> Boolean) { val end = System.currentTimeMillis() + ms; while (!cond() && System.currentTimeMillis() < end) Thread.sleep(100); assertTrue(cond(), "timed out") }

    @Test fun twoDevicesFindEachOtherAndSwapDataOverTheNetwork() = runBlocking {
        val a = Dev("Phone", transport(47411, 47412), "AAAA-BBBB-CCCC-DDDD"); val b = Dev("PC", transport(47412, 47411), "AAAA-BBBB-CCCC-DDDD")
        try {
            val at = Instant.fromEpochMilliseconds(1_700_000_000_000)
            SubscriptionRepository(a.db).subscribe("UC1", "One", null, at)
            SubscriptionRepository(b.db).subscribe("UC2", "Two", null, at)
            a.service.syncNow(); b.service.syncNow()
            waitFor { a.service.nearby.value.any { it.name == "PC" } && b.service.nearby.value.any { it.name == "Phone" } }
            // peers fetch each other's file and sync by themselves
            waitFor { SubscriptionRepository(a.db).all().size == 2 && SubscriptionRepository(b.db).all().size == 2 }
            // a change on one side arrives on the other, and the exchange settles (no endless ping-pong)
            SubscriptionRepository(b.db).unsubscribe("UC1"); b.service.syncNow()
            waitFor { SubscriptionRepository(a.db).all().map { it.channelId } == listOf("UC2") }
            val v1 = a.lan.backend().let { runBlocking { it.read("${a.service.deviceIdForTest()}.vsync") } }
            Thread.sleep(2_500)
            assertContentEquals(v1, runBlocking { a.lan.backend().read("${a.service.deviceIdForTest()}.vsync") })
        } finally { a.lan.stop(); b.lan.stop() }
    }

    @Test fun discoveryWorksEvenWhenOnlyOneSideCanSendToTheOther() = runBlocking {
        // B's announcements never reach A (nothing to send to), but A's reach B: B answers A directly.
        val a = Dev("Phone", transport(47431, 47432), "AAAA-BBBB-CCCC-DDDD")
        val b = Dev("PC", LanSyncTransport(Files.createTempDirectory("lan").toFile(), 47432, { emptyList() }, announceEveryMs = 300), "AAAA-BBBB-CCCC-DDDD")
        try {
            a.service.syncNow(); b.service.syncNow()
            waitFor { a.service.nearby.value.any { it.name == "PC" } && b.service.nearby.value.any { it.name == "Phone" } }
        } finally { a.lan.stop(); b.lan.stop() }
    }

    @Test fun aDeviceWithAnotherCodeIsIgnored() = runBlocking {
        val a = Dev("Phone", transport(47421, 47422), "AAAA-BBBB-CCCC-DDDD"); val b = Dev("Other", transport(47422, 47421), "ZZZZ-ZZZZ-ZZZZ-ZZZZ")
        try {
            a.service.syncNow(); b.service.syncNow()
            Thread.sleep(2_000)
            assertTrue(a.service.nearby.value.isEmpty() && b.service.nearby.value.isEmpty())
        } finally { a.lan.stop(); b.lan.stop() }
    }
}
