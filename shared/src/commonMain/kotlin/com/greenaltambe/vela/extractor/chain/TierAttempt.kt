package com.greenaltambe.vela.extractor.chain

import com.greenaltambe.vela.extractor.Extractor
import com.greenaltambe.vela.extractor.FailureReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Duration
import kotlin.time.Instant

enum class TierId { ANONYMOUS, AUTHENTICATED, ALT_CLIENT, YT_DLP }

class ExtractorTier(val id: TierId, val extractor: Extractor)

enum class AttemptOutcome { SUCCESS, FAILED, SKIPPED }

/** One line of the diagnostics screen. [reason] is set for failures; [detail] explains skips and failures. */
data class TierAttempt(
    val subject: String,
    val tier: TierId,
    val outcome: AttemptOutcome,
    val reason: FailureReason?,
    val detail: String?,
    val duration: Duration,
    val at: Instant,
)

class DiagnosticsLog(private val capacity: Int = 200) {
    private val _attempts = MutableStateFlow<List<TierAttempt>>(emptyList())
    val attempts: StateFlow<List<TierAttempt>> = _attempts

    fun record(attempt: TierAttempt) = _attempts.update { (it + attempt).takeLast(capacity) }
}
