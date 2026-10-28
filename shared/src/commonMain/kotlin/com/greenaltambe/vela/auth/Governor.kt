package com.greenaltambe.vela.auth

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** What an authenticated request is for; each kind has its own budget. */
enum class CallKind { PLAYBACK, BROWSE, WRITE, SESSION_CHECK }

/** Why the breaker opens. [observed] marks what this project has actually seen from the services (see docs/reports/14-reliability.md). */
enum class BlockSignal(val label: String, val observed: Boolean) {
    HTTP_429("HTTP 429", false),
    CAPTCHA("captcha or /sorry page", false),
    UNUSUAL_TRAFFIC("\"unusual traffic\" text", false),
    VERIFICATION("account verification page", false),
    REPEATED_LOGIN_REQUIRED("repeated LOGIN_REQUIRED after a valid session", false),
    COOKIES_INVALIDATED("cookies rejected / signed out", true),
}

/** Implemented by exceptions that know they mean "stop calling with this account". */
interface BreakerSignal { val signal: BlockSignal }

enum class BreakerState { CLOSED, OPEN, HALF_OPEN }

data class BreakerStatus(val state: BreakerState, val openUntil: Instant?, val level: Int, val lastSignal: BlockSignal?) {
    companion object { val Closed = BreakerStatus(BreakerState.CLOSED, null, 0, null) }
}

enum class DenyReason { KILL_SWITCH, COOLING_DOWN, BUDGET }

/** Thrown instead of making the call. Never a network error; callers treat it as "this tier is unavailable right now". */
class GovernorDenied(val reason: DenyReason, val until: Instant?, message: String) : Exception(message)

data class GovernorLimits(
    val playbackPerHour: Int = 20,
    val browsePerMinute: Int = 6,
    val writesPerHour: Int = 10,
    val killSwitch: Boolean = false,
    val minSpacingMs: Long = 1500,
)

/** Persists the breaker so a restart does not make a cooling-down account look healthy. */
interface BreakerStore {
    fun load(accountId: String): BreakerStatus
    fun save(accountId: String, status: BreakerStatus)
}

class InMemoryBreakerStore : BreakerStore {
    private val map = HashMap<String, BreakerStatus>()
    @Synchronized override fun load(accountId: String) = map[accountId] ?: BreakerStatus.Closed
    @Synchronized override fun save(accountId: String, status: BreakerStatus) { map[accountId] = status }
}

/** One line of the local audit log: no credentials, no URLs with query strings, just what happened. */
data class AuditEntry(val accountId: String, val at: Instant, val kind: String, val host: String, val result: String, val decision: String)

fun interface AuditSink { fun record(entry: AuditEntry) }

object NoAudit : AuditSink { override fun record(entry: AuditEntry) {} }

/**
 * Text patterns that mean the service is pushing back, for stderr and bodies. All are ASSUMED unless the report says
 * otherwise: the throwaway account never triggered one during the measurements.
 */
object BlockPatterns {
    private val patterns: List<Pair<Regex, BlockSignal>> = listOf(
        Regex("unusual traffic", RegexOption.IGNORE_CASE) to BlockSignal.UNUSUAL_TRAFFIC,
        Regex("/sorry/|google\\.com/sorry|recaptcha|captcha", RegexOption.IGNORE_CASE) to BlockSignal.CAPTCHA,
        Regex("HTTP Error 429|Too Many Requests|\\b429\\b", RegexOption.IGNORE_CASE) to BlockSignal.HTTP_429,
        Regex("confirm it'?s you|verify it'?s you|verify your identity|account verification|accounts\\.google\\.com/signin/v2/challenge", RegexOption.IGNORE_CASE) to BlockSignal.VERIFICATION,
        Regex("cookies are no longer valid|cookies.*(expired|invalid|rotated)", RegexOption.IGNORE_CASE) to BlockSignal.COOKIES_INVALIDATED,
    )

    fun scan(text: String?): BlockSignal? = text?.let { t -> patterns.firstOrNull { it.first.containsMatchIn(t) }?.second }
}

/**
 * The single choke point for traffic that carries an account's credentials. Everything that attaches cookies or a
 * SAPISIDHASH runs inside [call]: serialised per account, spaced with jitter, counted against budgets, refused while the
 * breaker is open or the kill switch is on, and written to the audit log. Credentials are only injectable through the
 * transports that require a governor (see `AuthSession`), so there is no other path.
 */
