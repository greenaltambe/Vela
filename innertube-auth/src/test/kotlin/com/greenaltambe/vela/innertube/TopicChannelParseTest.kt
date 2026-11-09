package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.extractor.ChannelItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Auto-generated "Topic" channels (checked live 2026-11, "Palash Muchhal - Topic"): the Home tab is one "Albums & Singles" shelf whose items are
 * `lockupViewModel`s of type LOCKUP_CONTENT_TYPE_ALBUM. Only PLAYLIST was recognised, so every album was dropped and the channel looked empty.
 */
class TopicChannelParseTest {
    private val album = """{"lockupViewModel":{"contentId":"OLAK5uy_leJuBA9FO6xT75yBDUHUFhD47eCITo2Kc","contentType":"LOCKUP_CONTENT_TYPE_ALBUM",
        "contentImage":{"collectionThumbnailViewModel":{"primaryThumbnail":{"thumbnailViewModel":{"image":{"sources":[{"url":"https://i9.ytimg.com/s_p/OLAK/mqdefault.jpg","width":180,"height":180}]}}}}},
        "metadata":{"lockupMetadataViewModel":{"title":{"content":"Inshallah"}}}}}"""

    @Test fun albumsInAShelfAreListedAsPlaylists() {
        val json = lenientJson.parseToJsonElement("""{"contents":{"twoColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"selected":true,"content":{"sectionListRenderer":{"contents":[
            {"itemSectionRenderer":{"contents":[{"shelfRenderer":{"title":{"runs":[{"text":"Albums & Singles"}]},"content":{"horizontalListRenderer":{"items":[$album]}}}}]}}
        ]}}}}]}}}""")
        val page = ChannelBrowse().parseTabPage(json)
        val items = page.shelves.single().items
        val pl = assertIs<ChannelItem.Playlist>(items.single()).playlist
        assertEquals("Inshallah", pl.title)
        assertEquals("OLAK5uy_leJuBA9FO6xT75yBDUHUFhD47eCITo2Kc", pl.id)
    }

    @Test fun albumsInAGridAreListedAsPlaylists() {
        val json = lenientJson.parseToJsonElement("""{"contents":{"twoColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"selected":true,"content":{"richGridRenderer":{"contents":[
            {"richItemRenderer":{"content":$album}}
        ]}}}}]}}}""")
        val page = ChannelBrowse().parseTabPage(json)
        assertEquals("Inshallah", assertIs<ChannelItem.Playlist>(page.items.single()).playlist.title)
    }

    private fun header(vararg rows: String) = lenientJson.parseToJsonElement("""{"header":{"pageHeaderRenderer":{"content":{"pageHeaderViewModel":{
        "title":{"dynamicTextViewModel":{"text":{"content":"Adele - Topic"}}},
        "metadata":{"contentMetadataViewModel":{"metadataRows":[${rows.joinToString(",")}]}}}}}}}""")

    @Test fun theHeaderCarriesTheHandleAndTheVideoCount() {
        val h = ChannelBrowse().parseHeader("UC1", header(
            """{"metadataParts":[{"text":{"content":"@adele-topic"}}]}""",
            """{"metadataParts":[{"text":{"content":"56.6K subscribers"}},{"text":{"content":"71 videos"}}]}""",
        ))
        assertEquals("@adele-topic", h.handle)
        assertEquals(71L, h.videoCount)
        assertEquals(56_600L, h.subscriberCount)
    }

    @Test fun aHeaderWithOnlySubscribersLeavesTheRestUnknown() {
        val h = ChannelBrowse().parseHeader("UC1", header("""{"metadataParts":[{"text":{"content":"19.5K subscribers"}}]}"""))
        assertNull(h.handle)
        assertNull(h.videoCount)
        assertEquals(19_500L, h.subscriberCount)
    }
}
