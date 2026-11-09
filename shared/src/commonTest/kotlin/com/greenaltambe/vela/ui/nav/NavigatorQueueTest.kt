package com.greenaltambe.vela.ui.nav

import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.ui.nav.Navigator.QueueResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The play queue: "Add to queue" (end), "Play next" (front), removing, skipping ahead, and what happens with nothing playing. */
class NavigatorQueueTest {
    private fun v(id: String) = VideoSummary(id, "Video $id", "UC", "Chan")
    private fun playing(vararg queued: String) = Navigator().also { n ->
        n.setNowPlayingDirect(v("now")); queued.forEach { n.addToQueue(v(it)) }
    }
    private fun Navigator.ids() = queue.map { it.id }

    @Test fun addToQueueAppendsInOrder() {
        val n = playing("a", "b")
        assertEquals(QueueResult.QUEUED, n.addToQueue(v("c")))
        assertEquals(listOf("a", "b", "c"), n.ids())
    }

    @Test fun playNextGoesFirstAndAVideoAlreadyQueuedMovesInsteadOfRepeating() {
        val n = playing("a", "b", "c")
        n.playNextUp(v("c"))
        assertEquals(listOf("c", "a", "b"), n.ids())
        n.addToQueue(v("c"))
        assertEquals(listOf("a", "b", "c"), n.ids(), "adding a queued video again moves it to the end, it is not listed twice")
    }

    @Test fun theVideoThatIsPlayingIsNotQueued() {
        val n = playing("a")
        assertEquals(QueueResult.ALREADY_PLAYING, n.addToQueue(v("now")))
        assertEquals(listOf("a"), n.ids())
    }

    @Test fun withNothingPlayingTheVideoStartsInsteadOfWaiting() {
        val n = Navigator()
        assertEquals(QueueResult.STARTED, n.addToQueue(v("x")))
        assertEquals("x", n.nowPlaying?.id)
        assertEquals(emptyList(), n.ids())
    }

    @Test fun removeAndClearAndUndo() {
        val n = playing("a", "b", "c")
        val before = n.queue
        n.removeFromQueue("b"); assertEquals(listOf("a", "c"), n.ids())
        n.clearQueue(); assertEquals(emptyList(), n.ids())
        n.restoreQueue(before); assertEquals(listOf("a", "b", "c"), n.ids())
    }

    @Test fun playFromQueueSkipsTheOnesBeforeAndKeepsTheOnesAfter() {
        val n = playing("a", "b", "c", "d")
        assertEquals("c", n.playFromQueue(2)?.id)
        assertEquals("c", n.nowPlaying?.id)
        assertEquals(listOf("d"), n.ids())
        assertNull(n.playFromQueue(5), "an index past the end does nothing")
        assertEquals(listOf("d"), n.ids())
    }

    @Test fun moveInQueueReordersAndIgnoresBadPositions() {
        val n = playing("a", "b", "c", "d")
        n.moveInQueue(0, 2); assertEquals(listOf("b", "c", "a", "d"), n.ids())
        n.moveInQueue(3, 0); assertEquals(listOf("d", "b", "c", "a"), n.ids())
        n.moveInQueue(1, 1); n.moveInQueue(-1, 2); n.moveInQueue(0, 9)
        assertEquals(listOf("d", "b", "c", "a"), n.ids())
    }

    @Test fun repeatBelongsToTheVideoItWasSetOn() {
        val n = playing()
        n.repeatOne = true
        n.replaceWithVideo(v("other"))
        assertEquals(false, n.repeatOne, "another video starts without repeat")
        n.repeatOne = true
        n.replaceWithVideo(v("other"))
        assertEquals(true, n.repeatOne, "the same video keeps it")
    }

    @Test fun shuffleKeepsTheSameVideosAndAFixedSeedGivesAFixedOrder() {
        val n = playing("a", "b", "c", "d", "e", "f")
        n.shuffleQueue(kotlin.random.Random(1))
        assertEquals(setOf("a", "b", "c", "d", "e", "f"), n.ids().toSet())
        val once = n.ids()
        val m = playing("a", "b", "c", "d", "e", "f"); m.shuffleQueue(kotlin.random.Random(1))
        assertEquals(once, m.ids())
        val single = playing("a"); single.shuffleQueue(); assertEquals(listOf("a"), single.ids())
    }
}

class NavigatorTabTest {
    @Test fun choosingTheOpenTabAgainIsCountedAndChoosingAnotherIsNot() {
        val n = Navigator()
        n.selectTab(Route.Subscriptions)
        val before = n.tabReselected
        n.selectTab(Route.Subscriptions)
        n.selectTab(Route.Subscriptions)
        assertEquals(before + 2, n.tabReselected)
        n.selectTab(Route.Library)
        assertEquals(before + 2, n.tabReselected, "moving to another tab is not a reselection")
        assertEquals(Route.Library, n.current)
    }

    @Test fun onATabsDeeperPageTheTabButtonGoesBackToThatTabNotToTheTop() {
        val n = Navigator()
        n.selectTab(Route.Library)
        n.push(Route.History)
        val before = n.tabReselected
        n.selectTab(Route.Library)
        assertEquals(Route.Library, n.current)
        assertEquals(before, n.tabReselected)
    }
}
