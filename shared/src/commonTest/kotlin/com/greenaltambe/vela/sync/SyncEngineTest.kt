package com.greenaltambe.vela.sync

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SyncEngineTest {
    private class MemBackend : SyncBackend {
        val files = HashMap<String, ByteArray>()
        override suspend fun list() = files.keys.toList()
        override suspend fun read(name: String) = files[name]
        override suspend fun write(name: String, data: ByteArray) { files[name] = data }
        override suspend fun delete(name: String) { files.remove(name) }
    }

    private class MemDomain(override val prefix: String = "sub/", val data: MutableMap<String, String> = HashMap()) : SyncDomain {
        override fun read() = data.toMap()
        override fun apply(changes: Map<String, String?>) { for ((k, v) in changes) if (v == null) data.remove(k) else data[k] = v }
    }

    private class MemState : SyncStateStore {
        var json: String? = null
        override fun load() = json
        override fun save(json: String) { this.json = json }
    }

    private class Dev(id: String, backend: SyncBackend, clock: () -> Long, codec: SyncCodec = PlainCodec) {
        val domain = MemDomain()
        val engine = SyncEngine(id, listOf(domain), backend, codec, MemState(), clock)
    }

    private var t = 1_000_000L
    private val clock = { t += 10; t }

    @Test fun twoDevicesConverge() = runTest {
        val b = MemBackend()
        val a = Dev("A", b, clock); val p = Dev("B", b, clock)
        a.domain.data["sub/1"] = "one"; p.domain.data["sub/2"] = "two"
        a.engine.sync(); p.engine.sync(); a.engine.sync()
        assertEquals(mapOf("sub/1" to "one", "sub/2" to "two"), a.domain.data)
        assertEquals(a.domain.data, p.domain.data)
    }

    @Test fun deletionPropagatesAndLaterReAddWins() = runTest {
        val b = MemBackend()
        val a = Dev("A", b, clock); val p = Dev("B", b, clock)
        a.domain.data["sub/1"] = "one"
        a.engine.sync(); p.engine.sync()
        assertEquals("one", p.domain.data["sub/1"])
        a.domain.data.remove("sub/1"); a.engine.sync(); p.engine.sync()
        assertTrue(p.domain.data.isEmpty())
        p.domain.data["sub/1"] = "again"; p.engine.sync(); a.engine.sync()
        assertEquals("again", a.domain.data["sub/1"])
    }

    @Test fun concurrentEditsAreLastWriterWinsOnBothDevices() = runTest {
        val b = MemBackend()
        val a = Dev("A", b, clock); val p = Dev("B", b, clock)
        a.domain.data["sub/1"] = "v0"; a.engine.sync(); p.engine.sync()
        a.domain.data["sub/1"] = "fromA"; a.engine.sync()
        p.domain.data["sub/1"] = "fromB"; p.engine.sync()
        a.engine.sync(); p.engine.sync()
        assertEquals("fromB", a.domain.data["sub/1"])
        assertEquals("fromB", p.domain.data["sub/1"])
    }

    @Test fun repeatedSyncIsIdempotent() = runTest {
        val b = MemBackend()
        val a = Dev("A", b, clock)
        a.domain.data["sub/1"] = "x"
        a.engine.sync()
        val r = a.engine.sync()
        assertEquals(0, r.authored); assertEquals(0, r.appliedLocally)
    }

    @Test fun newDeviceBootstrapsWithoutDeletingAnything() = runTest {
        val b = MemBackend()
        val a = Dev("A", b, clock)
        repeat(30) { a.domain.data["sub/$it"] = "c$it" }
        a.engine.sync()
        val n = Dev("N", b, clock)
        n.engine.sync(); a.engine.sync()
        assertEquals(30, n.domain.data.size); assertEquals(30, a.domain.data.size)
    }

    @Test fun corruptOrUnreadableFileIsSkippedNotFatal() = runTest {
        val b = MemBackend()
        b.files["X.vsync"] = "not json".encodeToByteArray()
        val a = Dev("A", b, clock)
        a.domain.data["sub/1"] = "x"
        val r = a.engine.sync()
        assertEquals(listOf("X.vsync"), r.skipped)
        assertEquals("x", a.domain.data["sub/1"])
    }

    @Test fun emptyLocalReadCannotWipeEveryone() = runTest {
        val b = MemBackend()
        val a = Dev("A", b, clock)
        repeat(40) { a.domain.data["sub/$it"] = "c" }
        a.engine.sync()
        a.domain.data.clear() // simulates a failed/empty read
        assertFailsWith<SyncAbortedException> { a.engine.sync() }
        val p = Dev("B", b, clock); p.engine.sync()
        assertEquals(40, p.domain.data.size)
    }

    @Test fun threeDevicesAnyOrderConverge() = runTest {
        val b = MemBackend()
        val devs = listOf(Dev("A", b, clock), Dev("B", b, clock), Dev("C", b, clock))
        devs[0].domain.data["sub/a"] = "1"; devs[1].domain.data["sub/b"] = "2"; devs[2].domain.data["sub/c"] = "3"
        repeat(2) { for (d in listOf(2, 0, 1)) devs[d].engine.sync() }
        devs[1].domain.data.remove("sub/a")
        repeat(2) { for (d in listOf(1, 2, 0)) devs[d].engine.sync() }
        val expect = mapOf("sub/b" to "2", "sub/c" to "3")
        devs.forEach { assertEquals(expect, it.domain.data) }
    }

    @Test fun wrongCodecIsSkipped() = runTest {
        val b = MemBackend()
        val flip = object : SyncCodec {
            override fun encode(plain: ByteArray) = plain.reversedArray()
            override fun decode(stored: ByteArray) = stored.reversedArray()
        }
        val a = Dev("A", b, clock, flip); a.domain.data["sub/1"] = "x"; a.engine.sync()
        val strict = object : SyncCodec {
            override fun encode(plain: ByteArray) = plain
            override fun decode(stored: ByteArray): ByteArray = throw SyncDecodeException("wrong code")
        }
        val p = Dev("B", b, clock, strict)
        assertFailsWith<SyncWrongCodeException> { p.engine.sync() }
        assertTrue(p.domain.data.isEmpty())
        assertEquals(listOf("A.vsync"), b.files.keys.toList()) // the wrong-code device published nothing
    }
}
