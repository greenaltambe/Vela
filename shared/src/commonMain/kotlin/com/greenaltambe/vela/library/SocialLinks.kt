package com.greenaltambe.vela.library

/** A social network a description links to: shown as a small round badge (its colour and a glyph) and the account name, like YouTube's own chips. */
enum class Social(val glyph: String, val argb: Long, val label: String) {
    TIKTOK("♪", 0xFF111111, "TikTok"),
    INSTAGRAM("◎", 0xFFD6249F, "Instagram"),
    FACEBOOK("f", 0xFF1877F2, "Facebook"),
    X("X", 0xFF111111, "X"),
    YOUTUBE("▶", 0xFFE62117, "YouTube"),
    TWITCH("t", 0xFF9146FF, "Twitch"),
    DISCORD("d", 0xFF5865F2, "Discord"),
    REDDIT("r", 0xFFFF4500, "Reddit"),
    GITHUB("g", 0xFF24292F, "GitHub"),
    LINKEDIN("in", 0xFF0A66C2, "LinkedIn"),
    SPOTIFY("♫", 0xFF1DB954, "Spotify"),
    PATREON("p", 0xFFF96854, "Patreon"),
    THREADS("@", 0xFF111111, "Threads"),
    BLUESKY("b", 0xFF1185FE, "Bluesky"),
}

/** The glyph colour on every badge (the badges are dark enough for white). */
const val SOCIAL_BADGE_TEXT: Long = 0xFFFFFFFF

private val hosts = mapOf(
    "tiktok.com" to Social.TIKTOK, "instagram.com" to Social.INSTAGRAM, "facebook.com" to Social.FACEBOOK, "fb.com" to Social.FACEBOOK, "fb.me" to Social.FACEBOOK,
    "twitter.com" to Social.X, "x.com" to Social.X, "youtube.com" to Social.YOUTUBE, "youtu.be" to Social.YOUTUBE, "twitch.tv" to Social.TWITCH,
    "discord.gg" to Social.DISCORD, "discord.com" to Social.DISCORD, "reddit.com" to Social.REDDIT, "github.com" to Social.GITHUB, "linkedin.com" to Social.LINKEDIN,
    "open.spotify.com" to Social.SPOTIFY, "spotify.com" to Social.SPOTIFY, "patreon.com" to Social.PATREON, "threads.net" to Social.THREADS, "bsky.app" to Social.BLUESKY,
)

/** The network a link's address belongs to, or null for any other site. Subdomains count ("m.facebook.com"). */
fun socialOf(url: String): Social? {
    val host = url.substringAfter("://", "").substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':').lowercase()
    if (host.isEmpty()) return null
    return hosts.entries.firstOrNull { (h, _) -> host == h || host.endsWith(".$h") }?.value
}

/**
 * The account a social link names, as the chip shows it: the part of the label after "Network: " when it has one ("TikTok: peacocktv" -> "peacocktv"),
 * otherwise the last piece of the address's path without a leading "@".
 */
fun socialHandle(label: String, url: String): String {
    val afterColon = label.substringAfter(": ", "").trim()
    if (afterColon.isNotEmpty() && !label.startsWith("http")) return afterColon.removePrefix("@")
    val path = url.substringAfter("://", url).substringAfter('/', "").substringBefore('?').substringBefore('#').trimEnd('/')
    val last = path.substringAfterLast('/').removePrefix("@")
    return last.ifEmpty { url.substringAfter("://", url).substringBefore('/').removePrefix("www.") }
}
