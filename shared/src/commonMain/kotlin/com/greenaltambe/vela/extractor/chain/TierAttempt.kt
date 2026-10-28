package com.greenaltambe.vela.extractor.chain

import com.greenaltambe.vela.extractor.Extractor
import com.greenaltambe.vela.extractor.FailureReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Duration
import kotlin.time.Instant

enum class TierId { ANONYMOUS, AUTHENTICATED, ALT_CLIENT, YT_DLP }

/** [version] names the code behind the tier for Diagnostics, e.g. "NewPipeExtractor v0.26.5" or "yt-dlp 2026.10.26 (/home/u/.local/bin/yt-dlp)". */
class ExtractorTier(val id: TierId, val extractor: Extractor, val version: String? = null) {
    val capabilities get() = extractor.capabilities
}

/** [NOTE] is an informational line (e.g. a setup warning) rather than an attempt at a request. */
enum class AttemptOutcome { SUCCESS, FAILED, SKIPPED, NOTE }

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

/** Where log lines go besides memory (a rotating file). Lines arrive already redacted. */
fun interface LogLineSink { fun write(line: String) }

class DiagnosticsLog(private val capacity: Int = 200, private val sink: LogLineSink? = null) {
    private val _attempts = MutableStateFlow<List<TierAttempt>>(emptyList())
    val attempts: StateFlow<List<TierAttempt>> = _attempts

    fun record(attempt: TierAttempt) {
        _attempts.update { (it + attempt).takeLast(capacity) }
        sink?.let { runCatching { it.write(attempt.logLine()) } }
    }
}

/** One redacted line: when, tier, outcome, what was asked, why (never headers, bodies or cookies; the redactor also strips anything that looks like a credential). */
fun TierAttempt.logLine(): String = com.greenaltambe.vela.auth.Redaction.redact(
    "$at tier=${tier.name} ${outcome.name} $subject${reason?.let { " reason=${it.name}" } ?: ""} ${duration.inWholeMilliseconds}ms${detail?.let { " | ${it.replace('\n', ' ')}" } ?: ""}",
)
