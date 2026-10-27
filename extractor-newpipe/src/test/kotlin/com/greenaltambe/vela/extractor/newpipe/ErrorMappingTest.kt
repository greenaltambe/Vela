package com.greenaltambe.vela.extractor.newpipe

import com.greenaltambe.vela.extractor.ExtractorError
import org.schabi.newpipe.extractor.exceptions.*
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame

class ErrorMappingTest {
    private inline fun <reified E : ExtractorError> assertMapsTo(t: Throwable): E {
        val error = t.toExtractorError()
        assertIs<E>(error)
        assertSame(t, error.cause, "the original exception must stay attached")
        return error
    }

    @Test fun botCheckNeedsSignIn() { assertMapsTo<ExtractorError.SignInRequired>(SignInConfirmNotBotException("Sign in to confirm you're not a bot")) }

    @Test fun reCaptchaIsBlocked() { assertMapsTo<ExtractorError.Blocked>(ReCaptchaException("reCaptcha Challenge requested", "https://youtube.com")) }

    @Test fun signInMessageOnAnotherExceptionStillNeedsSignIn() {
        assertMapsTo<ExtractorError.SignInRequired>(ParsingException("Sign in to confirm you’re not a bot"))
        assertMapsTo<ExtractorError.SignInRequired>(ContentNotAvailableException("Sign in to confirm your age or something"))
    }

    @Test fun unavailableContentIsUnplayable() {
        assertMapsTo<ExtractorError.Unplayable>(AgeRestrictedContentException("age"))
        assertMapsTo<ExtractorError.Unplayable>(PrivateContentException("private"))
        assertMapsTo<ExtractorError.Unplayable>(GeographicRestrictionException("geo"))
        assertMapsTo<ExtractorError.Unplayable>(PaidContentException("paid"))
        assertMapsTo<ExtractorError.Unplayable>(AccountTerminatedException("gone"))
        assertMapsTo<ExtractorError.Unplayable>(ContentNotAvailableException("This video is unavailable"))
        assertMapsTo<ExtractorError.Unplayable>(ContentNotSupportedException("not supported"))
    }

    @Test fun http403IsBlocked() { assertMapsTo<ExtractorError.Blocked>(HttpStatusException(403, "https://www.youtube.com/youtubei/v1/player")) }

    @Test fun http403WrappedByNewPipeIsStillBlocked() {
        assertIs<ExtractorError.Blocked>(ExtractionException("Could not fetch", HttpStatusException(403, "u")).toExtractorError())
    }

    @Test fun unavailableMessagesAreUnplayableEvenOnGenericExceptions() {
        listOf(
            "Video unavailable", "This video is unavailable", "The video is private", "This video does not exist",
            "This video has been removed by the uploader", "This channel doesn't exist.",
        ).forEach { assertMapsTo<ExtractorError.Unplayable>(ParsingException(it)) }
    }

    @Test fun truncatedResponseStaysAParseFailure() {
        // Also what NewPipe says for a non-existent id, but a cut-off body looks the same, so it is not "unplayable".
        assertMapsTo<ExtractorError.ParseFailure>(ParsingException("JSON response is too short"))
    }

    @Test fun signInBeatsUnavailable() {
        assertMapsTo<ExtractorError.SignInRequired>(ParsingException("Video unavailable. Sign in to confirm you're not a bot"))
    }

    @Test fun http429IsRateLimited() { assertMapsTo<ExtractorError.RateLimited>(HttpStatusException(429, "https://youtube.com")) }

    @Test fun http429WrappedByNewPipeIsStillRateLimited() {
        val error = ExtractionException("Could not fetch", HttpStatusException(429, "u")).toExtractorError()
        assertIs<ExtractorError.RateLimited>(error)
    }

    @Test fun ioFailuresAreNetwork() {
        assertMapsTo<ExtractorError.Network>(IOException("boom"))
        assertMapsTo<ExtractorError.Network>(SocketTimeoutException("timeout"))
        assertMapsTo<ExtractorError.Network>(UnknownHostException("www.youtube.com"))
    }

    @Test fun parsingAndUnexpectedErrorsAreParseFailures() {
        assertMapsTo<ExtractorError.ParseFailure>(ParsingException("no title"))
        assertMapsTo<ExtractorError.ParseFailure>(ExtractionException("generic"))
        assertMapsTo<ExtractorError.ParseFailure>(IllegalStateException("surprise"))
        assertMapsTo<ExtractorError.ParseFailure>(NullPointerException())
    }
}
