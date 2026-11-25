package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.auth.FeedGroupKind
import kotlin.test.*

/** The Home parser on hand-built responses that mirror the shapes seen live (every id, title and token is made up). */
class HomeFeedParserTest {
    // ---- the pieces ----

    private fun lockup(id: String, type: String = "LOCKUP_CONTENT_TYPE_VIDEO", extra: String = "") =
        """{"lockupViewModel":{"contentId":"$id","contentType":"$type","metadata":{"lockupMetadataViewModel":{"title":{"content":"T $id"}}}$extra}}"""
    private fun item(content: String) = """{"richItemRenderer":{"content":$content}}"""
    private fun video(id: String) = item(lockup(id))
    private fun classic(id: String) = item("""{"videoRenderer":{"videoId":"$id","title":{"simpleText":"T $id"}}}""")
    private fun short(id: String) = """{"shortsLockupViewModel":{"entityId":"shorts-shelf-item-$id",
        "onTap":{"innertubeCommand":{"reelWatchEndpoint":{"videoId":"$id","sequenceParams":"seq-$id"}}},
        "overlayMetadata":{"primaryText":{"content":"S $id"},"secondaryText":{"content":"12K views"}}}}"""
    private fun shelf(title: String, vararg contents: String, subtitle: String? = null, rest: String = "") =
        """{"richSectionRenderer":{"content":{"richShelfRenderer":{"title":$title,${subtitle?.let { """"subtitle":{"runs":[{"text":"$it"}]},""" }.orEmpty()}"contents":[${contents.joinToString(",")}]$rest}}}}"""
    private fun simple(t: String) = """{"simpleText":"$t"}"""
    private fun runs(t: String) = """{"runs":[{"text":"$t"}]}"""
    private fun next(token: String) = """{"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"$token"}}}}"""
    private val ad = item("""{"adSlotRenderer":{"fulfillmentContent":{"videoRenderer":{"videoId":"advert","title":{"simpleText":"Buy"}}}}}""")

    private fun first(vararg items: String) = """{"contents":{"twoColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"richGridRenderer":{
        "header":{"feedFilterChipBarRenderer":{"contents":[]}},"contents":[${items.joinToString(",")}]}}}}]}}}"""
    private fun appended(vararg items: String) = """{"onResponseReceivedActions":[{"appendContinuationItemsAction":{"continuationItems":[${items.joinToString(",")}]}}]}"""
    private fun reloaded(vararg items: String) = """{"onResponseReceivedActions":[
        {"reloadContinuationItemsCommand":{"slot":"RELOAD_CONTINUATION_SLOT_HEADER","continuationItems":[${video("header-video")}]}},
        {"reloadContinuationItemsCommand":{"slot":"RELOAD_CONTINUATION_SLOT_BODY","continuationItems":[${items.joinToString(",")}]}}]}"""

    private fun parse(json: String) = HomeFeedParser.parse(lenientJson.parseToJsonElement(json)).also {
        if (it.report.fallback == null) assertEquals(it.report.items, it.report.accounted, "every top-level item is counted once: ${it.report}")
    }
    private fun HomeFeed.ids() = videos.map { it.id }

    // ---- the pages ----

    @Test fun theFirstPageKeepsGroupsOutOfTheFeedAndRemembersWhereTheyWere() {
        val page = parse(first(
            video("a1"), shelf(runs("Shorts"), item(short("s1")), item(short("s2"))), video("a2"), ad,
            shelf(simple("A game"), video("g1"), video("g2"), subtitle = "More from this game"), classic("a3"), next("n1"),
        ))
        assertEquals(listOf("a1", "a2", "a3"), page.ids())
        assertEquals(listOf("Shorts", "A game"), page.groups.map { it.title })
        assertEquals(listOf(FeedGroupKind.SHORTS, FeedGroupKind.SUGGESTED), page.groups.map { it.kind })
        assertEquals(listOf("a1", "a2"), page.groups.map { it.afterVideoId })
        assertEquals("More from this game", page.groups[1].subtitle)
        assertEquals(listOf("g1", "g2"), page.groups[1].videos.map { it.id })
        assertEquals("n1", page.continuation)
        assertNull(page.report.problem(), "an ordinary page is no problem: ${page.report}")
        assertEquals(1, page.report.ads)
    }

