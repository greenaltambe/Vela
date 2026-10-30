package com.greenaltambe.vela.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class YoutubeLinkTest {
    private val id = "dQw4w9WgXcQ"

    @Test fun watchAndShortLinks() {
        assertEquals(YoutubeLink.Video(id), YoutubeLinks.parse("https://www.youtube.com/watch?v=$id"))
        assertEquals(YoutubeLink.Video(id, 83), YoutubeLinks.parse("https://youtu.be/$id?t=83"))
        assertEquals(YoutubeLink.Video(id, 83), YoutubeLinks.parse("https://m.youtube.com/watch?v=$id&t=1m23s"))
        assertEquals(YoutubeLink.Video(id), YoutubeLinks.parse("https://music.youtube.com/watch?v=$id&list=RDAMVM$id"))
    }

    @Test fun otherVideoForms() {
        for (p in listOf("shorts", "live", "embed")) assertEquals(YoutubeLink.Video(id), YoutubeLinks.parse("https://www.youtube.com/$p/$id"))
    }

    @Test fun playlistAndChannel() {
        assertEquals(YoutubeLink.Playlist("PLabcdefghij"), YoutubeLinks.parse("https://www.youtube.com/playlist?list=PLabcdefghij"))
        assertEquals(YoutubeLink.Channel("UCuAXFkgsw1L7xaCfnd5JJOw"), YoutubeLinks.parse("https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw"))
    }

    @Test fun notYoutube() {
        assertNull(YoutubeLinks.parse("https://example.com/watch?v=$id"))
        assertNull(YoutubeLinks.parse("https://evil.com/youtube.com/watch?v=$id"))
        assertNull(YoutubeLinks.parse("https://www.youtube.com/watch?v=short"))
        assertNull(YoutubeLinks.parse("file:///etc/passwd"))
    }

    @Test fun sharedMessage() {
        assertEquals(YoutubeLink.Video(id), YoutubeLinks.find("Cool song\nhttps://youtu.be/$id"))
        assertNull(YoutubeLinks.find("no link here"))
    }
}
