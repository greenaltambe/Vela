@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.greenaltambe.vela.core.settings.InMemorySettingsStore
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.data.JvmDatabaseDriverFactory
import com.greenaltambe.vela.data.SqlDelightWatchHistoryRepository
import com.greenaltambe.vela.data.createDatabase
import com.greenaltambe.vela.extractor.chain.DiagnosticsLog
import com.greenaltambe.vela.extractor.chain.FallbackChain
import com.greenaltambe.vela.extractor.chain.stubTiers
import com.greenaltambe.vela.extractor.newpipe.NewPipeExtractor
import com.greenaltambe.vela.modules.createModules
import com.greenaltambe.vela.net.OkHttpTransport
import com.greenaltambe.vela.playback.DesktopPlaybackRuntime
import com.greenaltambe.vela.playback.DesktopPlayerHost
import com.greenaltambe.vela.player.PlaybackStatus
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.modules.dearrow.DeArrowSurface
import com.greenaltambe.vela.ui.look.ComponentIds
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route
import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The real app over the real desktop player, driven by clicks: search from the top bar, open a result, watch it
 * play, scroll to the comments, subscribe, find the channel in the Subscriptions feed, then use the player controls.
 */
class DesktopUiLiveTest {
    @Test
    fun searchOpenScrollSubscribeFeedAndPlayerControls() = runDesktopComposeUiTest(width = 500, height = 900) {
        val reachable = runCatching { Socket().use { it.connect(InetSocketAddress("www.youtube.com", 443), 5_000) } }.isSuccess
        assumeTrue("SKIPPED: no network", reachable)
        val settings = SettingsResolver(InMemorySettingsStore())
        val log = DiagnosticsLog()
        val chain = FallbackChain(stubTiers(anonymous = NewPipeExtractor()), accountMode = { settings.resolve(Settings.accountFallbackMode) }, log = log)
        val database = createDatabase(JvmDatabaseDriverFactory(null))
        val modules = createModules(OkHttpTransport(), database, settings)
        val created = DesktopPlaybackRuntime.create(chain, settings, SqlDelightWatchHistoryRepository(database), listOf(modules.sponsorBlock))
        assumeTrue("SKIPPED: libmpv not loadable: ${created.exceptionOrNull()?.message}", created.isSuccess)
        val runtime = created.getOrThrow()
        val host = DesktopPlayerHost(runtime)
        val services = AppServices(chain, settings, log, database, host, modules = modules)
        val c = runtime.controller
        try {
            setContent { App(services) }

            onNodeWithTag("nav.search").performClick()
            onNodeWithTag("search.field").performTextInput("big buck bunny 60fps 4k")
            onNodeWithTag("search.submit").performClick()
            waitUntil(timeoutMillis = 30_000) { onAllNodesWithTag(ComponentIds.VIDEO_CARD).fetchSemanticsNodes().isNotEmpty() }
            println("search results shown: ${onAllNodesWithTag(ComponentIds.VIDEO_CARD).fetchSemanticsNodes().size} cards on screen")

            onAllNodesWithTag(ComponentIds.VIDEO_CARD).onFirst().performClick()
            waitUntil(timeoutMillis = 60_000) { c.state.value.status == PlaybackStatus.PLAYING && c.state.value.positionMs > 2_000 }
            println("playing: ${c.state.value}  stats=${c.stats.value}")
            val f0 = c.framesRendered
            val t0 = System.nanoTime()

            // Keyboard shortcuts: space, arrows, shift-arrows, m, up/down.
            val session = host.session
            onRoot().performKeyInput { pressKey(Key.Spacebar) }
            waitUntil(timeoutMillis = 10_000) { c.state.value.status == PlaybackStatus.PAUSED }
            onRoot().performKeyInput { pressKey(Key.Spacebar) }
            waitUntil(timeoutMillis = 10_000) { c.state.value.status == PlaybackStatus.PLAYING }
            val p0 = c.state.value.positionMs
            onRoot().performKeyInput { pressKey(Key.DirectionRight) }
            waitUntil(timeoutMillis = 10_000) { c.state.value.positionMs >= p0 + 4_000 }
            val p1 = c.state.value.positionMs
            onRoot().performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionRight) } }
            waitUntil(timeoutMillis = 10_000) { c.state.value.positionMs >= p1 + 9_000 }
            onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
            onRoot().performKeyInput { pressKey(Key.M) }
            waitUntil(timeoutMillis = 5_000) { session.muted.value }
            onRoot().performKeyInput { pressKey(Key.M) }
            waitUntil(timeoutMillis = 5_000) { !session.muted.value }
            onRoot().performKeyInput { pressKey(Key.DirectionDown) }
            waitUntil(timeoutMillis = 5_000) { session.volume.value < 0.99f }
            println("shortcuts ok: position ${c.state.value.positionMs} ms, volume ${session.volume.value}, mpv volume ${c.volume.value}")
            assertTrue(c.volume.value in 90..96, "volume reached mpv: ${c.volume.value}")
            onRoot().performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Period) } }
            waitUntil(timeoutMillis = 5_000) { c.state.value.speed == 1.25f }
            onRoot().performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Comma) } }
            waitUntil(timeoutMillis = 5_000) { c.state.value.speed == 1f }

            // Comments are far down the page: scroll until their Load more button exists, then use it.
            onNodeWithTag("video.list").performScrollToNode(hasTestTag("video.moreComments").or(hasTestTag("video.commentsStatus")))
            waitUntil(timeoutMillis = 30_000) {
                onAllNodesWithTag(ComponentIds.COMMENT_ITEM).fetchSemanticsNodes().isNotEmpty() ||
                    onAllNodesWithTag("video.commentsStatus").fetchSemanticsNodes().isNotEmpty()
            }
            val comments = onAllNodesWithTag(ComponentIds.COMMENT_ITEM).fetchSemanticsNodes().size
            println("comments composed after scrolling: $comments")

            // Subscribe, then look for the channel in Subscriptions.
            onNodeWithTag("video.list").performScrollToNode(hasTestTag("video.subscribe"))
            onNodeWithTag("video.subscribe").performClick()
            val channelId = services.subscriptions.all().single().channelId
            println("subscribed to $channelId (${services.subscriptions.all().single().name})")
            assertEquals(1, services.subscriptions.all().size)

            waitUntil(timeoutMillis = 45_000) { c.state.value.positionMs > 20_000 }
            val secs = (System.nanoTime() - t0) / 1e9
            val fps = (c.framesRendered - f0) / secs
            println("position ${c.state.value.positionMs} ms after ${"%.1f".format(secs)} s; mpv rendered %.1f fps; host drew %d".format(fps, host.framesDrawn))
            assertTrue(c.framesRendered - f0 > 20 * secs, "too few frames: $fps fps")

            // Player controls: they hide themselves while playing, a tap on the picture brings them back; speed and quality are menus.
            fun showControls() { if (onAllNodesWithTag("player.playPause").fetchSemanticsNodes().isEmpty()) onNodeWithTag("player.surface").performClick() }
            showControls()
            onNodeWithTag("player.playPause").performClick()
            waitUntil(timeoutMillis = 10_000) { c.state.value.status == PlaybackStatus.PAUSED }
            onNodeWithTag("player.playPause").performClick()
            waitUntil(timeoutMillis = 10_000) { c.state.value.status == PlaybackStatus.PLAYING }
            showControls()
            onNodeWithTag("player.speed").performClick()
            onNodeWithText("2.0x").performClick()
            waitUntil(timeoutMillis = 10_000) { c.state.value.speed == 2f }
            val before = c.stats.value.videoResolution
            showControls()
            onNodeWithTag("player.quality").performClick()
            onNodeWithText("360p").performClick()
            waitUntil(timeoutMillis = 30_000) { c.stats.value.videoResolution?.contains("x360") == true && c.state.value.status == PlaybackStatus.PLAYING }
            println("quality: $before -> ${c.stats.value.videoResolution}")

            // Leave the page: the mini player takes over and the video keeps playing.
            showControls()
            onNodeWithTag("player.back").performClick()
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(ComponentIds.MINI_PLAYER).fetchSemanticsNodes().isNotEmpty() }
            assertTrue(c.state.value.status == PlaybackStatus.PLAYING, "playback must survive navigation")

            // The feed: the subscribed channel's uploads arrive from the real RSS feed.
            onNodeWithText("Subs").performClick()
            waitUntil(timeoutMillis = 45_000) { services.feed.entries.value.isNotEmpty() }
            waitUntil(timeoutMillis = 15_000) { onAllNodesWithTag(ComponentIds.VIDEO_CARD).fetchSemanticsNodes().isNotEmpty() }
            println("feed: ${services.feed.entries.value.size} cached entries from $channelId; ${onAllNodesWithTag(ComponentIds.VIDEO_CARD).fetchSemanticsNodes().size} cards shown")
            assertTrue(services.feed.entries.value.all { it.video.channelId == channelId })

            // Close from the mini player.
            onNodeWithTag("mini.close").performClick()
            waitUntil(timeoutMillis = 10_000) { c.state.value.status == PlaybackStatus.IDLE }
        } finally {
            runtime.close()
        }
    }

    @Test
    fun sponsorBlockRydAndDeArrowOnARealSponsoredVideo() = runDesktopComposeUiTest(width = 500, height = 900) {
        val reachable = runCatching { Socket().use { it.connect(InetSocketAddress("sponsor.ajay.app", 443), 5_000) } }.isSuccess
        assumeTrue("SKIPPED: no network", reachable)
        val settings = SettingsResolver(InMemorySettingsStore())
        val log = DiagnosticsLog()
        val chain = FallbackChain(stubTiers(anonymous = NewPipeExtractor()), accountMode = { settings.resolve(Settings.accountFallbackMode) }, log = log)
        val database = createDatabase(JvmDatabaseDriverFactory(null))
        val modules = createModules(OkHttpTransport(), database, settings)
        val created = DesktopPlaybackRuntime.create(chain, settings, SqlDelightWatchHistoryRepository(database), listOf(modules.sponsorBlock))
        assumeTrue("SKIPPED: libmpv not loadable: ${created.exceptionOrNull()?.message}", created.isSuccess)
        val runtime = created.getOrThrow()
        val host = DesktopPlayerHost(runtime)
        val services = AppServices(chain, settings, log, database, host, modules = modules)
        val c = runtime.controller
        val navigator = Navigator().also { it.push(Route.Video(VideoSummary("DN953UW8iJ8", "placeholder title", "UC", "channel"))) }
        try {
            setContent { App(services, navigator) }
            waitUntil(timeoutMillis = 60_000) { c.state.value.status == PlaybackStatus.PLAYING && c.state.value.positionMs > 1_000 }

            // Return YouTube Dislike and DeArrow fill in after the page is already playing.
            waitUntil(timeoutMillis = 20_000) { onAllNodesWithTag("video.votes").fetchSemanticsNodes().isNotEmpty() }
            println("votes line shown: ${modules.ryd.cached("DN953UW8iJ8")}")
            waitUntil(timeoutMillis = 20_000) { modules.dearrow.cached("DN953UW8iJ8") != null }
            val toggle = onAllNodesWithTag("video.toggleOriginal").fetchSemanticsNodes().isNotEmpty()
            println("dearrow title replaced on the page: $toggle (cached title: ${modules.dearrow.title("DN953UW8iJ8", "ORIGINAL", DeArrowSurface.VIDEO_PAGE)})")
            if (toggle) {
                onNodeWithTag("video.toggleOriginal").performClick()
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Original title", substring = true).fetchSemanticsNodes().isNotEmpty() }
                onNodeWithTag("video.toggleOriginal").performClick()
            }

            // SponsorBlock: markers, an automatic skip with a notice, Undo, and a prompt.
            waitUntil(timeoutMillis = 30_000) { modules.sponsorBlock.segments.value.isNotEmpty() }
            val all = modules.sponsorBlock.segments.value
            println("markers: ${all.map { "${it.category} ${it.startMs}-${it.endMs}" }}")
            val sponsor = all.first { it.category == "sponsor" && it.actionType == "skip" }

            host.session.seekTo(sponsor.startMs - 2_000)
            waitUntil(timeoutMillis = 30_000) { c.state.value.positionMs >= sponsor.endMs - 500 }
            println("skipped: now at ${c.state.value.positionMs} ms (segment ${sponsor.startMs}-${sponsor.endMs})")
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("sponsorblock.notice").fetchSemanticsNodes().isNotEmpty() }

            onNodeWithTag("sponsorblock.undo").performClick()
            waitUntil(timeoutMillis = 15_000) { c.state.value.positionMs in (sponsor.startMs - 1_000)..(sponsor.startMs + 4_000) }
            val after = c.state.value.positionMs
            Thread.sleep(2_500)
            assertTrue(c.state.value.positionMs < sponsor.endMs, "after Undo the segment plays (position ${c.state.value.positionMs}, was $after)")

            // A segment whose action is "prompt" shows a Skip button instead of skipping.
            val prompted = all.firstOrNull { it.category != "sponsor" && it.category != "selfpromo" && it.category != "interaction" && it.category != "filler" && !it.isPoint }
            if (prompted != null) {
                host.session.seekTo(prompted.startMs + 500)
                waitUntil(timeoutMillis = 15_000) { onAllNodesWithTag("sponsorblock.prompt").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithTag("sponsorblock.prompt").performClick()
                waitUntil(timeoutMillis = 15_000) { c.state.value.positionMs >= prompted.endMs - 500 }
                println("prompt '${prompted.category}' accepted: now at ${c.state.value.positionMs} ms")
            } else println("no prompt-type segment on this video today")
        } finally {
            runtime.close()
        }
    }
}
