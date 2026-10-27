package com.greenaltambe.vela.extractor.ytdlp

import com.greenaltambe.vela.extractor.ExtractorError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Only [realStderrFromYtDlp] uses captured output. Every other case below is yt-dlp's wording as remembered (private,
 * age gate, bot wall, 429/403, network): they could not be reproduced when this was verified.
 */
class YtDlpErrorsTest {
    private inline fun <reified E : ExtractorError> assertMaps(stderr: String): E {
        val e = mapYtDlpFailure(1, stderr)
        assertIs<E>(e, "for: $stderr")
        return e
    }

    @Test fun signInToConfirm() {
        assertMaps<ExtractorError.SignInRequired>("ERROR: [youtube] abc: Sign in to confirm you’re not a bot. Use --cookies-from-browser")
        assertMaps<ExtractorError.SignInRequired>("ERROR: [youtube] abc: Sign in to confirm you're not a bot")
    }

    @Test fun rateLimited() {
        assertMaps<ExtractorError.RateLimited>("ERROR: unable to download video data: HTTP Error 429: Too Many Requests")
        assertMaps<ExtractorError.RateLimited>("ERROR: Too Many Requests")
    }

    @Test fun blocked() {
        assertMaps<ExtractorError.Blocked>("ERROR: unable to download video data: HTTP Error 403: Forbidden")
    }

    @Test fun http403InsideAnUnableToDownloadLineIsStillBlockedNotNetwork() {
        assertMaps<ExtractorError.Blocked>("ERROR: [youtube] abc: Unable to download webpage: HTTP Error 403: Forbidden")
    }

    @Test fun unavailableVideos() {
        listOf(
            "ERROR: [youtube] abc: Video unavailable",
            "ERROR: [youtube] abc: Video unavailable. This video is not available",
            "ERROR: [youtube] abc: Private video. Sign in if you've been granted access to this video",
            "ERROR: [youtube] abc: This video has been removed by the uploader",
            "ERROR: [youtube] abc: The uploader has not made this video available in your country",
            "ERROR: [youtube] abc: Join this channel to get access to members-only content like this video",
            "ERROR: [youtube] abc: Premieres in 3 hours",
        ).forEach { assertMaps<ExtractorError.Unplayable>(it) }
    }

    @Test fun ageRestrictionIsUnplayableEvenThoughItSaysSignIn() {
        assertMaps<ExtractorError.Unplayable>("ERROR: [youtube] abc: Sign in to confirm your age. This video may be inappropriate for some users.")
        assertMaps<ExtractorError.Unplayable>("ERROR: [youtube] abc: This video is age-restricted")
    }

    @Test fun networkProblems() {
        listOf(
            "ERROR: [youtube] abc: Unable to download webpage: <urlopen error [Errno -3] Temporary failure in name resolution>",
            "ERROR: Unable to download API page: [Errno 111] Connection refused",
            "ERROR: unable to download video data: The read operation timed out",
            "ERROR: <urlopen error [Errno 101] Network is unreachable>",
        ).forEach { assertMaps<ExtractorError.Network>(it) }
    }

    @Test fun anythingElseIsAParseFailure() {
        assertMaps<ExtractorError.ParseFailure>("ERROR: Unsupported thing happened")
        assertMaps<ExtractorError.ParseFailure>("")
    }

    // Messages below are verbatim stderr from yt-dlp 2026.10.26 (see fixtures/stderr_*.txt).
    @Test fun realStderrFromYtDlp() {
        fun stderr(name: String) = Fixtures.text(name)
        assertMaps<ExtractorError.Unplayable>(stderr("stderr_unavailable.txt"))
        assertMaps<ExtractorError.Unplayable>(stderr("stderr_terminated.txt"))
        assertMaps<ExtractorError.Unplayable>(stderr("stderr_truncated_id.txt"))
        assertMaps<ExtractorError.Unplayable>(stderr("stderr_not_a_url.txt"))
        assertMaps<ExtractorError.Unplayable>(stderr("stderr_channel_missing.txt"))
        assertEquals(
            "ERROR: [youtube] aaaaaaaaaaa: This video is unavailable",
            mapYtDlpFailure(1, stderr("stderr_unavailable.txt")).message,
        )
    }

    @Test fun theMessageIsTheLastErrorLine() {
        val e = mapYtDlpFailure(1, "WARNING: noise\nERROR: first\nERROR: [youtube] abc: Video unavailable\n")
        assertEquals("ERROR: [youtube] abc: Video unavailable", e.message)
        assertEquals("yt-dlp exited with code 7", mapYtDlpFailure(7, "").message)
    }
}
