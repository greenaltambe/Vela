package com.greenaltambe.vela.extractor.chain

import com.greenaltambe.vela.extractor.FailureReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

class RememberedRoute(
    val failedTier: TierId,
    val reason: FailureReason,
    val workedTier: TierId,
    val expiresAt: Instant,
)

/**
 * Remembers, per (failed tier, failure reason), which tier worked instead, until the entry expires.
 * At most one entry per key, so expired entries are ignored on read rather than pruned.
 * Safe for concurrent use: state is an immutable map swapped atomically.
 */
class TierMemory(private val clock: Clock, private val ttl: Duration) {
    private val routes = MutableStateFlow<Map<Pair<TierId, FailureReason>, RememberedRoute>>(emptyMap())

    fun remember(failedTier: TierId, reason: FailureReason, workedTier: TierId) {
        val route = RememberedRoute(failedTier, reason, workedTier, clock.now() + ttl)
        routes.update { it + ((failedTier to reason) to route) }
    }

    /**
     * A live route saying [tier] should be skipped, or null. Only routes whose worked tier is in [available]
     * count, so a route never hides a tier when the tier it points at isn't usable right now.
     */
    fun routeAround(tier: TierId, available: Set<TierId>): RememberedRoute? {
        val now = clock.now()
        return routes.value.values.firstOrNull { it.failedTier == tier && it.expiresAt > now && it.workedTier in available }
    }

    fun clear() { routes.value = emptyMap() }

    /** Called when a tier fails, so routes pointing at it stop hiding the earlier tiers. */
    fun forgetWorked(tier: TierId) = routes.update { current -> current.filterValues { it.workedTier != tier } }
}
