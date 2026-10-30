package com.greenaltambe.vela

import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom

/**
 * One running Vela per user. A second start (`vela https://youtu.be/…`, from a file manager or a launcher) hands its link to the running
 * one over a loopback socket and exits, instead of opening a second window on the same database. The port and a random token live in a
 * file in the data directory (readable only by the user), so another user's process cannot send anything.
 */
object SingleInstance {
    /** Returns true when this process is the running instance (and [onLink] receives links sent by later starts); false when the link was handed over. */
    fun claim(dir: File, link: String?, onLink: (String) -> Unit): Boolean {
        val file = File(dir, "instance")
        if (forward(file, link ?: "")) return false
        val server = runCatching { ServerSocket(0, 8, InetAddress.getLoopbackAddress()) }.getOrNull() ?: return true
        val token = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        runCatching {
            dir.mkdirs()
            file.writeText("${server.localPort} $token")
            file.setReadable(false, false); file.setReadable(true, true); file.setWritable(false, false); file.setWritable(true, true)
        }
        Thread({
            while (!server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                runCatching {
                    client.soTimeout = 2_000
                    val line = client.getInputStream().bufferedReader().readLine().orEmpty()
                    if (line.startsWith("$token ")) { client.getOutputStream().write("ok\n".toByteArray()); onLink(line.substring(token.length + 1)) }
                }
                runCatching { client.close() }
            }
        }, "vela-single-instance").apply { isDaemon = true }.start()
        Runtime.getRuntime().addShutdownHook(Thread { runCatching { server.close(); file.delete() } })
        return true
    }

    /** Sends [link] to the instance named in [file]; true if one answered. A stale file (the old instance crashed) answers nothing and is taken over. */
    private fun forward(file: File, link: String): Boolean = runCatching {
        val (port, token) = file.readText().trim().split(' ').let { it[0].toInt() to it[1] }
        Socket(InetAddress.getLoopbackAddress(), port).use { s ->
            s.soTimeout = 2_000
            s.getOutputStream().write("$token $link\n".toByteArray())
            s.getInputStream().bufferedReader().readLine() == "ok"
        }
    }.getOrDefault(false)
}
