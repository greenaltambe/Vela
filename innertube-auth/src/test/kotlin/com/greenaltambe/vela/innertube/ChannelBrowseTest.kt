package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.extractor.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.*

class ChannelBrowseTest {
    private fun fixture(name: String): String =
        ChannelBrowseTest::class.java.getResource("/fixtures/channel/$name")!!.readText()

    private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun parseLandingExtractsHeaderTabsAndInitialContent() {
        val json = lenientJson.parseToJsonElement(fixture("channel_landing.json"))
        val browse = ChannelBrowse()
        val landing = browse.parseLanding("UCEBb1b_L6zDS3xTUrIALZOw", json)

        assertEquals("UCEBb1b_L6zDS3xTUrIALZOw", landing.header.id)
        assertTrue(landing.header.name.isNotBlank(), "channel title present")
        assertNotNull(landing.header.avatarUrl, "avatar present")
        assertNotNull(landing.header.subscriberCount, "subs count present")
        assertTrue(landing.header.subscriberCount!! > 1_000_000)

        // Tabs parsed by URL segment, never localized title
        assertTrue(landing.tabs.isNotEmpty())
        val kinds = landing.tabs.map { it.kind }
        assertTrue(ChannelTabKind.VIDEOS in kinds, "Videos tab present")
        assertTrue(ChannelTabKind.PLAYLISTS in kinds, "Playlists tab present")
        assertTrue(ChannelTabKind.POSTS in kinds, "Posts tab present")
        assertTrue(ChannelTabKind.SEARCH in kinds, "Search tab present")

        // About token in header
        assertNotNull(landing.aboutToken, "about continuation token found in header")

        // Initial tab content
        assertNotNull(landing.initialPage, "initial tab page parsed")
        assertTrue(landing.initialPage!!.items.isNotEmpty() || landing.initialPage!!.shelves.isNotEmpty())
        val shortsShelf = landing.initialPage!!.shelves.firstOrNull { it.title.contains("Short", ignoreCase = true) }
        assertNotNull(shortsShelf, "shorts shelf present on landing")
        assertTrue(shortsShelf.items.isNotEmpty(), "shorts shelf has items")
        val firstShort = shortsShelf.items.first() as ChannelItem.Video
        assertTrue(firstShort.summary.isShort, "item is marked as short")
        assertEquals("KBfIo9sZbC4", firstShort.summary.id)
        assertEquals("How Does Ketamine Work?", firstShort.summary.title)
        assertNotNull(firstShort.summary.thumbnailUrl)
    }

    @Test
    fun parseShortParsesReelItemRendererAndShortsLockupViewModel() {
        val browse = ChannelBrowse()

        // 1. Classic reelItemRenderer
        val reelJson = lenientJson.parseToJsonElement("""
            {
                "videoId": "abc12345",
                "headline": { "simpleText": "Classic Short Title" },
                "viewCountText": { "simpleText": "1.5M views" },
                "thumbnail": {
                    "thumbnails": [
                        { "url": "https://example.invalid/short.jpg", "width": 720, "height": 1280 }
                    ]
                }
            }
        """.trimIndent())
        val short1 = browse.parseShort(reelJson)
        assertNotNull(short1)
        assertEquals("abc12345", short1.id)
        assertEquals("Classic Short Title", short1.title)
        assertEquals(1_500_000L, short1.viewCount)
        assertEquals("https://example.invalid/short.jpg", short1.thumbnailUrl)
        assertTrue(short1.isShort)

        // 2. Modern shortsLockupViewModel
        val lockupJson = lenientJson.parseToJsonElement("""
            {
                "entityId": "shorts-shelf-item-xyz789",
                "accessibilityText": "Modern Short Title, 500K views - play Short",
                "overlayMetadata": {
                    "primaryText": { "content": "Modern Short Title" },
                    "secondaryText": { "content": "500K views" }
                },
                "thumbnailViewModel": {
                    "thumbnailViewModel": {
                        "image": {
                            "sources": [
                                { "url": "https://example.invalid/modern.jpg", "width": 405, "height": 720 }
                            ]
                        }
                    }
                }
            }
        """.trimIndent())
        val short2 = browse.parseShort(lockupJson)
        assertNotNull(short2)
        assertEquals("xyz789", short2.id)
        assertEquals("Modern Short Title", short2.title)
        assertEquals(500_000L, short2.viewCount)
        assertEquals("https://example.invalid/modern.jpg", short2.thumbnailUrl)
        assertTrue(short2.isShort)
    }

