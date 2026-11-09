package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.extractor.ChannelItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The Shows tab of a channel (checked live on @MoreSidemen, 2026-11): each show is a `gridShowRenderer` holding a playlist. It used to parse as an empty tab. */
class ShowsTabParseTest {
    private val json = lenientJson.parseToJsonElement("""{"contents":{"twoColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"selected":true,"content":{"richGridRenderer":{"contents":[
        {"richItemRenderer":{"content":{"gridShowRenderer":{"title":{"simpleText":"SIDE-MENU"},
          "thumbnailRenderer":{"showCustomThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"https://i.ytimg.com/vi/RQ2XsmVIkDE/hqdefault.jpg","width":480,"height":270}]}}},
          "navigationEndpoint":{"browseEndpoint":{"browseId":"VLPLhjLcvbbPVrVOKUpVlMTweObqLoDQQcL2","params":"kgYCQAE%3D"}},
          "shortBylineText":{"runs":[{"text":"MoreSidemen","navigationEndpoint":{"browseEndpoint":{"browseId":"UCh5mLn90vUaB1PbRRx_AiaA"}}}]},
          "thumbnailOverlays":[{"thumbnailOverlayBottomPanelRenderer":{"text":{"runs":[{"text":"4"},{"text":" episodes"}]}}}]}}}},
        {"richItemRenderer":{"content":{"gridShowRenderer":{"title":{"simpleText":"Second show"},"navigationEndpoint":{"browseEndpoint":{"browseId":"VLPLsecond"}}}}}}
    ]}}}}]}}}""")

    @Test fun showsAreListedAsPlaylistsWithTheirEpisodeCount() {
        val page = ChannelBrowse().parseTabPage(json)
        val shows = page.items.map { assertIs<ChannelItem.Playlist>(it).playlist }
        assertEquals(listOf("SIDE-MENU", "Second show"), shows.map { it.title })
        assertEquals(listOf("PLhjLcvbbPVrVOKUpVlMTweObqLoDQQcL2", "PLsecond"), shows.map { it.id }, "the VL prefix of the browse id is not part of the playlist id")
        assertEquals("https://i.ytimg.com/vi/RQ2XsmVIkDE/hqdefault.jpg", shows[0].thumbnailUrl)
        assertEquals(4L, shows[0].videoCount)
    }
}
