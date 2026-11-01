package com.greenaltambe.vela.extractor

import com.greenaltambe.vela.core.settings.AccountFallbackMode
import com.greenaltambe.vela.extractor.chain.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes

class FallbackChainChannelTierTest {
    private val clock = FakeClock()
    private val log = DiagnosticsLog()

    @Test
    fun channelTokenFailureDoesNotPoisonStreamsTierMemory() = runTest {
        var altStreamsCalled = 0
        var altTabCalled = 0
        val altClient = object : Extractor by StubExtractor() {
            override suspend fun channelTabByContinuation(token: String): ExtractorResult<ChannelTabPage> {
                altTabCalled++
                // On HTTP 400 from YouTube browse, ChannelBrowse was returning ExtractorError.Blocked
                // but should return TokenExpired, and CHANNELS operations must never poison playback memory
                return ExtractorResult.Failure(ExtractorError.Blocked("HTTP 400"))
            }

            override suspend fun streams(videoId: String): ExtractorResult<StreamSet> {
                altStreamsCalled++
                return ExtractorResult.Success(StreamSet(emptyList(), emptyList()))
            }
        }

        val newpipe = object : Extractor by StubExtractor() {
            override suspend fun channelTabByContinuation(token: String): ExtractorResult<ChannelTabPage> {
                return ExtractorResult.Success(ChannelTabPage())
            }

            override suspend fun streams(videoId: String): ExtractorResult<StreamSet> {
                return ExtractorResult.Success(StreamSet(emptyList(), emptyList()))
            }
        }

        val chain = FallbackChain(
            tiers = listOf(
                ExtractorTier(TierId.ALT_CLIENT, altClient),
                ExtractorTier(TierId.ANONYMOUS, newpipe),
            ),
            accountMode = { AccountFallbackMode.NEVER },
            log = log,
            clock = clock,
            memoryTtl = 15.minutes,
        )

        // Step 1: Channel continuation fails on alt client, NewPipe answers successfully
        val tabRes = chain.channelTabByContinuation("test-continuation")
        assertIs<ExtractorResult.Success<*>>(tabRes)
        assertEquals(1, altTabCalled)

        // Step 2: Now request streams(). Alt client MUST still be tried and not routed around!
        val streamsRes = chain.streams("test-video")
        assertIs<ExtractorResult.Success<*>>(streamsRes)
        assertEquals(1, altStreamsCalled, "streams() must still try the alt client instead of skipping it due to channel browse failure")
    }

    @Test
    fun tokenNotMineStopsFallbackAndDoesNotForward() = runTest {
        var newpipeCalled = 0
        val nativeTier = object : Extractor by StubExtractor() {
            override suspend fun channelTabByContinuation(token: String): ExtractorResult<ChannelTabPage> {
                return ExtractorResult.Failure(ExtractorError.TokenNotMine("Token is for NewPipe"))
            }
        }
        val newpipeTier = object : Extractor by StubExtractor() {
            override suspend fun channelTabByContinuation(token: String): ExtractorResult<ChannelTabPage> {
                newpipeCalled++
                return ExtractorResult.Success(ChannelTabPage())
            }
        }

        val chain = FallbackChain(
            tiers = listOf(
                ExtractorTier(TierId.ALT_CLIENT, nativeTier),
                ExtractorTier(TierId.ANONYMOUS, newpipeTier),
            ),
            accountMode = { AccountFallbackMode.NEVER },
            log = log,
            clock = clock,
        )

        val res = chain.channelTabByContinuation("np:token123")
        assertIs<ExtractorResult.Failure>(res)
        assertIs<ExtractorError.TokenNotMine>(res.error)
        assertEquals(0, newpipeCalled, "TokenNotMine must not be forwarded to subsequent tiers")
    }
}
