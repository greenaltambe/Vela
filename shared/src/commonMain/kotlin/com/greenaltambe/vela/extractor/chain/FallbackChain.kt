package com.greenaltambe.vela.extractor.chain

import com.greenaltambe.vela.core.settings.AccountFallbackMode
import com.greenaltambe.vela.auth.Redaction
import com.greenaltambe.vela.extractor.*
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** What a call is for, so the account can be kept to the operations the user allowed. Search, comments, trending, related and feeds are never account operations. */
enum class AccountOp { PLAYBACK, DETAILS, CHANNELS, NEVER }

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
    /**
     * The order tiers are tried in, as data (see [TierOrder]). Null keeps the order of [tiers] as given. Whatever the list says,
     * the account tier is never moved before an anonymous tier unless the mode is ALWAYS.
     */
    private val tierOrder: (() -> List<TierId>)? = null,
    /** Why the active account must not be used right now ("account cooling down until 14:05 UTC"), or null. */
    private val accountPaused: () -> String? = { null },
    /** Which operations the account may serve (policy settings). Default: all, so a chain built without policy keeps its old behaviour. */
    private val accountMayServe: (AccountOp) -> Boolean = { true },
    /** Environment facts worth showing when a tier failed or is missing ("yt-dlp 2026.08.19 is too old", "no JavaScript runtime"). */
    private val tierHint: (TierId) -> String? = { null },
    /** The "allow age-restricted videos" setting. When off, the first tier that reports an age restriction ends the request: the account and yt-dlp are never asked. */
    private val allowAgeRestricted: () -> Boolean = { true },
) : Extractor {
    private val memory = TierMemory(clock, memoryTtl)
    /** Anonymous tiers that answered SIGN_IN_REQUIRED recently: when one does so again the chain goes straight to the account. */
    private val signInFailures = HashMap<TierId, kotlin.time.Instant>()
    private val ttl = memoryTtl
    private var memoryOwner: String? = signedInAccount?.invoke()

    /** Only true if every tier honours it, so the UI never offers a choice some tier would silently ignore. */
    override val capabilities = ExtractorCapabilities(
        supportsCommentSort = tiers.isNotEmpty() && tiers.all { it.capabilities.supportsCommentSort },
    )

    override suspend fun search(query: String, continuation: String?) =
        run("search($query${page(continuation)})", AccountOp.NEVER) { it.search(query, continuation) }
    override suspend fun videoInfo(videoId: String) = run("videoInfo($videoId)", AccountOp.DETAILS) { it.videoInfo(videoId) }
    override suspend fun streams(videoId: String) = run("streams($videoId)", AccountOp.PLAYBACK) { it.streams(videoId) }
    override suspend fun channel(channelId: String) = run("channel($channelId)", AccountOp.CHANNELS) { it.channel(channelId) }
    override suspend fun channelVideos(channelId: String, continuation: String?) =
        run("channelVideos($channelId${page(continuation)})", AccountOp.CHANNELS) { it.channelVideos(channelId, continuation) }
    override suspend fun comments(videoId: String, sort: CommentSort, continuation: String?) =
        run("comments($videoId${page(continuation)})", AccountOp.NEVER) { it.comments(videoId, sort, continuation) }
    override suspend fun replies(videoId: String, commentId: String, continuation: String?) =
        run("replies($videoId/$commentId${page(continuation)})", AccountOp.NEVER) { it.replies(videoId, commentId, continuation) }

    override suspend fun trending(continuation: String?) = run("trending(${page(continuation)})", AccountOp.NEVER) { it.trending(continuation) }
    override suspend fun related(videoId: String) = run("related($videoId)", AccountOp.NEVER) { it.related(videoId) }
    override suspend fun channelShorts(channelId: String, continuation: String?) =
        run("channelShorts($channelId${page(continuation)})", AccountOp.CHANNELS) { it.channelShorts(channelId, continuation) }
    override suspend fun recentUploads(channelId: String) = run("recentUploads($channelId)", AccountOp.NEVER) { it.recentUploads(channelId) }

    private fun page(continuation: String?) = if (continuation == null) "" else ", more"

    @Volatile private var lastStreamsTier: TierId? = null

    /** Tries the tiers other than the one that served streams last first; if they all fail, that one is tried too. */
    override suspend fun streamsPreferringOther(videoId: String): ExtractorResult<StreamSet> {
        val last = lastStreamsTier ?: return streams(videoId)
        log.record(TierAttempt("streams($videoId)", last, AttemptOutcome.NOTE, null, "stream kept buffering; preferring another source", Duration.ZERO, clock.now()))
        return run("streams($videoId)", AccountOp.PLAYBACK, avoid = setOf(last)) { it.streams(videoId) }
    }

    private suspend fun <T> run(
        subject: String,
        op: AccountOp,
        avoid: Set<TierId> = emptySet(),
        call: suspend (Extractor) -> ExtractorResult<T>,
    ): ExtractorResult<T> {
        val account = signedInAccount?.invoke()
        if (signedInAccount != null && account != memoryOwner) { memory.clear(); memoryOwner = account }
        val skips = HashMap<TierId, String>()
        val all = candidateTiers(subject, account, op, skips)
        // Preferred order for a retry: the others first, the avoided tier last (never dropped).
        val candidates = if (avoid.isEmpty()) all else all.filter { it.id !in avoid } + all.filter { it.id in avoid }
        var signInSeen = false
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
            if (signInSeen && tier.id != TierId.AUTHENTICATED && TierId.AUTHENTICATED in available &&
                signInFailures[tier.id]?.let { clock.now() - it < ttl } == true
            ) {
                record(subject, tier.id, AttemptOutcome.SKIPPED, FailureReason.SIGN_IN_REQUIRED, "Failed the same way recently; going to the account", Duration.ZERO)
                continue
            }
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
                    signInFailures.remove(tier.id)
                    if (subject.startsWith("streams(")) lastStreamsTier = tier.id
                    record(subject, tier.id, AttemptOutcome.SUCCESS, null, servedDetail(tier, account, result.value), took)
                    failures.filter { (_, e) -> e.reason.isTierHealthIssue }
                        .forEach { (failed, e) -> memory.remember(failed, e.reason, tier.id) }
                    return result
                }
                is ExtractorResult.Failure -> {
                    record(subject, tier.id, AttemptOutcome.FAILED, result.error.reason, account?.takeIf { tier.id == TierId.AUTHENTICATED }?.let { "account $it: " }.orEmpty() + result.error.message, took)
                    memory.forgetWorked(tier.id)
                    if (result.error.reason == FailureReason.SIGN_IN_REQUIRED && tier.id != TierId.AUTHENTICATED) { signInSeen = true; signInFailures[tier.id] = clock.now() }
                    failures += tier.id to result.error
                    if (AgeText.matches(result.error.message) && !allowAgeRestricted()) {
                        record(subject, tier.id, AttemptOutcome.NOTE, null, "age-restricted and disabled in settings; no other source tried", Duration.ZERO)
                        return ExtractorResult.Failure(ExtractorError.AgeRestrictedDisabled())
                    }
                }
            }
        }
        return ExtractorResult.Failure(composeFailure(failures, skips[TierId.AUTHENTICATED]))
    }

    /**
     * Keeps the last tier's own error unless a tier said sign-in or age: then the answer names each tier's reason, what the
     * account did, and the environment hints, because "UNPLAYABLE: ERROR: [youtube]..." tells the viewer nothing.
     */
    private fun composeFailure(failures: List<Pair<TierId, ExtractorError>>, accountSkip: String?): ExtractorError {
        val age = failures.any { (_, e) -> AgeText.matches(e.message) }
        if (!age && failures.none { (_, e) -> e.reason == FailureReason.SIGN_IN_REQUIRED }) return failures.last().second
        val ids = tiers.mapTo(HashSet()) { it.id }
        val anonymous = failures.filter { it.first != TierId.AUTHENTICATED }
        val tried = failures.firstOrNull { it.first == TierId.AUTHENTICATED }
        val status = when {
            tried != null -> AccountStatus.TRIED
            TierId.AUTHENTICATED !in ids -> AccountStatus.NO_ACCOUNT_TIER
            accountSkip == null -> AccountStatus.TRIED
            accountSkip.startsWith("No signed-in") -> AccountStatus.SIGNED_OUT
            accountSkip.startsWith("Account fallback") || accountSkip.startsWith("The account is not used") -> AccountStatus.DISABLED
            else -> AccountStatus.PAUSED
        }
        val hints = TierId.entries.mapNotNull(tierHint).toMutableList()
        if (age && TierId.YT_DLP !in ids && hints.none { it.contains("yt-dlp") }) hints += "No tier on this device can play age-restricted videos without an account yet"
        val named = anonymous.joinToString(", ") { (id, e) -> "${tierName(id)} (${shortReason(e)})" }
        val accountSentence = when (status) {
            AccountStatus.PAUSED -> "The account is paused: ${accountSkip!!.replaceFirstChar { it.lowercase() }}."
            AccountStatus.SIGNED_OUT -> "No account is signed in."
            AccountStatus.DISABLED -> "Playing through the account is turned off."
            AccountStatus.NO_ACCOUNT_TIER -> "Playing through an account is not available on this device yet."
            AccountStatus.TRIED -> "The account also failed (${tried?.second?.let(::shortReason) ?: "no answer"})."
        }
        val message = (if (age) "Age-restricted" else "Sign-in required") + ": " +
            (if (named.isEmpty()) "no anonymous tier answered" else "anonymous tiers failed — $named") + ". $accountSentence" +
            hints.joinToString("") { " $it." }
        return TiersFailed(
            message = Redaction.redact(message),
            tiers = failures.map { (id, e) -> TierFailure(tierName(id), e.reason, shortReason(e)) },
            account = status, accountDetail = accountSkip, ageRestricted = age, hints = hints,
        )
    }

    private fun tierName(id: TierId) = when (id) {
        TierId.ANONYMOUS -> "NewPipe"; TierId.AUTHENTICATED -> "account"; TierId.ALT_CLIENT -> "alt client"; TierId.YT_DLP -> "yt-dlp"
    }

    private fun shortReason(e: ExtractorError): String = when {
        AgeText.matches(e.message) -> "age-restricted"
        e.reason == FailureReason.SIGN_IN_REQUIRED -> "sign-in required"
        e.reason == FailureReason.RATE_LIMITED -> "rate limited"
        e.reason == FailureReason.BLOCKED -> "blocked"
        e.reason == FailureReason.CAPTCHA -> "captcha required"
        e.reason == FailureReason.NETWORK -> "network error"
        e.reason == FailureReason.TIER_UNAVAILABLE -> "unavailable: " + e.message.take(80)
        else -> e.message.take(80)
    }

    /** What served a request: the account (if it did), the exact client/profile when the result names one, and the code version behind the tier. */
    private fun servedDetail(tier: ExtractorTier, account: String?, value: Any?): String? =
        listOfNotNull(
            account?.takeIf { tier.id == TierId.AUTHENTICATED }?.let { "account $it" },
            (value as? StreamSet)?.client?.let { "client $it" },
            tier.version,
        ).joinToString("; ").ifEmpty { null }

    /** Applies the account fallback setting: drops or promotes the authenticated tier. */
    private fun candidateTiers(subject: String, account: String?, op: AccountOp, skips: MutableMap<TierId, String>): List<ExtractorTier> {
        val mode = accountMode()
        var usable = tiers.filter { tier ->
            val why = if (tier.id != TierId.AUTHENTICATED) null else when {
                op == AccountOp.NEVER || !accountMayServe(op) -> "The account is not used for this"
                signedInAccount != null && account == null -> "No signed-in account"
                mode == AccountFallbackMode.NEVER -> "Account fallback is set to never"
                else -> accountPaused()
            }
            (why == null).also { keep -> if (!keep) { skips[tier.id] = why!!; record(subject, tier.id, AttemptOutcome.SKIPPED, null, why, Duration.ZERO) } }
        }
        tierOrder?.let { order ->
            val rank = TierOrder.sanitize(order(), mode)
            usable = usable.sortedBy { rank.indexOf(it.id).let { i -> if (i < 0) Int.MAX_VALUE else i } }
        }
        return if (mode == AccountFallbackMode.ALWAYS) usable.sortedBy { if (it.id == TierId.AUTHENTICATED) 0 else 1 } else usable
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

/** Tier messages that mean "age gate" (NewPipe, yt-dlp and the player API each word it differently). */
internal object AgeText {
    private val needles = listOf("age-restricted", "age restricted", "confirm your age", "inappropriate for some users")
    fun matches(message: String) = needles.any { message.contains(it, ignoreCase = true) }
}
