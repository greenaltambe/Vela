package com.greenaltambe.vela.extractor.ytdlp

import com.greenaltambe.vela.extractor.ExtractorError

/**
 * Maps a failed yt-dlp run to our error taxonomy from its stderr. Order matters: age and availability messages
 * often also say "sign in", and a 403 can appear inside a "Unable to download webpage" network-sounding line.
 */
fun mapYtDlpFailure(exitCode: Int, stderr: String): ExtractorError {
    val message = stderr.lines().lastOrNull { it.contains("ERROR", ignoreCase = true) }?.trim()
        ?: stderr.lines().lastOrNull { it.isNotBlank() }?.trim()
        ?: "yt-dlp exited with code $exitCode"
    // yt-dlp writes both ' and ’ in "you're not a bot"; normalised so a needle only has to be written once.
    val text = stderr.replace('\u2019', '\'').replace('\u2018', '\'')
    fun has(vararg needles: String) = needles.any { text.contains(it, ignoreCase = true) }

    return when {
        has("confirm your age", "age-restricted", "age restricted", "inappropriate for some users") ->
            ExtractorError.Unplayable(message)
        has("private video", "this video is private") -> ExtractorError.Unplayable(message)
        has("video unavailable", "video is unavailable", "video is not available", "has been removed",
            "account associated with this video has been terminated", "members-only", "available in your country",
            "blocked it in your country", "is not a valid url", "incomplete youtube id", "premieres in",
            "this channel does not exist", "this video is unavailable",
            "live event will begin") -> ExtractorError.Unplayable(message)
        has("sign in to confirm") -> ExtractorError.SignInRequired(message)
        has("http error 429", "too many requests") -> ExtractorError.RateLimited(message)
        has("http error 403") -> ExtractorError.Blocked(message)
        has("temporary failure in name resolution", "name or service not known", "getaddrinfo failed",
            "network is unreachable", "connection refused", "connection reset", "timed out", "unable to download",
            "urlopen error", "failed to resolve", "transporterror", "remote end closed") -> ExtractorError.Network(message)
        else -> ExtractorError.ParseFailure(message)
    }
}
