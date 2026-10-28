package com.greenaltambe.vela.ui.screens

import com.greenaltambe.vela.extractor.FailureReason
import com.greenaltambe.vela.extractor.chain.AgeText
import com.greenaltambe.vela.extractor.chain.AttemptOutcome
import com.greenaltambe.vela.extractor.chain.TierAttempt
import com.greenaltambe.vela.extractor.chain.TierId
import kotlin.time.Instant

/**
 * What to tell the viewer while the player is still resolving [videoId], from the tier attempts recorded since resolving began
 * ([since]). Attempts are logged when a tier finishes, so a failure of the last tried tier tells which one is running now.
 * Null while the first tier is still working (no note needed for a normal 1-2 s resolve).
 */
fun resolveNote(attempts: List<TierAttempt>, videoId: String, since: Instant, accountSignedIn: Boolean, accountMayServe: Boolean): String? {
    val failed = attempts.filter { it.subject == "streams($videoId)" && it.at >= since && it.outcome == AttemptOutcome.FAILED }
    if (failed.isEmpty()) return null
    val last = failed.last()
    val ageOrSignIn = failed.any { it.reason == FailureReason.SIGN_IN_REQUIRED || AgeText.matches(it.detail.orEmpty()) }
    return when {
        last.tier == TierId.AUTHENTICATED -> null
        last.tier == TierId.YT_DLP && accountSignedIn && accountMayServe -> "Using your account…"
        ageOrSignIn && TierId.YT_DLP !in failed.map { it.tier } -> "Age-restricted or sign-in video: trying a slower source (can take up to 30 s)…"
        else -> "Trying the next source…"
    }
}
