package com.greenaltambe.vela.innertube

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Phase 1 of task 83. The JSON is hand-built from the documented shapes, NOT a recording (see the report). */
class WatchedPercentParseTest {
    private fun classic(id: String, overlay: String) =
        """{"videoRenderer":{"videoId":"$id","title":{"simpleText":"T$id"},"thumbnailOverlays":[$overlay]}}"""

    private fun lockup(id: String, overlay: String) =
        """{"lockupViewModel":{"contentType":"LOCKUP_CONTENT_TYPE_VIDEO","contentId":"$id",
            "metadata":{"lockupMetadataViewModel":{"title":{"content":"T$id"}}},
            "contentImage":{"thumbnailViewModel":{"image":{"sources":[{"url":"https://t/$id.jpg"}]},"overlays":[$overlay]}}}}"""

    private fun parse(vararg items: String) =
        InnertubeParsers.videos(lenientJson.parseToJsonElement("""{"contents":[${items.joinToString(",")}]}""")).associateBy { it.id }

    @Test fun classicRendererReadsPercentDurationWatched() {
        val v = parse(
            classic("full", """{"thumbnailOverlayResumePlaybackRenderer":{"percentDurationWatched":100}}"""),
            classic("part", """{"thumbnailOverlayResumePlaybackRenderer":{"percentDurationWatched":37}}"""),
            classic("none", """{"thumbnailOverlayTimeStatusRenderer":{"text":{"simpleText":"1:00"}}}"""),
        )
        assertEquals(100, v["full"]!!.watchedPercent)
        assertEquals(37, v["part"]!!.watchedPercent)
        assertNull(v["none"]!!.watchedPercent)
    }

    @Test fun lockupReadsProgressBarStartPercent() {
        val v = parse(
            lockup("full", """{"thumbnailOverlayProgressBarViewModel":{"startPercent":100}}"""),
            lockup("part", """{"thumbnailBottomOverlayViewModel":{"progressBar":{"thumbnailOverlayProgressBarViewModel":{"startPercent":42}}}}"""),
            lockup("none", """{"thumbnailBadgeViewModel":{"text":"3:00"}}"""),
        )
        assertEquals(100, v["full"]!!.watchedPercent)
        assertEquals(42, v["part"]!!.watchedPercent)
        assertNull(v["none"]!!.watchedPercent)
    }

    @Test fun outOfRangeValuesAreClamped() {
        val v = parse(classic("big", """{"thumbnailOverlayResumePlaybackRenderer":{"percentDurationWatched":250}}"""))
        assertEquals(100, v["big"]!!.watchedPercent)
    }
}
