package com.greenaltambe.vela.synclan

import com.greenaltambe.vela.sync.LanPeer
import com.greenaltambe.vela.sync.LanTransport
import com.greenaltambe.vela.sync.SyncBackend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.net.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Same-Wi-Fi sync. Every few seconds each device broadcasts "group, my id, my port, version of my file". A device that sees a peer of its own group
 * whose file version changed downloads that file (plain HTTP GET; the file is already AES-encrypted with the sync code, so the wire carries only
 * ciphertext). The engine works on [cacheDir], so a peer that is switched off still contributes its last file.
 *
 * [port] is the UDP discovery port; [announceTo] defaults to the broadcast addresses of this machine's networks (tests pass loopback targets).
 */
class LanSyncTransport(
    private val cacheDir: File,
    private val port: Int = 47321,
    private val announceTo: (() -> List<InetSocketAddress>)? = null,
    /** Android needs a multicast lock held while listening; the app passes a function that takes (true) or releases (false) it. */
    private val networkLock: (Boolean) -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
    private val announceEveryMs: Long = 4_000,
) : LanTransport {
    private val _peers = MutableStateFlow<List<LanPeer>>(emptyList())
    override val peers: StateFlow<List<LanPeer>> get() = _peers

    private class Run(val group: String, val deviceId: String, val name: String, val onPeerData: () -> Unit) {
        @Volatile var open = true
        val sockets = mutableListOf<java.io.Closeable>()
        val seen = ConcurrentHashMap<String, LanPeer>()
        val pulled = ConcurrentHashMap<String, String>()
        val pulling = ConcurrentHashMap.newKeySet<String>()
        @Volatile var tcpPort = 0
        val key get() = "$group/$deviceId/$name"
    }

    @Volatile private var run: Run? = null
    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "vela-lan").apply { isDaemon = true } }

    @Synchronized override fun start(secret: String, deviceId: String, deviceName: String, onPeerData: () -> Unit) {
        val group = groupOf(secret)
        if (run?.key == "$group/$deviceId/$deviceName") return
        stop()
        cacheDir.mkdirs()
        val r = Run(group, deviceId, deviceName.replace('\t', ' ').replace('\n', ' '), onPeerData)
        run = r
        runCatching { networkLock(true) }
        val server = ServerSocket(0).also { r.sockets += it }
        r.tcpPort = server.localPort
        pool.execute { serve(r, server) }
        val udp = runCatching { DatagramSocket(null).apply { reuseAddress = true; broadcast = true; bind(InetSocketAddress(this@LanSyncTransport.port)) } }.getOrNull()
        if (udp != null) { r.sockets += udp; pool.execute { listen(r, udp) } }
        pool.execute { announce(r) }
    }

    @Synchronized override fun stop() {
        val r = run ?: return
        r.open = false
        r.sockets.forEach { runCatching { it.close() } }
        run = null
        _peers.value = emptyList()
        runCatching { networkLock(false) }
    }

    override fun backend(): SyncBackend = CacheBackend(cacheDir)

    override fun reset() { cacheDir.listFiles()?.forEach { it.delete() } }

    // ---- announcing and listening -----------------------------------------------------------------------------------------------

    private fun announce(r: Run) {
        val out = runCatching { DatagramSocket().apply { broadcast = true } }.getOrNull() ?: return
        r.sockets += out
        while (r.open) {
            val version = File(cacheDir, "${r.deviceId}.vsync").takeIf { it.isFile }?.lastModified()?.toString() ?: "0"
            val bytes = packet("VELA1", r, version)
            for (t in targets()) runCatching { out.send(DatagramPacket(bytes, bytes.size, t)) }
            prune(r)
            try { Thread.sleep(announceEveryMs) } catch (e: InterruptedException) { return }
        }
    }

    private fun packet(kind: String, r: Run, version: String = ownVersion(r)): ByteArray =
        "$kind\t${r.group}\t${r.deviceId}\t${r.tcpPort}\t$version\t${r.name}\t$port".toByteArray()

    private fun ownVersion(r: Run) = File(cacheDir, "${r.deviceId}.vsync").takeIf { it.isFile }?.lastModified()?.toString() ?: "0"

    private fun targets(): List<InetSocketAddress> = announceTo?.invoke() ?: buildList {
        add(InetSocketAddress(InetAddress.getByName("255.255.255.255"), port))
        runCatching {
            NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
                .flatMap { it.interfaceAddresses }.mapNotNull { it.broadcast }.forEach { add(InetSocketAddress(it, port)) }
        }
    }

    private fun listen(r: Run, udp: DatagramSocket) {
        val buf = ByteArray(1024)
        while (r.open) {
            val p = DatagramPacket(buf, buf.size)
            try { udp.receive(p) } catch (e: Exception) { return }
            val f = String(p.data, 0, p.length).split('\t')
            if (f.size < 6 || (f[0] != "VELA1" && f[0] != "VELA1R") || f[1] != r.group || f[2] == r.deviceId) continue
            val id = f[2]; val tcp = f[3].toIntOrNull() ?: continue; val version = f[4]
            val host = p.address.hostAddress ?: continue
            r.seen[id] = LanPeer(id, f[5].ifBlank { id }, host, now())
            publish(r)
            // Broadcasts only reach us one way on some networks and phones; answering straight to the sender (not a broadcast) makes discovery work both ways.
            if (f[0] == "VELA1") {
                val reply = packet("VELA1R", r)
                runCatching { udp.send(DatagramPacket(reply, reply.size, InetSocketAddress(p.address, f.getOrNull(6)?.toIntOrNull() ?: port))) }
            }
            if (version != "0" && r.pulled[id] != version && r.pulling.add(id)) pool.execute {
                try { if (fetch(r, id, host, tcp)) { r.pulled[id] = version; r.onPeerData() } } finally { r.pulling.remove(id) }
            }
        }
    }

    private fun prune(r: Run) {
        r.seen.values.removeIf { now() - it.lastSeen > 30_000 }
        publish(r)
    }

    private fun publish(r: Run) { if (r.open) _peers.value = r.seen.values.sortedBy { it.name } }

    // ---- file exchange ----------------------------------------------------------------------------------------------------------

    /**
     * A bare-socket HTTP GET. Not HttpURLConnection: Android refuses cleartext http:// through it, and this traffic is already AES-encrypted
     * data on the local network, so a plain socket is the simplest thing that works on both platforms.
     */
    private fun fetch(r: Run, id: String, host: String, tcp: Int): Boolean = try {
        Socket().use { s ->
            s.connect(InetSocketAddress(host, tcp), 3_000); s.soTimeout = 8_000
            s.getOutputStream().apply { write("GET /f/$id.vsync HTTP/1.1\r\nHost: $host\r\nX-Group: ${r.group}\r\nConnection: close\r\n\r\n".toByteArray()); flush() }
            val input = java.io.BufferedInputStream(s.getInputStream())
            fun line(): String { val sb = StringBuilder(); while (true) { val c = input.read(); if (c < 0 || c == '\n'.code) break; if (c != '\r'.code) sb.append(c.toChar()) }; return sb.toString() }
            val status = line()
            var length = -1
            while (true) { val h = line(); if (h.isEmpty()) break; if (h.startsWith("Content-Length:", true)) length = h.substringAfter(':').trim().toIntOrNull() ?: -1 }
            if (!status.contains(" 200") || length < 0 || length > MAX_BYTES) false
            else {
                val data = input.readNBytes(length)
                if (data.size != length) false
                else {
                    val tmp = File(cacheDir, ".$id.tmp").also { it.writeBytes(data) }
                    Files.move(tmp.toPath(), File(cacheDir, "$id.vsync").toPath(), StandardCopyOption.REPLACE_EXISTING)
                    true
                }
            }
        }
    } catch (e: Exception) { false }

    private fun serve(r: Run, server: ServerSocket) {
        while (r.open) {
            val s = try { server.accept() } catch (e: Exception) { return }
            pool.execute { runCatching { s.use { handle(r, it) } } }
        }
    }

    private fun handle(r: Run, s: Socket) {
        s.soTimeout = 5_000
        val reader = s.getInputStream().bufferedReader()
        val request = reader.readLine() ?: return
        var group = ""
        while (true) { val h = reader.readLine() ?: break; if (h.isEmpty()) break; if (h.startsWith("X-Group:", true)) group = h.substringAfter(':').trim() }
        val out = s.getOutputStream()
        val file = File(cacheDir, "${r.deviceId}.vsync")
        // Only this device's own file, and only to someone who knows the group (a hash of the sync code).
        if (request != "GET /f/${r.deviceId}.vsync HTTP/1.1" && request != "GET /f/${r.deviceId}.vsync HTTP/1.0" || group != r.group || !file.isFile) {
            out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()); return
        }
        val bytes = file.readBytes()
        out.write("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(bytes); out.flush()
    }

    private companion object {
        const val MAX_BYTES = 32 * 1024 * 1024
        fun groupOf(secret: String): String = MessageDigest.getInstance("SHA-256").digest("vela-lan:$secret".toByteArray()).take(8).joinToString("") { "%02x".format(it) }
    }
}

/** A plain folder used as the engine's cache. Writes are atomic (temp file, then replace). */
internal class CacheBackend(private val dir: File) : SyncBackend {
    override suspend fun list(): List<String> = dir.listFiles { f -> f.isFile && f.name.endsWith(".vsync") }?.map { it.name }.orEmpty()
    override suspend fun read(name: String): ByteArray? = File(dir, name).takeIf { it.isFile }?.readBytes()
    override suspend fun write(name: String, data: ByteArray) {
        dir.mkdirs()
        val tmp = File(dir, ".$name.tmp").also { it.writeBytes(data) }
        Files.move(tmp.toPath(), File(dir, name).toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
    override suspend fun delete(name: String) { File(dir, name).delete() }
}
