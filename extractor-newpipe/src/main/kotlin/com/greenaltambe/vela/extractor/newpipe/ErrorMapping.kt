package com.greenaltambe.vela.extractor.newpipe

import com.greenaltambe.vela.extractor.ExtractorError
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.exceptions.FoundAdException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import java.io.IOException

/**
 * Maps whatever NewPipe or the network threw onto our error taxonomy. The original exception stays attached as
 * the cause. Callers rethrow CancellationException before getting here.
 *
 * NewPipe sometimes wraps the interesting exception, so the cause chain is searched for a 403/429 first.
 */
fun Throwable.toExtractorError(): ExtractorError {
    generateSequence(this) { it.cause }.take(MAX_CAUSE_DEPTH).filterIsInstance<HttpStatusException>().firstOrNull()?.let {
        val text = it.message ?: "HTTP ${it.code}"
        return if (it.code == 403) ExtractorError.Blocked(text, this) else ExtractorError.RateLimited(text, this)
    }
    val message = message.orEmpty()
    return when (this) {
        // Subclass of ParsingException, so it has to be checked before the generic cases below.
        is SignInConfirmNotBotException -> ExtractorError.SignInRequired(message.ifBlank { "Sign in to confirm you're not a bot" }, this)
        is ReCaptchaException -> ExtractorError.Blocked(message.ifBlank { "reCAPTCHA required" }, this)
        is AgeRestrictedContentException -> ExtractorError.Unplayable(message.ifBlank { "Age-restricted content" }, this)
        is ContentNotAvailableException ->
            if (message.mentionsSignIn()) ExtractorError.SignInRequired(message, this)
            else ExtractorError.Unplayable(message.ifBlank { "Content not available" }, this)
        is ContentNotSupportedException -> ExtractorError.Unplayable(message.ifBlank { "Content not supported" }, this)
        is FoundAdException -> ExtractorError.Unplayable(message.ifBlank { "Advertisement" }, this)
        is IOException -> ExtractorError.Network(message.ifBlank { javaClass.simpleName }, this)
        else ->
            if (message.mentionsSignIn()) ExtractorError.SignInRequired(message, this)
            else if (message.mentionsUnavailable()) ExtractorError.Unplayable(message, this)
            else ExtractorError.ParseFailure(message.ifBlank { javaClass.simpleName }, this)
    }
}

private const val MAX_CAUSE_DEPTH = 8

private fun String.mentionsSignIn() =
    contains("sign in to confirm", ignoreCase = true) || contains("confirm you're not a bot", ignoreCase = true) ||
        contains("confirm you’re not a bot", ignoreCase = true)

/**
 * Messages that unambiguously say the video isn't there. Deliberately narrow: NewPipe's "JSON response is too short"
 * also shows up for non-existent ids, but a truncated body would say the same, so that one stays a ParseFailure.
 */
private val unavailablePhrases = listOf(
    "video unavailable", "video is unavailable", "video is not available", "video is private", "private video",
    "video does not exist", "video doesn't exist", "video has been removed", "video no longer available",
    "this channel doesn't exist", "this channel does not exist", "has been terminated",
)

private fun String.mentionsUnavailable() = unavailablePhrases.any { contains(it, ignoreCase = true) }
