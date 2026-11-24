package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.auth.HomeChipKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Hand-built from the shapes seen on a signed-in Home (not a recording; labels, ids and tokens are made up). */
class HomeChipsParseTest {
    private fun json(s: String) = lenientJson.parseToJsonElement(s)

    private fun chip(label: String, token: String? = null, unique: String? = null, selected: Boolean = false) =
        """{"chipCloudChipRenderer":{"style":{"styleType":"STYLE_HOME_FILTER"},"text":{"runs":[{"text":"$label"}]},
            ${token?.let { """"navigationEndpoint":{"continuationCommand":{"token":"$it","request":"CONTINUATION_REQUEST_TYPE_BROWSE"}},""" } ?: ""}
            ${unique?.let { """"uniqueId":"$it",""" } ?: ""}
            "isSelected":$selected}}"""

    /** Chips come first in the document, the list (and its own continuation item) after them. */
    private val home = json("""{"contents":{"richGridRenderer":{
        "header":{"feedFilterChipBarRenderer":{"contents":[
          ${chip("Make a feed", "tok-create", "ATTRIBUTE_FILTER_TYPE_CUSTOM_FEED_CREATE_FEED")},
          ${chip("All", selected = true)},
          ${chip("My feed", "tok-custom", "ATTRIBUTE_FILTER_TYPE_CUSTOM_FEED_1")},
          ${chip("Topic A", "tok-a")},
          ${chip("New to you", "tok-new", "ATTRIBUTE_FILTER_TYPE_EXPLORE")}
        ]}},
        "contents":[
          {"richItemRenderer":{"content":{"videoRenderer":{"videoId":"v1","title":{"simpleText":"One"}}}}},
          {"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"tok-feed-next"}}}}
        ]}}}""")

    @Test fun chipsComeInYouTubesOrderWithTheirKinds() {
        val chips = InnertubeParsers.homeChips(home)
        assertEquals(listOf("Make a feed", "All", "My feed", "Topic A", "New to you"), chips.map { it.label })
        assertEquals(
            listOf(HomeChipKind.CREATE_CUSTOM_FEED, HomeChipKind.ALL, HomeChipKind.CUSTOM_FEED, HomeChipKind.TOPIC, HomeChipKind.TOPIC),
            chips.map { it.kind },
        )
        assertEquals(listOf(false, true, false, false, false), chips.map { it.selected })
    }

    @Test fun theSelectedAllChipHasNoTokenAndTheOthersDo() {
        val chips = InnertubeParsers.homeChips(home)
        assertNull(chips.single { it.kind == HomeChipKind.ALL }.token)
        assertEquals("tok-custom", chips.single { it.kind == HomeChipKind.CUSTOM_FEED }.token)
        assertEquals("tok-a", chips.first { it.label == "Topic A" }.token)
    }

    @Test fun feedContinuationIgnoresTheChipTokensThatComeFirst() {
        // The old reader returned the first continuation command anywhere: a chip's token.
        assertEquals("tok-create", InnertubeParsers.continuationToken(home))
        assertEquals("tok-feed-next", InnertubeParsers.feedContinuation(home))
    }

    @Test fun aPageWithoutAChipBarHasNoChips() {
        assertTrue(InnertubeParsers.homeChips(json("""{"contents":[{"videoRenderer":{"videoId":"v","title":{"simpleText":"T"}}}]}""")).isEmpty())
        assertNull(InnertubeParsers.feedContinuation(json("""{"contents":[]}""")))
    }

    @Test fun otherChipCloudsOutsideTheHomeBarAreNotHomeChips() {
        val page = json("""{"header":{"chipCloudRenderer":{"chips":[${chip("Filter", "t")}]}}}""")
        assertTrue(InnertubeParsers.homeChips(page).isEmpty())
    }
}

class LiveLockupParseTest {
    private fun parse(badge: String) = InnertubeParsers.videos(lenientJson.parseToJsonElement(
        """{"contents":[{"lockupViewModel":{"contentType":"LOCKUP_CONTENT_TYPE_VIDEO","contentId":"vid","metadata":{"lockupMetadataViewModel":{"title":{"content":"T"}}},
            "contentImage":{"thumbnailViewModel":{"overlays":[{"thumbnailBottomOverlayViewModel":{"badges":[{"thumbnailBadgeViewModel":$badge}]}}]}}}}]}""")).single()

    @Test fun aLiveBadgeMarksTheVideoLiveAndLeavesNoDuration() {
        val v = parse("""{"text":"LIVE","badgeStyle":"THUMBNAIL_OVERLAY_BADGE_STYLE_LIVE"}""")
        assertTrue(v.isLive)
        assertNull(v.durationSeconds)
    }

    @Test fun aDurationBadgeIsNotLive() {
        val v = parse("""{"text":"12:34","badgeStyle":"THUMBNAIL_OVERLAY_BADGE_STYLE_DEFAULT"}""")
        assertEquals(false, v.isLive)
        assertEquals(12L * 60 + 34, v.durationSeconds)
    }
}
