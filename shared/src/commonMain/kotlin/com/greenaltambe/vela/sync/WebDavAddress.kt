package com.greenaltambe.vela.sync

enum class WebDavService(val label: String) { KOOFR("Koofr"), NEXTCLOUD("Nextcloud"), OTHER("Other") }

/** Builds the full folder address from what a person knows (their service, server, username, a folder name), so nobody has to type a long URL by hand. */
object WebDavAddress {
    const val KOOFR_BASE = "https://app.koofr.net/dav/Koofr"
    const val DEFAULT_FOLDER = "Vela sync"

    /** Adds https:// when missing and drops trailing slashes and spaces. Null for blank input. */
    fun normalizeServer(server: String): String? {
        val s = server.trim().trimEnd('/')
        if (s.isEmpty()) return null
        return if (s.startsWith("http://", true) || s.startsWith("https://", true)) s else "https://$s"
    }

    private fun segment(s: String) = buildString { for (b in s.encodeToByteArray()) { val c = b.toInt().toChar(); if (c.isLetterOrDigit() && b >= 0 || c in "-._~") append(c) else append("%" + (b.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0')) } }

    /** The address of the sync folder, or null while something needed is still empty. */
    fun build(service: WebDavService, server: String, user: String, folder: String): String? {
        val base = when (service) {
            WebDavService.KOOFR -> KOOFR_BASE
            WebDavService.NEXTCLOUD -> normalizeServer(server)?.let { s -> if ("/remote.php/" in s) s else if (user.isBlank()) return null else "$s/remote.php/dav/files/${segment(user.trim())}" } ?: return null
            WebDavService.OTHER -> normalizeServer(server) ?: return null
        }
        val parts = folder.split('/').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf(DEFAULT_FOLDER) }
        return base + parts.joinToString("") { "/" + segment(it) }
    }
}