    @Test
    fun parseVideosTabExtractsSortChipsAndItems() {
        val json = lenientJson.parseToJsonElement(fixture("videos_tab.json"))
        val browse = ChannelBrowse()
        val page = browse.parseTabPage(json)

        assertTrue(page.items.isNotEmpty())
        assertTrue(page.items.all { it is ChannelItem.Video })
        assertNotNull(page.nextToken, "paging token inside list extracted")

        // Filter chips (Latest, Popular, Oldest)
        assertTrue(page.filters.isNotEmpty())
        val sortGroup = page.filters.first()
        val labels = sortGroup.options.map { it.label }
        assertTrue(labels.any { it.contains("Latest", true) })
        assertTrue(labels.any { it.contains("Popular", true) })
        assertTrue(labels.any { it.contains("Oldest", true) })

        val latestOption = sortGroup.options.first { it.label.contains("Latest", true) }
        assertTrue(latestOption.selected, "Latest is selected by default")
        assertNotNull(latestOption.token, "token present for sort chip")
    }

    @Test
    fun parseOldestVideosReturnsOldestFirst() {
        val json = lenientJson.parseToJsonElement(fixture("videos_sorted_oldest.json"))
        val browse = ChannelBrowse()
        val page = browse.parseTabPage(json)

        assertTrue(page.items.isNotEmpty())
        val firstVideo = (page.items.first() as ChannelItem.Video).summary
        // Kurzgesagt's first recorded oldest video
        assertEquals("hOfRN0KihOU", firstVideo.id)
        assertTrue(firstVideo.title.contains("Evolution", true) || firstVideo.title.isNotBlank())
        assertNotNull(page.nextToken, "paging continuation token inside list")

        val sortGroup = page.filters.first()
        val oldestOption = sortGroup.options.first { it.label.contains("Oldest", true) }
        assertTrue(oldestOption.selected, "Oldest is marked selected")
    }

    @Test
    fun parseCoursesTabDetectsCoursesAndLessonCounts() {
        val json = lenientJson.parseToJsonElement(fixture("courses_tab.json"))
        val browse = ChannelBrowse()
        val page = browse.parseTabPage(json)

        assertTrue(page.items.isNotEmpty(), "courses parsed")
        val playlists = page.items.filterIsInstance<ChannelItem.Playlist>().map { it.playlist }
        assertTrue(playlists.isNotEmpty())
        val course = playlists.firstOrNull { it.isCourse }
        assertNotNull(course, "at least one playlist recognized as course")
        assertTrue(course.isCourse)
        assertNotNull(course.lessonCountText, "lesson count extracted")
        assertTrue(course.lessonCountText!!.contains("lesson", true))
    }

    @Test
    fun parsePostsTabExtractsBackstagePosts() {
        val json = lenientJson.parseToJsonElement(fixture("posts_tab.json"))
        val browse = ChannelBrowse()
        val page = browse.parseTabPage(json)

        assertTrue(page.items.isNotEmpty())
        val posts = page.items.filterIsInstance<ChannelItem.Post>().map { it.post }
        assertTrue(posts.isNotEmpty(), "posts extracted")
        val first = posts.first()
        assertTrue(first.id.isNotBlank())
        assertTrue(first.text.isNotBlank())
        assertNotNull(first.publishedText)
    }

    @Test
    fun parsePlaylistsTabExtractsDropdownSubmenuWithParams() {
        val json = lenientJson.parseToJsonElement(fixture("playlists_tab.json"))
        val browse = ChannelBrowse()
        val page = browse.parseTabPage(json)

        assertTrue(page.items.isNotEmpty())
        assertTrue(page.items.all { it is ChannelItem.Playlist })

        // Playlists tab uses sortFilterSubMenuRenderer with params
        val filterGroup = page.filters.firstOrNull { it.isDropdown }
        assertNotNull(filterGroup, "dropdown filter group found")
        assertTrue(filterGroup.options.size >= 2)
        assertTrue(filterGroup.options.any { it.params != null }, "options carry fresh browse params")
    }

    @Test
    fun parseAboutPanelExtractsFullChannelDetailsAndLinks() {
        val json = lenientJson.parseToJsonElement(fixture("about_panel.json"))
        val browse = ChannelBrowse()
        val about = browse.parseAbout(json, "UCEBb1b_L6zDS3xTUrIALZOw")

        assertTrue(about.description.isNotBlank())
        assertNotNull(about.joinedDateText)
        assertNotNull(about.viewCountText)
        assertTrue(about.viewCountText!!.contains("views", true))
        assertNotNull(about.country)
        assertTrue(about.links.isNotEmpty(), "external channel links extracted")
        assertTrue(about.links.any { it.url.contains("http") })
    }

