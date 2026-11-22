package com.greenaltambe.vela.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class SyncResult(
    val authored: Int,
    val appliedLocally: Int,
    val devicesRead: Int,
    /** Files that could not be read or decoded (wrong passphrase, truncated); their data is ignored, never deleted. */
    val skipped: List<String>,
    /** How many of [skipped] could not be decoded with this device's sync code (the code differs or the file is damaged). */
    val wrongCode: Int = 0,
    /** The other devices seen in this sync: name and when they last synced (epoch ms). */
    val devices: List<SyncDevice> = emptyList(),
)

data class SyncDevice(val id: String, val name: String, val syncedAt: Long)

class SyncAbortedException(message: String) : Exception(message)

/** Every other device's file failed to open with this code: nothing was published or changed, so a typo never pollutes the folder. */
class SyncWrongCodeException : Exception("This sync code does not open the files in the folder.")

/**
 * Convergent sync over dumb storage. Every device owns one file; it rewrites only that one and only reads the others, and the winner per
 * key is the highest [Hlc]. Merging is commutative and idempotent, so repeated, partial, duplicated or reordered syncs converge.
 *
 * Local changes are found by diffing the domains against what the last sync left behind (no hooks in each repository).
 */
class SyncEngine(
    private val deviceId: String,
    private val domains: List<SyncDomain>,
    private val backend: SyncBackend,
    private val codec: SyncCodec,
    private val state: SyncStateStore,
    private val now: () -> Long,
    private val deviceName: String = "",
    /** A sync that would delete more than this many keys at once, and over half of what it knew, is refused (a failed read must not wipe a library). */
    private val massDeleteFloor: Int = 10,
) {
    @Serializable
    private data class Saved(val lastWall: Long = 0, val lastCounter: Int = 0, val records: Map<String, SyncRecord> = emptyMap(), val publishedSig: String = "", val publishedAt: Long = 0, val seenLocal: Set<String>? = null)

    private val json = Json { ignoreUnknownKeys = true }
    private fun fileName(device: String) = "$device.vsync"

    suspend fun sync(): SyncResult {
        val saved = state.load()?.let { runCatching { json.decodeFromString<Saved>(it) }.getOrNull() } ?: Saved()
        val known = saved.records.toMutableMap()
        var wall = saved.lastWall
        var counter = saved.lastCounter
        fun tick(): Hlc {
            val t = now()
            if (t > wall) { wall = t; counter = 0 } else counter++
            return Hlc(wall, counter, deviceId)
        }

        // 1. Local changes since the last sync.
        val local = HashMap<String, String>()
        for (d in domains) for ((k, v) in d.read()) {
            require(k.startsWith(d.prefix)) { "key $k is outside ${d.prefix}" }
            local[k] = v
        }
        val authored = ArrayList<SyncRecord>()
        for ((k, v) in local) if (known[k]?.value != v) {
            val at = domains.first { k.startsWith(it.prefix) }.happenedAt(v)
            authored += SyncRecord(k, v, if (at != null) Hlc(at, 0, deviceId) else tick())
        }
        // Only what this device really had before can have been deleted here. Records that arrived from elsewhere but were never applied here (a domain that
        // just appeared, e.g. a Google account that was just signed in) are not deletions.
        val had = saved.seenLocal ?: known.filter { it.value.value != null }.keys
        val removed = known.filter { (k, r) -> r.value != null && k !in local && k in had && domains.any { k.startsWith(it.prefix) } }.keys
        val live = known.values.count { it.value != null }
        if (removed.size > massDeleteFloor && removed.size * 2 > live) {
            throw SyncAbortedException("This sync would remove ${removed.size} of $live items from every device; refusing (local data may have failed to load).")
        }
        for (k in removed) authored += SyncRecord(k, null, tick())
        for (r in authored) known[r.key] = r

        // 2. Read the other devices.
        var devicesRead = 0
        val skipped = ArrayList<String>()
        var wrongCode = 0
        val devices = ArrayList<SyncDevice>()
        for (name in backend.list()) {
            if (!name.endsWith(".vsync") || name == fileName(deviceId)) continue
            val file = try {
                val bytes = backend.read(name) ?: continue
                json.decodeFromString<DeviceFile>(codec.decode(bytes).decodeToString())
            } catch (e: Exception) {
                skipped += name
                if (e is SyncDecodeException) wrongCode++
                continue
            }
            devicesRead++
            devices += SyncDevice(file.device, file.name.ifBlank { file.device }, file.syncedAt)
            for (r in file.records) {
                if (r.hlc.wallMs > wall || (r.hlc.wallMs == wall && r.hlc.counter > counter)) { wall = r.hlc.wallMs; counter = r.hlc.counter }
                val cur = known[r.key]
                if (cur == null || r.hlc > cur.hlc) known[r.key] = r
            }
        }

        if (wrongCode > 0 && devicesRead == 0) throw SyncWrongCodeException()

        // 3. Publish ours: every winning record we authored (so a rewrite is complete, never partial).
        val mine = known.values.filter { it.hlc.device == deviceId }
        // Only when something changed (or every 30 minutes as a heartbeat): a rewrite makes other devices fetch it, so rewriting for nothing would ping-pong.
        val sig = json.encodeToString(DeviceFile(device = deviceId, name = deviceName, records = mine))
        val unchanged = sig == saved.publishedSig && now() - saved.publishedAt < 30 * 60_000L && fileName(deviceId) in backend.list()
        var publishedAt = saved.publishedAt
        if (!unchanged) {
            val payload = json.encodeToString(DeviceFile(device = deviceId, name = deviceName, syncedAt = now(), records = mine)).encodeToByteArray()
            backend.write(fileName(deviceId), codec.encode(payload))
            publishedAt = now()
        }

        // 4. Apply what differs locally. Only after the upload succeeded, so a failed sync changes nothing here.
        var applied = 0
        for (d in domains) {
            val changes = HashMap<String, String?>()
            for ((k, r) in known) if (k.startsWith(d.prefix) && local[k] != r.value) changes[k] = r.value
            if (changes.isNotEmpty()) { d.apply(changes); applied += changes.size }
        }

        // 5. Remember. Tombstones older than 90 days are dropped from the books.
        val cutoff = now() - 90L * 24 * 3600 * 1000
        known.entries.removeAll { it.value.value == null && it.value.hlc.wallMs < cutoff }
        val seen = (had.filter { k -> domains.none { k.startsWith(it.prefix) } } + known.filter { (k, r) -> r.value != null && domains.any { k.startsWith(it.prefix) } }.keys).toSet()
        state.save(json.encodeToString(Saved(wall, counter, known, sig, publishedAt, seen)))
        return SyncResult(authored.size, applied, devicesRead, skipped, wrongCode, devices)
    }
}
