package com.greenaltambe.vela.ui

import com.greenaltambe.vela.extractor.FailureReason
import com.greenaltambe.vela.extractor.chain.AttemptOutcome
import com.greenaltambe.vela.extractor.chain.TierAttempt
import com.greenaltambe.vela.extractor.chain.TierId
import com.greenaltambe.vela.ui.screens.resolveNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ResolveNoteTest {
    private val t0 = Instant.fromEpochSeconds(1_800_000_000)
    private fun fail(tier: TierId, reason: FailureReason, detail: String, at: Instant = t0 + 1.seconds, video: String = "v") =
        TierAttempt("streams($video)", tier, AttemptOutcome.FAILED, reason, detail, 1.seconds, at)

    @Test fun nothingIsSaidWhileTheFirstTierIsStillWorking() = assertNull(resolveNote(emptyList(), "v", t0, accountSignedIn = true, accountMayServe = true))

    @Test fun anAgeFailureAnnouncesTheSlowerSourceBeforeYtDlpHasFailed() {
        val a = listOf(fail(TierId.ANONYMOUS, FailureReason.UNPLAYABLE, "Sign in to confirm your age"), fail(TierId.ALT_CLIENT, FailureReason.SIGN_IN_REQUIRED, "LOGIN_REQUIRED"))
        assertEquals("Age-restricted or sign-in video: trying a slower source (can take up to 30 s)…", resolveNote(a, "v", t0, true, true))
    }

    @Test fun afterYtDlpFailedTheAccountIsNamedOnlyWhenItMayServe() {
        val a = listOf(fail(TierId.ANONYMOUS, FailureReason.UNPLAYABLE, "age-restricted"), fail(TierId.YT_DLP, FailureReason.SIGN_IN_REQUIRED, "Sign in to confirm your age"))
        assertEquals("Using your account…", resolveNote(a, "v", t0, accountSignedIn = true, accountMayServe = true))
        assertEquals("Trying the next source…", resolveNote(a, "v", t0, accountSignedIn = false, accountMayServe = true))
        assertEquals("Trying the next source…", resolveNote(a, "v", t0, accountSignedIn = true, accountMayServe = false))
    }

    @Test fun oldAttemptsAndOtherVideosAreIgnored() {
        val a = listOf(fail(TierId.ANONYMOUS, FailureReason.UNPLAYABLE, "age-restricted", at = t0 - 60.seconds), fail(TierId.ANONYMOUS, FailureReason.UNPLAYABLE, "age-restricted", video = "other"))
        assertNull(resolveNote(a, "v", t0, true, true))
    }

    @Test fun aPlainNetworkFailureJustSaysTheNextSourceIsTried() =
        assertEquals("Trying the next source…", resolveNote(listOf(fail(TierId.ANONYMOUS, FailureReason.NETWORK, "timeout")), "v", t0, true, true))
}