    @Test
    fun parsePostDetailExtractsPostAndCommentsContinuation() {
        val json = lenientJson.parseToJsonElement(fixture("post_detail.json"))
        val browse = ChannelBrowse()
        val (post, commentsToken) = browse.parsePostDetail(json)!!

        assertTrue(post.id.isNotBlank())
        assertTrue(post.text.isNotBlank())
        assertNotNull(commentsToken, "comments continuation token present")
    }

    @Test
    fun parseSearchTabExtractsSearchResults() {
        val json = lenientJson.parseToJsonElement(fixture("search_tab.json"))
        val browse = ChannelBrowse()
        val page = browse.parseTabPage(json)

        assertTrue(page.items.isNotEmpty(), "search results parsed")
        assertTrue(page.items.any { it is ChannelItem.Video })
    }

    @Test
    fun unknownRenderersAreCountedAndDoNotCrash() {
        val unknownRenderers = mutableListOf<String>()
        val browse = ChannelBrowse(onUnknownRenderer = { unknownRenderers += it })

        val dummyJson = lenientJson.parseToJsonElement("""
            {
                "contents": {
                    "twoColumnBrowseResultsRenderer": {
                        "tabs": [
                            {
                                "tabRenderer": {
                                    "endpoint": { "commandMetadata": { "webCommandMetadata": { "url": "/@test/videos" } } },
                                    "title": "Videos",
                                    "content": {
                                        "richGridRenderer": {
                                            "contents": [
                                                { "richItemRenderer": { "content": { "newFuturisticRenderer": { "id": "123" } } } }
                                            ]
                                        }
                                    }
                                }
                            }
                        ]
                    }
                }
            }
        """.trimIndent())

        val page = browse.parseTabPage(dummyJson)
        assertTrue(page.items.isEmpty())
        assertTrue(unknownRenderers.any { it.contains("newFuturisticRenderer") })
    }

    @Test
    fun cachingWorksForLandingAndTabs() = runTest {
        var networkCalls = 0
        val fakeTransport = FakeTransport { method, url, headers, body ->
            networkCalls++
            RawResponse(200, fixture("channel_landing.json"), emptyMap(), "")
        }

        val browse = ChannelBrowse(transport = fakeTransport)
        val res1 = browse.landing("UCEBb1b_L6zDS3xTUrIALZOw")
        assertIs<ExtractorResult.Success<*>>(res1)
        assertEquals(1, networkCalls)

        // Second call within TTL returns cached result without network request
        val res2 = browse.landing("UCEBb1b_L6zDS3xTUrIALZOw")
        assertIs<ExtractorResult.Success<*>>(res2)
        assertEquals(1, networkCalls)
    }

    @Test
    fun continuationWithForeignTokenReturnsTokenNotMine() = runTest {
        var networkCalls = 0
        val fakeTransport = FakeTransport { _, _, _, _ ->
            networkCalls++
            RawResponse(200, fixture("videos_tab.json"), emptyMap(), "")
        }

        val browse = ChannelBrowse(transport = fakeTransport)
        val tab = ChannelTab(ChannelTabKind.VIDEOS, "Videos", "videos_params", true)
        val res = browse.tab("UC123", tab, continuation = "foreign_or_np_token")
        assertIs<ExtractorResult.Failure>(res)
        assertIs<ExtractorError.TokenNotMine>(res.error)
        assertEquals(0, networkCalls, "Foreign token must not trigger network call")
    }

    @Test
    fun http400ContinuationReturnsTokenExpiredWithoutInternalRetry() = runTest {
        var attempts = 0
        val fakeTransport = FakeTransport { _, _, _, _ ->
            attempts++
            RawResponse(400, """{"error":{"code":400,"message":"Precondition check failed."}}""", emptyMap(), "")
        }

        val browse = ChannelBrowse(transport = fakeTransport)
        val tab = ChannelTab(ChannelTabKind.VIDEOS, "Videos", "videos_params", true)
        val res = browse.tab("UC123", tab, continuation = "n1:expired_token_400")
        assertIs<ExtractorResult.Failure>(res)
        assertIs<ExtractorError.TokenExpired>(res.error)
        assertEquals(1, attempts, "Must not retry page 1 on continuation 400")
    }