    @Test fun aShortCarriesItsTitleViewsAndSequenceAndIsMarkedShort() {
        val s = parse(first(video("a1"), shelf(runs("Shorts"), item(short("s1"))))).groups.single().videos.single()
        assertTrue(s.isShort)
        assertEquals("s1", s.id); assertEquals("S s1", s.title); assertEquals(12_000L, s.viewCount); assertEquals("seq-s1", s.shortsSequence)
        assertEquals("", s.channelId, "YouTube names no channel here")
    }

    @Test fun aCardThatOpensTheShortsPlayerIsAShortWhereverItIs() {
        val tap = ""","rendererContext":{"commandContext":{"onTap":{"innertubeCommand":{"reelWatchEndpoint":{"videoId":"r1","sequenceParams":"seq-r1"}}}}}"""
        val menuOnly = ""","menu":{"x":{"reelWatchEndpoint":{"videoId":"other"}}}"""
        val page = parse(first(item(lockup("r1", extra = tap)), item(lockup("v1", extra = menuOnly)), item(short("s9"))))
        assertEquals(listOf(true, false, true), page.videos.map { it.isShort }, "only the card's own tap counts")
        assertEquals("seq-r1", page.videos.first().shortsSequence)
    }

    @Test fun anAppendedPageIsReadTheSameWayAndAGroupAtItsTopHasNoAnchor() {
        val page = parse(appended(shelf(simple("Early"), video("g1")), video("b1"), next("n2")))
        assertEquals(listOf("b1"), page.ids())
        assertNull(page.groups.single().afterVideoId)
        assertEquals("n2", page.continuation)
    }

    @Test fun aChipsPageReadsItsBodyAndNotTheHeaderSlot() {
        val page = parse(reloaded(video("c1"), shelf(simple("One"), video("g1")), video("c2"), next("chip-next")))
        assertEquals(listOf("c1", "c2"), page.ids())
        assertEquals("c1", page.groups.single().afterVideoId)
        assertEquals("chip-next", page.continuation)
    }

    @Test fun aGroupMixingShortsAndVideosIsAnOrdinaryGroupWhoseShortsStayMarked() {
        val g = parse(first(video("a1"), shelf(runs("Mixed"), video("g1"), item(short("s1"))))).groups.single()
        assertEquals(FeedGroupKind.SUGGESTED, g.kind)
        assertEquals(listOf(false, true), g.videos.map { it.isShort })
    }

    @Test fun aKindOfSectionNeverSeenBeforeIsStillAGroup() {
        val odd = """{"richSectionRenderer":{"content":{"mostHypedViewModel":{"header":{"x":{"title":{"content":"Most hyped"}}},"rows":[{"card":${lockup("h1")}},{"card":${lockup("h2")}}]}}}}"""
        val page = parse(first(video("a1"), odd, video("a2")))
        assertEquals(listOf("a1", "a2"), page.ids(), "its videos stay out of the feed")
        assertEquals("Most hyped", page.groups.single().title)
        assertEquals(listOf("h1", "h2"), page.groups.single().videos.map { it.id })
    }

    @Test fun aGroupWithoutATitleGetsAGeneralOne() {
        val untitled = """{"richSectionRenderer":{"content":{"richShelfRenderer":{"contents":[${video("g1")}]}}}}"""
        assertEquals("More from YouTube", parse(first(video("a1"), untitled)).groups.single().title)
    }

    @Test fun sectionsWithoutVideosAreLeftOutAndCountedByName() {
        val games = shelf(runs("Playables"), item("""{"miniGameCardViewModel":{"title":"Game"}}"""))
        val nudge = """{"richSectionRenderer":{"content":{"feedNudgeRenderer":{}}}}"""
        assertNull(parse(first(video("a1"), """{"feedCustomizationFormViewModel":{}}""")).report.problem(), "the custom feed's form is expected")
        val page = parse(first(video("a1"), games, nudge, item(lockup("mix1", "LOCKUP_CONTENT_TYPE_PLAYLIST"))))
        assertEquals(listOf("a1"), page.ids())
        assertTrue(page.groups.isEmpty())
        assertEquals(mapOf("section:richShelfRenderer" to 1, "section:feedNudgeRenderer" to 1, "lockupViewModel:LOCKUP_CONTENT_TYPE_PLAYLIST" to 1), page.report.skipped)
        assertNull(page.report.problem())
    }

