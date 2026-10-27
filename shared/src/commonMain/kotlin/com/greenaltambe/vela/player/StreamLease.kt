package com.greenaltambe.vela.player

import com.greenaltambe.vela.extractor.Extractor
import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.StreamSet
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Stream URLs die, and both playback and downloads have to cope. This is the one place that knows when they are dead
 * and how to get live ones, so [PlaybackSession] and the downloader cannot drift apart.
 */
class StreamLeases(
    private val extractor: Extractor,
    private val clock: Clock = Clock.System,
    /** URLs this close to expiring count as expired, so a refresh happens before the server rejects them. */
    private val expiryMargin: Duration = 30.seconds,
) {
    /** True once [set]'s URLs have (almost) expired. Unknown expiry is never "expired". */
    fun isExpired(set: StreamSet?): Boolean {
        val expiry = set?.expiresAt ?: return false
        return clock.now() >= expiry - expiryMargin
    }

    /** Fresh streams for [videoId], waiting out [StreamSet.availableAt] (some clients' URLs 403 for a few seconds after issue). */
    suspend fun resolve(videoId: String): ExtractorResult<StreamSet> =
        extractor.streams(videoId).also { if (it is ExtractorResult.Success) awaitAvailable(it.value) }

    /** Some URLs 403 until a few seconds after they were issued; wait, but never long. */
    suspend fun awaitAvailable(set: StreamSet) {
        val wait = set.availableAt?.let { it - clock.now() } ?: return
        if (wait.isPositive()) delay(minOf(wait, MAX_AVAILABILITY_WAIT))
    }

    companion object {
        /** The longest to wait for [StreamSet.availableAt]; anything beyond is treated as a bad value. */
        val MAX_AVAILABILITY_WAIT = 10.seconds

        /**
         * HTTP statuses that mean "this URL is no longer good, resolve again": 401/403 (signature or IP changed,
         * expired), 404 and 410 (gone). Anything else (5xx, 429, timeouts) is a transient failure, not a reason to re-resolve.
         */
        fun isDeadStatus(status: Int): Boolean = status == 401 || status == 403 || status == 404 || status == 410
    }
}