class AccountGovernor(
    val accountId: String,
    private val clock: Clock = Clock.System,
    private val limits: () -> GovernorLimits = { GovernorLimits() },
    private val store: BreakerStore = InMemoryBreakerStore(),
    private val audit: AuditSink = NoAudit,
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
    private val random: Random = Random.Default,
) {
    private val mutex = Mutex()
    private val _status = MutableStateFlow(store.load(accountId))
    val status: StateFlow<BreakerStatus> = _status

    private val stamps = ArrayDeque<Pair<CallKind, Long>>()
    private var lastCallAt = 0L
    private var softLoginRequired = 0
    private var successes = 0
    private var everSucceeded = false

    /** Until when calls are refused, or null if the breaker is closed (or its cooldown is over). */
    fun cooldownUntil(): Instant? = _status.value.takeIf { it.state == BreakerState.OPEN }?.openUntil?.takeIf { it > clock.now() }

    /** The deliberate "try now": moves OPEN to HALF_OPEN so exactly one probe call is allowed. */
    fun tryNow() { set(_status.value.copy(state = BreakerState.HALF_OPEN, openUntil = null)) }

    /** For failures only the caller can see (yt-dlp stderr, a body with a captcha). */
    fun trip(signal: BlockSignal) {
        val level = _status.value.level
        val until = clock.now() + COOLDOWNS[level.coerceAtMost(COOLDOWNS.lastIndex)]
        successes = 0
        set(BreakerStatus(BreakerState.OPEN, until, level + 1, signal))
    }

    /** A LOGIN_REQUIRED answer: only a breaker signal when the session had worked before and it keeps happening. */
    fun loginRequired() {
        softLoginRequired++
        if (everSucceeded && softLoginRequired >= 2) { softLoginRequired = 0; trip(BlockSignal.REPEATED_LOGIN_REQUIRED) }
    }

    suspend fun <T> call(kind: CallKind, host: String, inspect: (T) -> BlockSignal? = { null }, op: suspend () -> T): T = mutex.withLock {
        val now0 = clock.now()
        try { admit(kind, now0) } catch (d: GovernorDenied) { log(kind, host, "refused", d.reason.name); throw d }
        val wait = spacing()
        if (wait > 0) sleeper(wait)
        stamps.addLast(kind to clock.now().toEpochMilliseconds()); lastCallAt = clock.now().toEpochMilliseconds()
        val result = try { op() } catch (e: Throwable) {
            val signal = (e as? BreakerSignal)?.signal ?: BlockPatterns.scan(e.message)
            if (signal != null) { trip(signal); log(kind, host, "failed: ${signal.label}", "breaker opened") }
            else log(kind, host, "failed: ${e.javaClass.simpleName}", "allowed")
            throw e
        }
        val signal = inspect(result)
        if (signal != null) { trip(signal); log(kind, host, "blocked: ${signal.label}", "breaker opened"); return@withLock result }
        succeeded(); log(kind, host, "ok", "allowed")
        result
    }

    private fun admit(kind: CallKind, now: Instant) {
        val l = limits()
        if (l.killSwitch) throw GovernorDenied(DenyReason.KILL_SWITCH, null, "Account traffic is switched off")
        val s = _status.value
        if (s.state == BreakerState.OPEN) {
            if (s.openUntil != null && s.openUntil > now) throw GovernorDenied(DenyReason.COOLING_DOWN, s.openUntil, "Account cooling down until ${hhmm(s.openUntil)}")
            set(s.copy(state = BreakerState.HALF_OPEN, openUntil = null))
        }
        val (window, cap) = when (kind) {
            CallKind.PLAYBACK -> 1.hours to l.playbackPerHour
            CallKind.WRITE -> 1.hours to l.writesPerHour
            CallKind.BROWSE, CallKind.SESSION_CHECK -> 1.minutes to l.browsePerMinute
        }
        val kinds = if (kind == CallKind.BROWSE || kind == CallKind.SESSION_CHECK) setOf(CallKind.BROWSE, CallKind.SESSION_CHECK) else setOf(kind)
        val from = now.toEpochMilliseconds() - window.inWholeMilliseconds
        while (stamps.isNotEmpty() && stamps.first().second < now.toEpochMilliseconds() - 1.hours.inWholeMilliseconds) stamps.removeFirst()
        val inWindow = stamps.filter { it.first in kinds && it.second >= from }
        if (inWindow.size >= cap) {
            val until = Instant.fromEpochMilliseconds(inWindow.first().second + window.inWholeMilliseconds)
            throw GovernorDenied(DenyReason.BUDGET, until, "${kind.name.lowercase()} budget used up until ${hhmm(until)}")
        }
    }

    private fun spacing(): Long {
        val min = limits().minSpacingMs
        if (min <= 0) return 0
        val earliest = lastCallAt + min + random.nextLong(0, min / 3 + 1)
        return (earliest - clock.now().toEpochMilliseconds()).coerceAtLeast(0).takeIf { lastCallAt > 0 } ?: 0
    }

    private fun succeeded() {
        everSucceeded = true; softLoginRequired = 0; successes++
        val s = _status.value
        if (s.state == BreakerState.HALF_OPEN) set(s.copy(state = BreakerState.CLOSED, openUntil = null))
        // Sustained success forgives past escalation.
        if (successes >= SUSTAINED && s.level > 0) set(BreakerStatus.Closed)
    }

    private fun set(s: BreakerStatus) { _status.value = s; store.save(accountId, s) }

    private fun log(kind: CallKind, host: String, result: String, decision: String) =
        audit.record(AuditEntry(accountId, clock.now(), kind.name, host, result, decision))

    companion object {
        /** 1 h, then 6 h, then 24 h for every further trip. */
        val COOLDOWNS: List<Duration> = listOf(1.hours, 6.hours, 24.hours)
        const val SUSTAINED = 10
        private fun hhmm(t: Instant): String {
            val m = (t.toEpochMilliseconds() / 60_000) % (24 * 60)
            return "${(m / 60).toString().padStart(2, '0')}:${(m % 60).toString().padStart(2, '0')} UTC"
        }
    }
}

/** One governor per account, built on demand from the settings, so every credentialed call of an account shares its budgets and breaker. */
class GovernorRegistry(
    private val clock: Clock = Clock.System,
    private val limits: () -> GovernorLimits = { GovernorLimits() },
    private val store: BreakerStore = InMemoryBreakerStore(),
    private val audit: AuditSink = NoAudit,
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
) {
    private val map = HashMap<String, AccountGovernor>()
    @Synchronized fun forAccount(accountId: String): AccountGovernor =
        map.getOrPut(accountId) { AccountGovernor(accountId, clock, limits, store, audit, sleeper) }
}
