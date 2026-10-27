package com.greenaltambe.vela.auth

/**
 * Removes credentials and personal data from any text before it is logged, shown in Diagnostics, kept in a
 * [com.greenaltambe.vela.extractor.chain.TierAttempt] or put in a crash message. Everything that logs goes through [redact].
 *
 * What it removes: `Cookie` / `Set-Cookie` / `Authorization` / `X-Goog-*` header values; any `name=value` for a cookie name
 * Google uses for sessions; `SAPISIDHASH` and bearer tokens; JSON fields that identify a session (`visitorData`, `datasyncId`,
 * `delegatedSessionId`, `onBehalfOfUser`, `refresh_token`, `access_token`); and e-mail addresses (kept as `a***@domain`).
 */
object Redaction {
    private val sessionCookies = listOf(
        "SID", "HSID", "SSID", "APISID", "SAPISID", "LOGIN_INFO", "SIDCC", "ACCOUNT_CHOOSER", "OSID", "SMSV", "GAPS", "LSID",
        "__Secure-1PSID", "__Secure-3PSID", "__Secure-1PAPISID", "__Secure-3PAPISID", "__Secure-1PSIDTS", "__Secure-3PSIDTS",
        "__Secure-1PSIDCC", "__Secure-3PSIDCC", "__Secure-ROLLOUT_TOKEN", "__Secure-YNID", "__Host-GAPS", "VISITOR_INFO1_LIVE",
        "VISITOR_PRIVACY_METADATA", "YSC",
    )

    private val headerLine = Regex("""(?im)^(\s*(?:cookie|set-cookie|authorization|proxy-authorization|x-goog-[a-z-]+|x-youtube-[a-z-]+)\s*[:=]\s*).+$""")
    private val inlineHeader = Regex("""(?i)\b(cookie|set-cookie|authorization)(["']?\s*[:=]\s*["']?)[^\r\n"']+""")
    private val cookiePair: Regex = Regex("""(?<![A-Za-z0-9_-])(${sessionCookies.joinToString("|") { Regex.escape(it) }}|ST-[A-Za-z0-9]+)(\s*=\s*)[^;\s,"'&]+""")
    private val sapisidHash = Regex("""(?i)\b(SAPISIDHASH|SAPISID1PHASH|SAPISID3PHASH)\s+[0-9]+_[0-9a-f]{20,}""")
    private val bearer = Regex("""(?i)\b(bearer)\s+[A-Za-z0-9._~+/=-]{12,}""")
    private val jsonSecret = Regex("""(?i)("(?:visitorData|visitor_data|datasyncId|delegatedSessionId|onBehalfOfUser|refresh_token|access_token|id_token|device_code|user_code|clientSecret|apiKey|poToken|remoteHost)"\s*:\s*")[^"]*(")""")
    private val queryParam = Regex("""(?i)([?&](?:key|token|access_token|refresh_token|sapisid|visitorData|pot|sig|lsig)=)[^&\s"']+""")
    private val email = Regex("""([A-Za-z0-9._%+-])[A-Za-z0-9._%+-]*@([A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+)""")

    const val MARK = "[redacted]"

    fun redact(text: String): String {
        if (text.isEmpty()) return text
        var s = text
        s = headerLine.replace(s) { it.groupValues[1] + MARK }
        s = inlineHeader.replace(s) { it.groupValues[1] + it.groupValues[2] + MARK }
        s = sapisidHash.replace(s) { "${it.groupValues[1]} $MARK" }
        s = bearer.replace(s) { "${it.groupValues[1]} $MARK" }
        s = cookiePair.replace(s) { it.groupValues[1] + it.groupValues[2] + MARK }
        s = jsonSecret.replace(s) { it.groupValues[1] + MARK + it.groupValues[2] }
        s = queryParam.replace(s) { it.groupValues[1] + MARK }
        s = email.replace(s) { "${it.groupValues[1]}***@${it.groupValues[2]}" }
        return s
    }

    /** The e-mail as shown to the owner in the account card: `a***@gmail.com`. Never log the real one. */
    fun maskEmail(address: String?): String? = address?.let { email.replace(it) { m -> "${m.groupValues[1]}***@${m.groupValues[2]}" } }

    /**
     * True if [text] still holds something [redact] would remove. Redaction is idempotent (already-redacted text is left
     * alone), so this is how captured fixtures and log lines are checked: it must be false for anything we keep.
     */
    fun looksSecret(text: String): Boolean = redact(text) != text
}
