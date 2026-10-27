package com.greenaltambe.vela.extractor.chain

import com.greenaltambe.vela.core.settings.AccountFallbackMode
import com.greenaltambe.vela.auth.Redaction
import com.greenaltambe.vela.extractor.*
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Tries [tiers] in order and returns the first success. Tiers that recently failed for a tier-health reason
 * are skipped while a remembered route to a working tier is live. Every attempt lands in [log].
 */
class FallbackChain(
    private val tiers: List<ExtractorTier>,
    private val accountMode: () -> AccountFallbackMode,
    private val log: DiagnosticsLog,
    private val clock: Clock = Clock.System,
    memoryTtl: Duration = 15.minutes,
    /**
     * The active account as the chain may name it (a label such as `google:ab12cd`, never a name or e-mail), or null when
     * nobody is signed in. Left at its default (null provider) the chain is not account-aware and tries every tier.
     * When it is set, a change of label clears the tier memory (what worked for one account says nothing about another).
     */
    private val signedInAccount: (() -> String?)? = null,
) : Extractor {
    private val memory = TierMemory(clock, memoryTtl)
    private var memoryOwner: String? = signedInAccount?.invoke()

    /** Only true if every tier honours it, so the UI never offers a choice some tier would silently ignore. */
    override val capabilities = ExtractorCapabilities(
        supportsCommentSort = tiers.isNotEmpty() && tiers.all { it.capabilities.supportsCommentSort },
    )

    override suspend fun search(query: String, continuation: String?) =
        run("search($query${page(continuation)})") { it.search(query, continuation) }
    override suspend fun videoInfo(videoId: String) = run("videoInfo($videoId)") { it.videoInfo(videoId) }
    override suspend fun streams(videoId: String) = run("streams($videoId)") { it.streams(videoId) }
    override suspend fun channel(channelId: String) = run("channel($channelId)") { it.channel(channelId) }
    override suspend fun channelVideos(channelId: String, continuation: String?) =
        run("channelVideos($channelId${page(continuation)})") { it.channelVideos(channelId, continuation) }
    override suspend fun comments(videoId: String, sort: CommentSort, continuation: String?) =
        run("comments($videoId${page(continuation)})") { it.comments(videoId, sort, continuation) }
    override suspend fun replies(videoId: String, commentId: String, continuation: String?) =
        run("replies($videoId/$commentId${page(continuation)})") { it.replies(videoId, commentId, continuation) }

    override suspend fun trending(continuation: String?) = run("trending(${page(continuation)})") { it.trending(continuation) }
    override suspend fun related(videoId: String) = run("related($videoId)") { it.related(videoId) }
    override suspend fun channelShorts(channelId: String, continuation: String?) =
        run("channelShorts($channelId${page(continuation)})") { it.channelShorts(channelId, continuation) }
    override suspend fun recentUploads(channelId: String) = run("recentUploads($channelId)") { it.recentUploads(channelId) }

    private fun page(continuation: String?) = if (continuation == null) "" else ", more"

    private suspend fun <T> run(
        subject: String,
        call: suspend (Extractor) -> ExtractorResult<T>,
    ): ExtractorResult<T> {
        val account = signedInAccount?.invoke()
        if (signedInAccount != null && account != memoryOwner) { memory.clear(); memoryOwner = account }
        val candidates = candidateTiers(subject, account)
        if (candidates.isEmpty()) {
            return ExtractorResult.Failure(ExtractorError.Unplayable("No extractor tier available"))
        }
        val available = candidates.mapTo(HashSet()) { it.id }
        val routes = candidates.associate { it.id to memory.routeAround(it.id, available) }
        val routed = candidates.filter { routes.getValue(it.id) == null }
        // If memory would hide everything, ignore it rather than fail without trying.
        val toTry = routed.ifEmpty { candidates }
        for (tier in candidates - toTry.toSet()) {
            val route = routes.getValue(tier.id)!!
            record(subject, tier.id, AttemptOutcome.SKIPPED, route.reason, "Remembered ${route.workedTier} worked after ${route.reason}", Duration.ZERO)
        }

        val failures = mutableListOf<Pair<TierId, ExtractorError>>()
        for (tier in toTry) {
            val start = clock.now()
            val result = try {
                call(tier.extractor)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ExtractorResult.Failure(ExtractorError.ParseFailure(e.message ?: "Unexpected error", e))
            }
            val took = clock.now() - start
            when (result) {
                is ExtractorResult.Success -> {
                    record(subject, tier.id, AttemptOutcome.SUCCESS, null, account?.takeIf { tier.id == TierId.AUTHENTICATED }?.let { "account $it" }, took)
                    failures.filter { (_, e) -> e.reason.isTierHealthIssue }
                        .forEach { (failed, e) -> memory.remember(failed, e.reason, tier.id) }
                    return result
                }
                is ExtractorResult.Failure -> {
                    record(subject, tier.id, AttemptOutcome.FAILED, result.error.reason, account?.takeIf { tier.id == TierId.AUTHENTICATED }?.let { "account $it: " }.orEmpty() + result.error.message, took)
                    memory.forgetWorked(tier.id)
                    failures += tier.id to result.error
                }
            }
        }
        return ExtractorResult.Failure(failures.last().second)
    }

    /** Applies the account fallback setting: drops or promotes the authenticated tier. */
    private fun candidateTiers(subject: String, account: String?): List<ExtractorTier> {
        val usable = if (signedInAccount != null && account == null) {
            tiers.filter { tier ->
                (tier.id != TierId.AUTHENTICATED).also { keep ->
                    if (!keep) record(subject, tier.id, AttemptOutcome.SKIPPED, null, "No signed-in account", Duration.ZERO)
                }
            }
        } else tiers
        return when (accountMode()) {
            AccountFallbackMode.ONLY_ON_FAILURE -> usable
            AccountFallbackMode.ALWAYS ->
                usable.sortedBy { if (it.id == TierId.AUTHENTICATED) 0 else 1 } // stable: rest keep their order
            AccountFallbackMode.NEVER -> usable.filter { tier ->
                (tier.id != TierId.AUTHENTICATED).also { keep ->
                    if (!keep) record(subject, tier.id, AttemptOutcome.SKIPPED, null, "Account fallback is set to never", Duration.ZERO)
                }
            }
        }
    }

    private fun record(
        subject: String,
        tier: TierId,
        outcome: AttemptOutcome,
        reason: FailureReason?,
        detail: String?,
        duration: Duration,
    ) = log.record(TierAttempt(subject, tier, outcome, reason, detail?.let(Redaction::redact), duration, clock.now()))
}
