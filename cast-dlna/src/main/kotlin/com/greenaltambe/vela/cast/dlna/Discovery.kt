package com.greenaltambe.vela.cast.dlna

import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException

/** Finds the `LOCATION` of every device that answers an SSDP search. */
fun interface SsdpSearch { fun search(timeoutMs: Int): Set<String> }

/** SSDP over UDP multicast. On Android the app must hold a WifiManager.MulticastLock while this runs, or replies are dropped. */
object MulticastSsdpSearch : SsdpSearch {
    private const val GROUP = "239.255.255.250"
    private const val PORT = 1900

    override fun search(timeoutMs: Int): Set<String> {
        val found = LinkedHashSet<String>()
        val message = ("M-SEARCH * HTTP/1.1\r\nHOST: $GROUP:$PORT\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\n" +
            "ST: urn:schemas-upnp-org:service:AVTransport:1\r\n\r\n").toByteArray()
        MulticastSocket(null).use { socket ->
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(0))
            socket.soTimeout = 500
            val target = InetSocketAddress(InetAddress.getByName(GROUP), PORT)
            repeat(2) { socket.send(DatagramPacket(message, message.size, target)) }
            val end = System.currentTimeMillis() + timeoutMs
            val buf = ByteArray(2048)
            while (System.currentTimeMillis() < end) {
                try {
                    val p = DatagramPacket(buf, buf.size)
                    socket.receive(p)
                    parseLocation(String(p.data, 0, p.length))?.let(found::add)
                } catch (_: SocketTimeoutException) { }
            }
        }
        return found
    }
}

/** The `LOCATION` header of an SSDP response, or null. */
fun parseLocation(response: String): String? =
    response.lineSequence().firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }?.substringAfter(':')?.trim()?.takeIf { it.startsWith("http", ignoreCase = true) }

/** Searches, then reads each answering device's description. Devices without AVTransport are dropped; one that fails to answer is skipped. */
class DlnaDiscovery(private val ssdp: SsdpSearch = MulticastSsdpSearch, private val http: UpnpHttp = JdkUpnpHttp) {
    fun discover(timeoutMs: Int = 3000): List<DlnaRenderer> =
        ssdp.search(timeoutMs).mapNotNull { loc -> runCatching { parseDeviceDescription(http.get(loc), loc) }.getOrNull() }.distinctBy { it.udn }
}
