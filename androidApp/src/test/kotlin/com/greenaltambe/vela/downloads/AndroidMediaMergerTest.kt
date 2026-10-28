package com.greenaltambe.vela.downloads

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidMediaMergerTest {
    private val m = AndroidMediaMerger()

    @Test fun vp9WithAacHasNoWorkingContainerOnAndroid() = assertFalse(m.canMerge("vp09.00.10.08", "mp4a.40.2"))
    @Test fun h264WithAacAndVp9WithOpusMerge() {
        assertTrue(m.canMerge("avc1.4d401f", "mp4a.40.2"))
        assertTrue(m.canMerge("vp09.00.10.08", "opus"))
    }
}
