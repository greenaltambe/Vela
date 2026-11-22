package com.greenaltambe.vela.sync

import kotlinx.serialization.Serializable

/**
 * Hybrid logical clock stamp. Total order: wall time, then counter, then device id (so two devices never tie).
 * Encoded as a fixed-width string so plain string comparison equals the order.
 */
@Serializable
data class Hlc(val wallMs: Long, val counter: Int, val device: String) : Comparable<Hlc> {
    override fun compareTo(other: Hlc): Int = compareValuesBy(this, other, { it.wallMs }, { it.counter }, { it.device })

    companion object {
        val ZERO = Hlc(0, 0, "")
    }
}

/** One synced fact: [value] null is a tombstone (the key was removed). The record with the highest [hlc] per key wins. */
@Serializable
data class SyncRecord(val key: String, val value: String?, val hlc: Hlc)

/** What one device publishes: everything it authored that is still current. One writer per file, so storage never sees a write conflict. */
@Serializable
data class DeviceFile(val formatVersion: Int = 1, val device: String, val name: String = "", val syncedAt: Long = 0, val records: List<SyncRecord>)

/** A slice of local app data, flattened to `key -> value` so merging needs no knowledge of the data's shape. Keys must be stable across devices. */
interface SyncDomain {
    /** Key prefix owned by this domain, e.g. "sub/". */
    val prefix: String

    /** Current local state; every key starts with [prefix]. Throw if it cannot be read (an empty result means "really empty"). */
    fun read(): Map<String, String>

    /**
     * When the fact in [value] really happened (e.g. when a video was watched), in epoch ms, or null to use the time the change was noticed.
     * Lets an old edit that is found late still lose to a newer one made on another device.
     */
    fun happenedAt(value: String): Long? = null

    /** Brings local state to [changes]: a value sets the key, null removes it. Called once per sync, only with real differences. */
    fun apply(changes: Map<String, String?>)
}

/** Opaque files in some storage (a folder, WebDAV, ...). Names are flat; the engine only uses `<device>.vsync`. */
interface SyncBackend {
    suspend fun list(): List<String>
    suspend fun read(name: String): ByteArray?
    /** Replaces the file as a whole; implementations should make this atomic (write temp then rename). */
    suspend fun write(name: String, data: ByteArray)
    suspend fun delete(name: String)
}

/** Turns bytes into what is stored, and back. A wrong passphrase must throw [SyncDecodeException]. */
interface SyncCodec {
    fun encode(plain: ByteArray): ByteArray
    fun decode(stored: ByteArray): ByteArray
}

class SyncDecodeException(message: String, cause: Throwable? = null) : Exception(message, cause)

object PlainCodec : SyncCodec {
    override fun encode(plain: ByteArray) = plain
    override fun decode(stored: ByteArray) = stored
}

/** A device on the same Wi-Fi that is running Vela with the same sync code. */
data class LanPeer(val deviceId: String, val name: String, val host: String, val lastSeen: Long)

/**
 * Sync over the local network: devices with the same sync code find each other and swap their (encrypted) files directly. The engine works on a
 * local cache folder ([backend]); this class fills it with the other devices' files and shares this device's own file.
 */
interface LanTransport {
    /** Devices seen on the network in the last half minute. */
    val peers: kotlinx.coroutines.flow.StateFlow<List<LanPeer>>
    /** Starts finding and serving. [secret] only picks the group (a hash of it is announced); [onPeerData] is called after a peer's file was fetched. */
    fun start(secret: String, deviceId: String, deviceName: String, onPeerData: () -> Unit)
    fun stop()
    /** The cache the engine reads and writes. */
    fun backend(): SyncBackend
    /** Forgets every cached file (sync turned off). */
    fun reset()
}

/** Where the engine keeps its own bookkeeping between syncs (a settings row on device). */
interface SyncStateStore {
    fun load(): String?
    fun save(json: String)
}
