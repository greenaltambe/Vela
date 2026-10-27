package com.greenaltambe.vela.extractor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class RssFeedTest {
    // Shape captured from https://www.youtube.com/feeds/videos.xml?channel_id=... (trimmed).
    private val xml = """<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns:media="http://search.yahoo.com/mrss/" xmlns="http://www.w3.org/2005/Atom">
 <title>Chan</title>
 <entry>
  <id>yt:video:abc123</id>
  <yt:videoId>abc123</yt:videoId>
  <yt:channelId>UCxyz</yt:channelId>
  <title>Fish &amp; Chips &quot;live&quot;</title>
  <link rel="alternate" href="https://www.youtube.com/watch?v=abc123"/>
  <author><name>Chan &amp; Co</name><uri>https://www.youtube.com/channel/UCxyz</uri></author>
  <published>2026-10-26T08:30:00+00:00</published>
  <updated>2026-10-26T09:00:00+00:00</updated>
  <media:group>
   <media:title>Fish</media:title>
   <media:thumbnail url="https://i.ytimg.com/vi/abc123/hqdefault.jpg" width="480" height="360"/>
   <media:community><media:starRating count="5" average="5.00" min="1" max="5"/><media:statistics views="12345"/></media:community>
  </media:group>
 </entry>
 <entry>
  <yt:videoId>short1</yt:videoId>
  <title>A short</title>
  <link rel="alternate" href="https://www.youtube.com/shorts/short1"/>
  <author><name>Chan</name></author>
  <published>2026-10-27T01:00:00+00:00</published>
 </entry>
</feed>"""

    @Test
    fun parsesEntriesWithDatesViewsThumbnailsAndEntities() {
        val items = parseChannelFeed(xml, "UCfallback")
        assertEquals(2, items.size)
        val first = items[0]
        assertEquals("abc123", first.id)
        assertEquals("Fish & Chips \"live\"", first.title)
        assertEquals("Chan & Co", first.channelName)
        assertEquals("UCxyz", first.channelId)
        assertEquals(12345L, first.viewCount)
        assertEquals("https://i.ytimg.com/vi/abc123/hqdefault.jpg", first.thumbnailUrl)
        assertEquals(Instant.parse("2026-10-26T08:30:00+00:00"), first.uploadedAt)
        assertEquals(false, first.isShort)
        assertNull(first.durationSeconds, "the feed carries no duration")
    }

    @Test
    fun shortsAreRecognisedFromTheLinkAndMissingFieldsFallBack() {
        val short = parseChannelFeed(xml, "UCfallback")[1]
        assertTrue(short.isShort)
        assertEquals("UCfallback", short.channelId)
        assertNull(short.viewCount)
    }

    @Test
    fun garbageIsEmptyNotACrash() {
        assertTrue(parseChannelFeed("<html>blocked</html>", "x").isEmpty())
        assertTrue(parseChannelFeed("", "x").isEmpty())
    }
}
