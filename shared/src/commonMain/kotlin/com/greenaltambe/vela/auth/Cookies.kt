package com.greenaltambe.vela.auth

import kotlin.time.Instant

/** One cookie. [expiresEpochSeconds] 0 or null is a session cookie. */
data class Cookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String = "/",
    val secure: Boolean = true,
    val httpOnly: Boolean = false,
    val expiresEpochSeconds: Long? = null,
) {
    fun domainMatches(host: String): Boolean {
        val d = domain.trimStart('.').lowercase(); val h = host.lowercase()
        return h == d || h.endsWith(".$d")
    }

    fun expired(now: Instant): Boolean = expiresEpochSeconds != null && expiresEpochSeconds > 0 && expiresEpochSeconds <= now.epochSeconds

    /** Never print this: it holds the value. */
    override fun toString(): String = "Cookie($name@$domain)"
}

/**
 * The session cookies of one signed-in account, as the browser would hold them. Parsing, the `Cookie` header for a host,
 * rotation updates from `Set-Cookie`, and the Netscape file format live here and nowhere else. [toString] never shows values.
 */
class CookieJar(cookies: List<Cookie> = emptyList()) {
    val cookies: List<Cookie> = cookies.distinctBy { Triple(it.name, it.domain.trimStart('.').lowercase(), it.path) }

    fun isEmpty() = cookies.isEmpty()

    fun get(name: String, host: String? = null): String? =
        cookies.firstOrNull { it.name == name && (host == null || it.domainMatches(host)) }?.value

    /** The value SAPISIDHASH is built from: `SAPISID`, or the equivalent `__Secure-3PAPISID`. */
    val sapisid: String? get() = get("SAPISID") ?: get("__Secure-3PAPISID") ?: get("__Secure-1PAPISID")

    /** True if the jar has the cookies that make a Google session at all (not that the session is still valid). */
    val looksSignedIn: Boolean get() = (get("SID") ?: get("__Secure-3PSID") ?: get("__Secure-1PSID")) != null && sapisid != null

    /** The `Cookie` header value for a request to [host] over HTTPS; null when no cookie applies. */
    fun headerFor(host: String, now: Instant): String? =
        cookies.filter { it.domainMatches(host) && !it.expired(now) }.takeIf { it.isNotEmpty() }?.joinToString("; ") { "${it.name}=${it.value}" }

    /**
     * A new jar with the cookies of `Set-Cookie` header values applied: replaced by name+domain, removed when the server
     * expires them (Max-Age=0 or a past Expires), so the rotating `__Secure-*PSIDTS` / `*PSIDCC` values keep the session fresh.
     */
    fun withSetCookies(headers: List<String>, requestHost: String, now: Instant): CookieJar {
        var jar = cookies
        for (raw in headers) {
            val parts = raw.split(';').map { it.trim() }
            val (name, value) = parts.firstOrNull()?.split('=', limit = 2)?.takeIf { it.size == 2 } ?: continue
            var domain = requestHost; var path = "/"; var secure = false; var httpOnly = false; var expires: Long? = null
            for (attr in parts.drop(1)) {
                val k = attr.substringBefore('=').lowercase(); val v = attr.substringAfter('=', "")
                when (k) {
                    "domain" -> domain = v.trimStart('.').let { ".$it" }
                    "path" -> path = v.ifEmpty { "/" }
                    "secure" -> secure = true
                    "httponly" -> httpOnly = true
                    "max-age" -> v.toLongOrNull()?.let { expires = if (it <= 0) 1L else now.epochSeconds + it }
                    "expires" -> if (expires == null) parseHttpDate(v)?.let { expires = it }
                }
            }
            jar = jar.filterNot { it.name == name && it.domain.trimStart('.').equals(domain.trimStart('.'), true) && it.path == path }
            if (expires == null || expires!! > now.epochSeconds) jar = jar + Cookie(name, value, domain, path, secure, httpOnly, expires)
        }
        return CookieJar(jar)
    }

    /** Netscape `cookies.txt`, the format browsers' export extensions write and yt-dlp reads. */
    fun toNetscape(): String = buildString {
        appendLine("# Netscape HTTP Cookie File")
        for (c in cookies) {
            val domain = (if (c.httpOnly) "#HttpOnly_" else "") + c.domain
            appendLine(listOf(domain, if (c.domain.startsWith(".")) "TRUE" else "FALSE", c.path, if (c.secure) "TRUE" else "FALSE", c.expiresEpochSeconds ?: 0L, c.name, c.value).joinToString("\t"))
        }
    }

    override fun toString(): String = "CookieJar(${cookies.size} cookies: ${cookies.joinToString { it.name }})"

    companion object {
        /** Parses a Netscape file. Lines that are not cookies are skipped; `#HttpOnly_` prefixes are honoured. */
        fun parseNetscape(text: String): CookieJar = CookieJar(
            text.lineSequence().mapNotNull { raw ->
                val line = raw.trimEnd('\r')
                if (line.isBlank()) return@mapNotNull null
                val httpOnly = line.startsWith("#HttpOnly_")
                if (line.startsWith("#") && !httpOnly) return@mapNotNull null
                val f = line.removePrefix("#HttpOnly_").split('\t')
                if (f.size < 7) return@mapNotNull null
                Cookie(f[5], f[6], f[0], f[2], f[3].equals("TRUE", true), httpOnly, f[4].toLongOrNull()?.takeIf { it > 0 })
            }.toList(),
        )

        private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

        /** `Wed, 21 Oct 2026 07:28:00 GMT` -> epoch seconds; null if it is not in that shape. */
        internal fun parseHttpDate(s: String): Long? {
            val m = Regex("""(?:\w+,\s*)?(\d{1,2})[ -](\w{3})\w*[ -](\d{2,4})\s+(\d{2}):(\d{2}):(\d{2})""").find(s) ?: return null
            val day = m.groupValues[1].toInt(); val mon = months.indexOf(m.groupValues[2].lowercase()).takeIf { it >= 0 }?.plus(1) ?: return null
            var year = m.groupValues[3].toInt(); if (year < 100) year += 2000
            // days from civil (Howard Hinnant)
            val y = if (mon <= 2) year - 1 else year
            val era = y.floorDiv(400); val yoe = y - era * 400
            val doy = (153 * (mon + (if (mon > 2) -3 else 9)) + 2) / 5 + day - 1
            val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
            val days = era.toLong() * 146097 + doe - 719468
            return days * 86400 + m.groupValues[4].toInt() * 3600 + m.groupValues[5].toInt() * 60 + m.groupValues[6].toInt()
        }
    }
}

/** What an authenticated request needs from the stored credentials, computed per request so the hash is always fresh. */
object SapisidHash {
    /** `SAPISIDHASH <ts>_<sha1("<ts> <sapisid> <origin>")>` as Google's web clients send it. */
    fun authorization(sapisid: String, origin: String, nowEpochSeconds: Long): String =
        "SAPISIDHASH ${nowEpochSeconds}_${Sha1.hex("$nowEpochSeconds $sapisid $origin")}"
}
