package com.greenaltambe.vela.extractor

enum class FailureReason {
    BLOCKED, SIGN_IN_REQUIRED, UNPLAYABLE, RATE_LIMITED, NETWORK, PARSE_FAILURE,

    /** The tier itself can't run (e.g. its binary is gone); says nothing about the video, IP or connection. */
    TIER_UNAVAILABLE;

    /** Reasons that say something about the tier/IP rather than the video or the connection. */
    val isTierHealthIssue: Boolean get() = this == BLOCKED || this == SIGN_IN_REQUIRED || this == RATE_LIMITED
}

sealed class ExtractorError(val reason: FailureReason, val message: String, val cause: Throwable? = null) {
    class Blocked(message: String = "Blocked", cause: Throwable? = null) : ExtractorError(FailureReason.BLOCKED, message, cause)
    class SignInRequired(message: String = "Sign in required", cause: Throwable? = null) : ExtractorError(FailureReason.SIGN_IN_REQUIRED, message, cause)
    class Unplayable(message: String = "Unplayable", cause: Throwable? = null) : ExtractorError(FailureReason.UNPLAYABLE, message, cause)
    class RateLimited(message: String = "Rate limited", cause: Throwable? = null) : ExtractorError(FailureReason.RATE_LIMITED, message, cause)
    class Network(message: String = "Network error", cause: Throwable? = null) : ExtractorError(FailureReason.NETWORK, message, cause)
    class TierUnavailable(message: String = "Tier unavailable", cause: Throwable? = null) : ExtractorError(FailureReason.TIER_UNAVAILABLE, message, cause)
    class ParseFailure(message: String = "Parse failure", cause: Throwable? = null) : ExtractorError(FailureReason.PARSE_FAILURE, message, cause)
}

sealed interface ExtractorResult<out T> {
    data class Success<T>(val value: T) : ExtractorResult<T>
    data class Failure(val error: ExtractorError) : ExtractorResult<Nothing>
}