    @Test fun theNextPageTokenComesFromTheListNotFromInsideAGroup() {
        val inside = shelf(simple("Paged"), video("g1"), next("group-token"))
        assertEquals("list-token", parse(first(inside, video("a1"), next("list-token"))).continuation)
        assertNull(parse(first(inside, video("a1"))).continuation, "a group's own token is never the feed's")
    }

    @Test fun aBrokenItemCostsOnlyItselfAndIsReported() {
        val noTitle = item("""{"lockupViewModel":{"contentId":"x","contentType":"LOCKUP_CONTENT_TYPE_VIDEO"}}""")
        val rubbish = """{"richItemRenderer":42}"""
        val page = parse(first(video("a1"), noTitle, rubbish, video("a2"), next("n")))
        assertEquals(listOf("a1", "a2"), page.ids()); assertEquals("n", page.continuation)
        assertEquals(1, page.report.broken)
        assertNotNull(page.report.problem())
    }

    @Test fun anUnknownTopLevelItemIsReportedAndItsVideosNeverReachTheFeed() {
        val odd = """{"brandNewThingRenderer":{"title":{"simpleText":"New"},"items":[${video("n1")}]}}"""
        val empty = """{"otherNewThingRenderer":{}}"""
        val page = parse(first(video("a1"), odd, empty))
        assertEquals(listOf("a1"), page.ids())
        assertEquals(listOf("n1"), page.groups.single().videos.map { it.id })
        assertEquals(mapOf("brandNewThingRenderer" to 1, "otherNewThingRenderer" to 1), page.report.unknown)
        assertTrue("brandNewThingRenderer" in page.report.problem()!!)
    }

    @Test fun anAdvertisementNeverBecomesAVideo() {
        val inShelf = shelf(simple("G"), video("g1"), ad)
        val page = parse(first(ad, video("a1"), inShelf, """{"adSlotRenderer":{"x":${lockup("ad2")}}}"""))
        assertEquals(listOf("a1"), page.ids())
        assertEquals(listOf("g1"), page.groups.single().videos.map { it.id })
    }

    @Test fun aRepeatedVideoIsListedOnce() {
        assertEquals(listOf("a1", "a2"), parse(first(video("a1"), video("a2"), video("a1"))).ids())
    }

    @Test fun anEmptyListIsAnEndNotAProblem() {
        for (json in listOf(first(), appended())) {
            val page = parse(json)
            assertTrue(page.videos.isEmpty() && page.groups.isEmpty()); assertNull(page.continuation); assertNull(page.report.problem())
        }
    }

    @Test fun aLayoutWithoutTheExpectedListIsReadTheOldWayAndSaysSo() {
        val page = parse("""{"contents":{"someOtherGrid":{"rows":[${video("a1")},${video("a2")},${next("n1")}]}}}""")
        assertEquals(listOf("a1", "a2"), page.ids()); assertEquals("n1", page.continuation)
        assertTrue(page.groups.isEmpty())
        assertTrue("old way" in page.report.problem()!!)
    }

    @Test fun aListNothingCanBeReadFromFallsBackToo() {
        val renamed = """{"richItemRendererV2":{"content":${lockup("a1")}}}"""
        val page = parse(first(renamed, renamed.replace("a1", "a2")))
        assertEquals(listOf("a1", "a2"), page.ids(), "better flat than empty")
        assertNotNull(page.report.fallback)
    }

    @Test fun aPageThatIsOnlyKnownGroupsIsNotAFallback() {
        val page = parse(appended(shelf(simple("Only"), video("g1")), next("n")))
        assertTrue(page.videos.isEmpty()); assertEquals(1, page.groups.size); assertNull(page.report.fallback); assertEquals("n", page.continuation)
    }

    @Test fun nonsenseDoesNotThrow() {
        for (json in listOf("{}", "[]", "\"x\"", """{"onResponseReceivedActions":[{}]}""", """{"contents":{"richGridRenderer":{"contents":[null,[],"x",{}]}}}""")) {
            val page = HomeFeedParser.parse(lenientJson.parseToJsonElement(json))
            assertTrue(page.videos.isEmpty() && page.groups.isEmpty(), json)
        }
    }
}
