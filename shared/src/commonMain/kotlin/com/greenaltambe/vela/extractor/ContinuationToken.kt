package com.greenaltambe.vela.extractor

/** Continuation tokens are stamped with the tier family that issued them: `<tier>:<payload>`. */
object ContinuationToken {
    const val FOREIGN_MESSAGE = "continuation from a different tier"

    fun wrap(tier: String, payload: String) = "$tier:$payload"

    /** The payload if [token] was issued by [tier]; null for anything else (another tier, or garbage). */
    fun unwrap(tier: String, token: String): String? =
        token.takeIf { it.startsWith("$tier:") }?.removePrefix("$tier:")

    fun foreign() = ExtractorResult.Failure(ExtractorError.Unplayable(FOREIGN_MESSAGE))
}
