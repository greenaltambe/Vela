package com.greenaltambe.vela.ui.nav

import com.greenaltambe.vela.extractor.VideoSummary
import kotlin.test.*

class NowPlayingSnapshotTest {
    @Test fun roundTripsAndKeepsOnlyWhatTheMiniPlayerNeeds() {
        val v = VideoSummary("abc", "A\ttitle\nwith breaks", "UC1", "Chan", "https://t/x.jpg", durationSeconds = 5)
        val back = assertNotNull(NowPlayingSnapshot.decode(NowPlayingSnapshot.encode(v)))
        assertEquals("A title with breaks", back.title); assertEquals("abc", back.id); assertEquals("https://t/x.jpg", back.thumbnailUrl)
    }
    @Test fun emptyOrBrokenIsNothingAndRestoreNeverReplacesWhatIsThere() {
        assertNull(NowPlayingSnapshot.decode("")); assertNull(NowPlayingSnapshot.decode("only\tfour\tfields\there"))
        val n = Navigator(); n.restore(VideoSummary("a", "A", "c", "C")); n.restore(VideoSummary("b", "B", "c", "C"))
        assertEquals("a", n.nowPlaying?.id)
    }
}
