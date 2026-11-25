package com.greenaltambe.vela.shorts

import com.greenaltambe.vela.extractor.Page
import com.greenaltambe.vela.extractor.VideoSummary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** Which source a Shorts feed asks, with what, and when it turns to the next. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ShortsSourceOrderTest {
    private fun v(id: String) = VideoSummary(id, "", "", "", isShort = true)
    private fun page(next: String?, vararg ids: String) = Page(ids.map(::v), next)

    /** A source answering from a script; every request is written down as "name seed/token". */
    private val asked = mutableListOf<String>()
    private fun source(name: String, vararg answers: Page<VideoSummary>?): ShortsRead {
        val left = ArrayDeque(answers.toList())
        return ShortsRead { seed, token ->
            asked += "$name ${seed?.let { "seed=$it" } ?: token ?: "new"}"
            if (left.isEmpty()) null else left.removeFirst()
        }
    }
    private val switches = mutableListOf<Pair<ShortsOrigin, ShortsOrigin>>()
    private var mixCalls = 0
    private var mixAnswer = listOf(v("m1"))
    private fun feed(account: ShortsRead?, anonymous: ShortsRead?, seed: String? = null, queued: Set<String> = emptySet()) = ShortsFeed(
        listOfNotNull(account?.let { ShortsOrigin.ACCOUNT to it }, anonymous?.let { ShortsOrigin.ANONYMOUS to it }),
        { mixCalls++; mixAnswer }, seed, queued,
    ) { from, to, _ -> switches += from to to }
    private suspend fun ShortsFeed.ids() = next().map { it.id }

    @Test fun theFirstSourceIsKeptAndItsSequenceFollowed() = runTest {
        val f = feed(source("acc", page("t1", "a"), page("t2", "b", "c")), source("anon"))
        assertEquals(listOf("a"), f.ids()); assertEquals(listOf("b", "c"), f.ids())
        assertEquals(listOf("acc new", "acc t1"), asked)
        assertEquals(ShortsOrigin.ACCOUNT, f.origin); assertEquals(0, mixCalls)
    }

    @Test fun aFailedFirstReadGoesStraightToTheNextSource() = runTest {
        val f = feed(source("acc", null), source("anon", page("u1", "x")))
        assertEquals(listOf("x"), f.ids())
        assertEquals(listOf("acc new", "anon new"), asked)
        assertEquals(listOf(ShortsOrigin.ACCOUNT to ShortsOrigin.ANONYMOUS), switches)
    }

    @Test fun aRefusedTokenStartsOneNewSequenceThenTheNextSourceWithoutThatToken() = runTest {
        val f = feed(source("acc", page("t1", "a"), null, null), source("anon", page("u1", "x"), page(null, "y")))
        assertEquals(listOf("a"), f.ids())
        assertEquals(listOf("x"), f.ids())
        assertEquals(listOf("acc new", "acc t1", "acc new", "anon new"), asked, "no account token reaches the other source")
    }

    @Test fun aNewSequenceThatWorksKeepsTheSource() = runTest {
        val f = feed(source("acc", page("t1", "a"), null, page("t2", "b")), source("anon"))
        f.ids(); assertEquals(listOf("b"), f.ids())
        assertEquals(ShortsOrigin.ACCOUNT, f.origin); assertTrue(switches.isEmpty())
    }

    @Test fun aRowsSeedGoesToTheFirstSourceOnlyOnce() = runTest {
        val f = feed(source("acc", page("t1", "a"), page("t2", "b")), source("anon"), seed = "row")
        f.ids(); f.ids()
        assertEquals(listOf("acc seed=row", "acc t1"), asked)
    }

    @Test fun aFailingSeedIsDroppedAndNeverPassedOn() = runTest {
        val f = feed(source("acc", null, null), source("anon", page("u1", "x")), seed = "row")
        assertEquals(listOf("x"), f.ids())
        assertEquals(listOf("acc seed=row", "acc new", "anon new"), asked)
    }

    @Test fun pagesWithNothingNewAreFollowedOnlyAFewTimes() = runTest {
        val repeats = Array(10) { page("t${it + 2}", "a") }
        val f = feed(source("acc", page("t1", "a"), *repeats), source("anon", page("u1", "x")))
        f.ids()
        assertEquals(listOf("x"), f.ids())
        assertEquals(1 + ShortsFeed.MAX_FOLLOW + 1, asked.count { it.startsWith("acc") })
    }

    @Test fun nothingIsHandedOutTwiceNorWhatIsAlreadyOnThePage() = runTest {
        val f = feed(source("acc", page("t1", "tapped", "a"), page("t2", "a", "b")), null, queued = setOf("tapped"))
        assertEquals(listOf("a"), f.ids()); assertEquals(listOf("b"), f.ids())
    }

    @Test fun anEndedSequenceBeginsANewOne() = runTest {
        val f = feed(source("acc", page(null, "a"), page("t1", "b")), null)
        assertEquals(listOf("a"), f.ids()); assertEquals(listOf("b"), f.ids())
        assertEquals(listOf("acc new", "acc new"), asked)
    }

    @Test fun whenEveryYouTubeFeedFailsTheChannelsAreUsedAndTheyStay() = runTest {
        val f = feed(source("acc", null), source("anon", null))
        assertEquals(listOf("m1"), f.ids())
        assertEquals(ShortsOrigin.MIX, f.origin)
        mixAnswer = listOf(v("m1"), v("m2"))
        assertEquals(listOf("m2"), f.ids())
        assertEquals(2, asked.size, "the failed sources are not asked again by this feed")
    }

    @Test fun whenNothingAnswersTheFeedSaysSoWithoutLooping() = runTest {
        mixAnswer = emptyList()
        assertEquals(emptyList(), feed(source("acc", null), source("anon", null)).ids())
        assertEquals(2, asked.size); assertEquals(1, mixCalls)
    }

    @Test fun withNoYouTubeFeedOnlyTheChannelsAreAsked() = runTest {
        assertEquals(listOf("m1"), feed(null, null).ids())
        assertTrue(asked.isEmpty())
    }

    @Test fun oneRequestIsUnderWayAtATime() = runTest {
        val gate = CompletableDeferred<Unit>()
        var running = 0; var most = 0; var n = 0
        val f = feed(ShortsRead { _, _ -> running++; most = maxOf(most, running); gate.await(); running--; page("t${++n}", "s$n") }, null)
        val a = async { f.next() }; val b = async { f.next() }
        runCurrent(); gate.complete(Unit)
        a.await(); b.await()
        assertEquals(1, most)
    }
}
