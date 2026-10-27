package com.greenaltambe.vela.player.mpv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RenderSizeTest {
    @Test fun widgetSmallerThanVideoIsLeftAlone() = assertEquals(1280 to 720, cappedRenderSize(1280, 720, 1920, 1080))

    @Test fun widgetLargerThanVideoIsScaledDownToTheVideoKeepingAspect() =
        assertEquals(1920 to 1080, cappedRenderSize(3840, 2160, 1920, 1080))

    @Test fun aWindowWiderThanTheVideoIsCappedByTheLimitingSide() {
        // 16:9 video in a 2560x1080 window: mpv would fit by height (scale 1.0); no cap needed.
        assertEquals(2560 to 1080, cappedRenderSize(2560, 1080, 1920, 1080))
        // Taller window: fit by width; 2400x2400 window shows 1920x1080 at scale 1.25 -> render at 1920x1920.
        assertEquals(1920 to 1920, cappedRenderSize(2400, 2400, 1920, 1080))
    }

    @Test fun unknownVideoSizeKeepsTheWidgetSize() {
        assertEquals(1000 to 500, cappedRenderSize(1000, 500, 0, 0))
        assertEquals(0 to 0, cappedRenderSize(0, 0, 1920, 1080))
    }

    @Test fun neverReturnsZero() = assertEquals(1 to 1, cappedRenderSize(1, 1, 1, 1))

    @Test fun parsesTheStatsResolution() {
        assertEquals(1920 to 1080, parseVideoSize("1920x1080@60"))
        assertEquals(640 to 360, parseVideoSize("640x360"))
        assertNull(parseVideoSize(null))
        assertNull(parseVideoSize("-"))
    }
}