    @Test
    fun emptyParseWithoutRecognisedEmptyStateReturnsParseFailure() = runTest {
        val fakeTransport = FakeTransport { _, _, _, _ ->
            // JSON with contents but unrecognized renderers, not an empty-state message
            RawResponse(200, """
                {
                    "contents": {
                        "twoColumnBrowseResultsRenderer": {
                            "tabs": [
                                {
                                    "tabRenderer": {
                                        "content": {
                                            "unknownCustomRenderer": { "someField": 123 }
                                        }
                                    }
                                }
                            ]
                        }
                    }
                }
            """.trimIndent(), emptyMap(), "")
        }

        val browse = ChannelBrowse(transport = fakeTransport)
        val tab = ChannelTab(ChannelTabKind.VIDEOS, "Videos", "videos_params", true)
        val res = browse.tab("UC123", tab)
        assertIs<ExtractorResult.Failure>(res)
        val err = res.error
        assertIs<ExtractorError.ParseFailure>(err)
        assertTrue(err.message.contains("Empty tab parse without recognised empty-state"), "Error message: ${err.message}")
        assertTrue(err.message.contains("unknownCustomRenderer"), "Must list renderer keys: ${err.message}")
    }

    @Test
    fun emptyParseWithRecognisedEmptyStateReturnsSuccessEmpty() = runTest {
        val fakeTransport = FakeTransport { _, _, _, _ ->
            RawResponse(200, """
                {
                    "contents": {
                        "twoColumnBrowseResultsRenderer": {
                            "tabs": [
                                {
                                    "tabRenderer": {
                                        "content": {
                                            "messageRenderer": {
                                                "text": { "runs": [{ "text": "This channel has no videos." }] }
                                            }
                                        }
                                    }
                                }
                            ]
                        }
                    }
                }
            """.trimIndent(), emptyMap(), "")
        }

        val browse = ChannelBrowse(transport = fakeTransport)
        val tab = ChannelTab(ChannelTabKind.VIDEOS, "Videos", "videos_params", true)
        val res = browse.tab("UC123", tab)
        assertIs<ExtractorResult.Success<*>>(res)
        val page = (res as ExtractorResult.Success).value
        assertTrue(page.items.isEmpty())
        assertTrue(page.shelves.isEmpty())
    }

    @Test
    fun unknownRendererReportingIsRateLimitedToOnePerHour() {
        val reported = mutableListOf<String>()
        val browse = ChannelBrowse(onUnknownRenderer = { reported += it })

        // Report the same unknown renderer multiple times in quick succession
        browse.reportUnknownRenderer("newShinyRenderer")
        browse.reportUnknownRenderer("newShinyRenderer")
        browse.reportUnknownRenderer("newShinyRenderer")
        browse.reportUnknownRenderer("anotherRenderer")

        assertEquals(2, reported.size, "Duplicate unknown renderer reports within 1 hour must be dropped")
        assertEquals(listOf("newShinyRenderer", "anotherRenderer"), reported)
    }

    @Test
    fun refreshParamBypassesAndClearsTabCache() = runTest {
        var networkCalls = 0
        val fakeTransport = FakeTransport { _, _, _, _ ->
            networkCalls++
            RawResponse(200, fixture("videos_tab.json"), emptyMap(), "")
        }

        val browse = ChannelBrowse(transport = fakeTransport)
        val tab = ChannelTab(ChannelTabKind.VIDEOS, "Videos", "videos_params", true)

        // Call 1: uncached, network call
        val r1 = browse.tab("UC123", tab)
        assertIs<ExtractorResult.Success<*>>(r1)
        assertEquals(1, networkCalls)

        // Call 2: cached, no network call
        val r2 = browse.tab("UC123", tab)
        assertIs<ExtractorResult.Success<*>>(r2)
        assertEquals(1, networkCalls)

        // Call 3: refresh = true, network call
        val r3 = browse.tab("UC123", tab, refresh = true)
        assertIs<ExtractorResult.Success<*>>(r3)
        assertEquals(2, networkCalls)
    }

    @Test
    fun mapsHttpErrorsCorrectly() = runTest {
        val t429 = FakeTransport { _, _, _, _ -> RawResponse(429, "", emptyMap(), "") }
        val b429 = ChannelBrowse(transport = t429)
        assertIs<ExtractorError.RateLimited>((b429.landing("UC123") as ExtractorResult.Failure).error)

        val t403 = FakeTransport { _, _, _, _ -> RawResponse(403, "", emptyMap(), "") }
        val b403 = ChannelBrowse(transport = t403)
        assertIs<ExtractorError.Blocked>((b403.landing("UC123") as ExtractorResult.Failure).error)

        val t500 = FakeTransport { _, _, _, _ -> RawResponse(500, "", emptyMap(), "") }
        val b500 = ChannelBrowse(transport = t500)
        assertIs<ExtractorError.TierUnavailable>((b500.landing("UC123") as ExtractorResult.Failure).error)
    }
}
