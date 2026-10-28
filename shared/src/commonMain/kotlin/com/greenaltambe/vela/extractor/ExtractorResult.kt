package com.greenaltambe.vela.extractor

enum class FailureReason {
    BLOCKED, SIGN_IN_REQUIRED, UNPLAYABLE, RATE_LIMITED, NETWORK, PARSE_FAILURE,

    /** YouTube/Google asked for a captcha ("unusual traffic", /sorry). Distinct from BLOCKED: solving it in a browser can lift it. */
    CAPTCHA,

    /** The tier itself can't run (e.g. its binary is gone); says nothing about the video, IP or connection. */
    TIER_UNAVAILABLE;

    /** Reasons that say something about the tier/IP rather than the video or the connection. */
    val isTierHealthIssue: Boolean get() = this == BLOCKED || this == SIGN_IN_REQUIRED || this == RATE_LIMITED || this == CAPTCHA
}

sealed class ExtractorError(val reason: FailureReason, val message: String, val cause: Throwable? = null) {
    class Blocked(message: String = "Blocked", cause: Throwable? = null) : ExtractorError(FailureReason.BLOCKED, message, cause)
    /** [url] is the challenge page when the source gave one (NewPipe does), so the app can open it. */
    class Captcha(message: String = "Captcha required", val url: String? = null, cause: Throwable? = null) : ExtractorError(FailureReason.CAPTCHA, message, cause)
    class SignInRequired(message: String = "Sign in required", cause: Throwable? = null) : ExtractorError(FailureReason.SIGN_IN_REQUIRED, message, cause)
    class Unplayable(message: String = "Unplayable", cause: Throwable? = null) : ExtractorError(FailureReason.UNPLAYABLE, message, cause)
    class RateLimited(message: String = "Rate limited", cause: Throwable? = null) : ExtractorError(FailureReason.RATE_LIMITED, message, cause)
    class Network(message: String = "Network error", cause: Throwable? = null) : ExtractorError(FailureReason.NETWORK, message, cause)
    class TierUnavailable(message: String = "Tier unavailable", cause: Throwable? = null) : ExtractorError(FailureReason.TIER_UNAVAILABLE, message, cause)
    class ParseFailure(message: String = "Parse failure", cause: Throwable? = null) : ExtractorError(FailureReason.PARSE_FAILURE, message, cause)
}

/** What the account did in a failed attempt, so the player can name the right action. */
enum class AccountStatus { NO_ACCOUNT_TIER, SIGNED_OUT, PAUSED, DISABLED, TRIED }

/** One tier's part of a failed attempt. [tier] is a person-readable name ("NewPipe", "alt client", "yt-dlp", "account"). */
data class TierFailure(val tier: String, val reason: FailureReason, val detail: String)

/**
 * Every tier failed and at least one said sign-in / age: carries each tier's reason and what the account did, so the
 * player can say "age-restricted: anonymous tiers failed (...); account paused until 09:25 UTC" and offer the right action.
 * [reason] is SIGN_IN_REQUIRED so existing handling keeps working.
 */
class TiersFailed(
    message: String,
    val tiers: List<TierFailure>,
    val account: AccountStatus,
    val accountDetail: String?,
    val ageRestricted: Boolean,
    val hints: List<String>,
) : ExtractorError(FailureReason.SIGN_IN_REQUIRED, message)

sealed interface ExtractorResult<out T> {
    data class Success<T>(val value: T) : ExtractorResult<T>
    data class Failure(val error: ExtractorError) : ExtractorResult<Nothing>
}
