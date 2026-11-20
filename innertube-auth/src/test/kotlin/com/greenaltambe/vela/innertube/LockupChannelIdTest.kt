package com.greenaltambe.vela.innertube

import kotlin.test.Test
import kotlin.test.assertEquals

/** Hand-built from the documented lockup shape (not a recording): the channel id sits in a browseEndpoint on the avatar. */
class LockupChannelIdTest {
    private fun parse(json: String) = InnertubeParsers.videos(lenientJson.parseToJsonElement("""{"contents":[$json]}""")).single()

    @Test fun lockupReadsChannelIdFromAvatarBrowseEndpoint() {
        val v = parse("""{"lockupViewModel":{"contentType":"LOCKUP_CONTENT_TYPE_VIDEO","contentId":"vid",
            "metadata":{"lockupMetadataViewModel":{"title":{"content":"T"},
              "image":{"decoratedAvatarViewModel":{"rendererContext":{"commandContext":{"onTap":{"innertubeCommand":{"browseEndpoint":{"browseId":"UCabc123"}}}}}}}}},
            "rendererContext":{"commandContext":{"onTap":{"innertubeCommand":{"watchEndpoint":{"videoId":"vid"}}}}}}}""")
        assertEquals("UCabc123", v.channelId)
    }

    @Test fun lockupWithoutChannelLinkStaysBlank() {
        val v = parse("""{"lockupViewModel":{"contentType":"LOCKUP_CONTENT_TYPE_VIDEO","contentId":"vid","metadata":{"lockupMetadataViewModel":{"title":{"content":"T"}}}}}""")
        assertEquals("", v.channelId)
    }
}

class MembersOnlyParseTest {
    private fun parse(json: String) = InnertubeParsers.videos(lenientJson.parseToJsonElement("""{"contents":[$json]}""")).single()

    @Test fun classicBadgeStyleIsMembersOnly() {
        assertEquals(true, parse("""{"videoRenderer":{"videoId":"a","title":{"simpleText":"T"},"badges":[{"metadataBadgeRenderer":{"style":"BADGE_STYLE_TYPE_MEMBERS_ONLY","label":"Members only"}}]}}""").isMembersOnly)
        assertEquals(false, parse("""{"videoRenderer":{"videoId":"a","title":{"simpleText":"T"}}}""").isMembersOnly)
    }

    @Test fun lockupBadgeTextIsMembersOnly() {
        assertEquals(true, parse("""{"lockupViewModel":{"contentType":"LOCKUP_CONTENT_TYPE_VIDEO","contentId":"v","metadata":{"lockupMetadataViewModel":{"title":{"content":"T"}}},"contentImage":{"thumbnailViewModel":{"overlays":[{"thumbnailBadgeViewModel":{"text":"Members only"}}]}}}}""").isMembersOnly)
    }
}
