package com.greenaltambe.vela.player.mpv

import com.greenaltambe.vela.core.settings.InMemorySettingsStore
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.data.JvmDatabaseDriverFactory
import com.greenaltambe.vela.data.SqlDelightWatchHistoryRepository
import com.greenaltambe.vela.data.createDatabase
import com.greenaltambe.vela.extractor.Extractor
import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.StreamSet
import com.greenaltambe.vela.extractor.newpipe.NewPipeExtractor
import com.greenaltambe.vela.player.PlaybackSession
import com.greenaltambe.vela.player.PlaybackStatus
import com.greenaltambe.vela.player.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The shared PlaybackSession over the real mpv controller: dead URLs on the first resolve, good ones on the second. */
class LiveMpvSessionRecoveryTest {
    @Test
    fun sessionReResolvesAfter403AndPlays() {
        val backend = requireLiveMpv()
        val real = NewPipeExtractor()
        val calls = AtomicInteger()
        val flaky = object : Extractor by real {
            override suspend fun streams(videoId: String): ExtractorResult<StreamSet> {
                val result = real.streams(videoId)
                if (calls.incrementAndGet() > 1 || result !is ExtractorResult.Success) return result
                fun bad(u: String) = u.replace(Regex("expire=\\d+"), "expire=1")
                val s = result.value
                return ExtractorResult.Success(s.copy(
                    videoStreams = s.videoStreams.map { it.copy(url = bad(it.url)) },
                    audioStreams = s.audioStreams.map { it.copy(url = bad(it.url)) },
                    hlsUrl = null,
                ))
            }
        }
        val controller = MpvPlayerController(backend)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val session = PlaybackSession(
                flaky, controller, SettingsResolver(InMemorySettingsStore()),
                SqlDelightWatchHistoryRepository(createDatabase(JvmDatabaseDriverFactory(null))), scope,
                constraints = { softwareCodecs },
            )
            session.start(BBB_24)
            val playing = controller.awaitState(60_000) { it.status == PlaybackStatus.PLAYING && it.positionMs > 2_000 }
            println("resolve calls=${calls.get()} state=${session.state.value::class.simpleName} player=${controller.state.value}")
            assertNotNull(playing, "never recovered: session=${session.state.value} player=${controller.state.value}")
            assertEquals(2, calls.get(), "expected exactly one re-resolve")
            assertTrue(session.state.value is SessionState.Ready)
        } finally {
            controller.release(); scope.cancel()
        }
    }
}
