package com.greenaltambe.vela.shorts

import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.extractor.Page
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.feed.BlocklistStep
import com.greenaltambe.vela.feed.FeedContext
import com.greenaltambe.vela.feed.FeedPipeline
import com.greenaltambe.vela.feed.KeywordFilter
import com.greenaltambe.vela.feed.OnlyShorts
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Where Shorts can come from. */
enum class ShortsOrigin(val label: String) {
    ACCOUNT("your account's Shorts"), ANONYMOUS("YouTube's Shorts without your account"), MIX("Shorts of channels")
}

/**
 * One of YouTube's Shorts feeds: the page after [continuation] or, without one, a new sequence ([seed] goes on from a Short of a listing). Null when
 * it could not be read.
 */
fun interface ShortsRead {
    suspend fun page(seed: String?, continuation: String?): Page<VideoSummary>?
}

/**
 * An endless supply of Shorts. [youTube] are YouTube's feeds in the order they are tried; [mix] (the channels' own Shorts) comes after the last.
 *
 * A source is kept while it answers, so its sequence goes on unbroken. A sequence that is refused, or ends with nothing new, is begun again once;
 * pages with nothing new are followed [MAX_FOLLOW] times. Then the next source takes over for good, and starts clean: a token or a seed is only ever
 * given to the source it was meant for. Nothing is handed out twice. A new object starts again from the first source.
 */
class ShortsFeed(
    private val youTube: List<Pair<ShortsOrigin, ShortsRead>>,
    private val mix: suspend () -> List<VideoSummary>,
    /** The token of a Short the viewer opened from a row, for the first source. */
    seed: String? = null,
    /** Shorts already on the page, never handed out again. */
    alreadyQueued: Set<String> = emptySet(),
    private val onSwitch: (from: ShortsOrigin, to: ShortsOrigin, why: String) -> Unit = { _, _, _ -> },
) {
    private var at = 0
    private var seed = seed
    private var token: String? = null
    private var begunAgain = false
    private val seen = alreadyQueued.toMutableSet()
    private val gate = Mutex()

    val origin: ShortsOrigin get() = youTube.getOrNull(at)?.first ?: ShortsOrigin.MIX

    /** More Shorts, or none when no source has any. One request is under way at a time. */
    suspend fun next(): List<VideoSummary> = gate.withLock {
        var followed = 0
        while (at < youTube.size) {
            val goingOn = token != null || seed != null
            val page = youTube[at].second.page(if (token == null) seed else null, token)
            if (page == null) {
                if (!beginAgain(goingOn)) { moveOn("could not be read"); followed = 0 }
                continue
            }
            seed = null
            token = page.continuation
            val fresh = page.items.filter { seen.add(it.id) }
            if (fresh.isNotEmpty()) {
                begunAgain = false
                return@withLock fresh
            }
            if (token == null) {
                if (!beginAgain(goingOn)) { moveOn("had nothing more"); followed = 0 }
            } else if (++followed > MAX_FOLLOW) {
                moveOn("kept repeating itself"); followed = 0
            }
        }
        mix().filter { seen.add(it.id) }
    }

    /** A new sequence from the same source: once, and only when the last request went on from somewhere. */
    private fun beginAgain(goingOn: Boolean): Boolean {
        if (begunAgain || !goingOn) return false
        begunAgain = true; token = null; seed = null
        return true
    }

    private fun moveOn(why: String) {
        val from = origin
        at++; token = null; seed = null; begunAgain = false
        onSwitch(from, origin, why)
    }

    companion object { const val MAX_FOLLOW = 3 }
}

/**
 * Those of [shorts] the viewer's rules leave out: blocked channels and words, the Shorts setting of their channel, keyword rules. A Short whose title
 * is not known yet cannot be judged and is not among them; [exempt] (the one the viewer opened) never is.
 */
fun AppServices.hiddenShorts(shorts: List<VideoSummary>, exempt: String? = null): Set<String> {
    val rules = FeedPipeline(listOf(BlocklistStep(blocklist.rules()), OnlyShorts, KeywordFilter))
    val judged = shorts.filter { it.id != exempt && it.title.isNotBlank() }.map { it.copy(isShort = true) }
    val passed = rules.run(judged, FeedContext(settings)).mapTo(HashSet()) { it.id }
    return judged.filterNot { it.id in passed }.mapTo(HashSet()) { it.id }
}
