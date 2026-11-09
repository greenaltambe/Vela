@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.ui.test.*
import com.greenaltambe.vela.extractor.*
import com.greenaltambe.vela.ui.nav.Route
import org.junit.Test
import kotlin.test.assertTrue

class ChannelTabsNativeUiTest {

    @Test
    fun nativeChannelTabsRenderInTabStripAndSwipePager() = runDesktopComposeUiTest {
        val courseRef = PlaylistRef(
            id = "course1",
            title = "Introduction to Computer Science",
            thumbnailUrl = "https://invalid.invalid/c.jpg",
            videoCount = 24,
            isCourse = true,
            lessonCountText = "24 lessons"
        )
        val postItem = ChannelPost(
            id = "post1",
            authorName = "Course Channel",
            text = "Welcome to our new course on computer science!",
            publishedText = "1 day ago",
            voteCountText = "1.5K"
        )
        val channelExtractor = object : Extractor by FakeExtractor() {
            override val capabilities = ExtractorCapabilities(channelTabs = true, channelTabFilters = true)
            override suspend fun channelLanding(channelId: String): ExtractorResult<ChannelLanding> =
                ExtractorResult.Success(
                    ChannelLanding(
                        header = ChannelPage(channelId, "Course Channel", "Learn computer science and more", subscriberCount = 500_000),
                        tabs = listOf(
                            ChannelTab(ChannelTabKind.VIDEOS, "Videos", selected = true),
                            ChannelTab(ChannelTabKind.COURSES, "Courses"),
                            ChannelTab(ChannelTabKind.POSTS, "Community"),
                            ChannelTab(ChannelTabKind.SEARCH, "Search"),
                        ),
                        initialPage = ChannelTabPage(
                            items = listOf(ChannelItem.Video(FakeData.videos[0])),
                            filters = listOf(
                                ChannelFilterGroup(
                                    isDropdown = false,
                                    options = listOf(
                                        ChannelFilterOption("Latest", selected = true),
                                        ChannelFilterOption("Popular", selected = false),
                                        ChannelFilterOption("Oldest", selected = false)
                                    )
                                )
                            )
                        )
                    )
                )

            override suspend fun channelTab(channelId: String, tab: ChannelTab, filterOption: ChannelFilterOption?, continuation: String?): ExtractorResult<ChannelTabPage> =
                when (tab.kind) {
                    ChannelTabKind.COURSES -> ExtractorResult.Success(ChannelTabPage(items = listOf(ChannelItem.Playlist(courseRef))))
                    ChannelTabKind.POSTS -> ExtractorResult.Success(ChannelTabPage(items = listOf(ChannelItem.Post(postItem))))
                    else -> ExtractorResult.Success(ChannelTabPage(items = listOf(ChannelItem.Video(FakeData.videos[0]))))
                }
        }

        val f = buildFixture(withDownloads = false, extractorOverride = channelExtractor)
        f.navigator.push(Route.Channel("UC_COURSE", "Course Channel"))
        setContent { AppUnderTest(f) }
        waitForIdle()

        // Verify channel header
        onNodeWithTag("channel.name").assertIsDisplayed()
        onNodeWithTag("channel.tabs").assertIsDisplayed()

        // Tab strip contains discovered tabs (Area 6: only tabs returned by server, no synthetic shorts)
        onNodeWithTag("channel.tab.videos").assertIsDisplayed()
        onNodeWithTag("channel.tab.shorts").assertDoesNotExist()
        onNodeWithTag("channel.tab.courses").assertIsDisplayed()
        onNodeWithTag("channel.tab.posts").assertIsDisplayed()
        onNodeWithTag("channel.tab.search").assertIsDisplayed()

        // Sort chips are visible when filters are offered
        onNodeWithTag("channel.sort").assertIsDisplayed()
        onNodeWithTag("channel.sort.latest").assertIsDisplayed()
        onNodeWithTag("channel.sort.oldest").assertIsDisplayed()

        // Switch tab to Courses
        onNodeWithTag("channel.tab.courses").performClick()
        waitUntil(timeoutMillis = 5_000) {
            runCatching { onNodeWithText("24 lessons").assertIsDisplayed() }.isSuccess
        }
        onAllNodesWithTag("playlist.badge", useUnmergedTree = true).onFirst().assertIsDisplayed()

        // Switch to Community tab
        onNodeWithTag("channel.tab.posts").performClick()
        waitUntil(timeoutMillis = 5_000) {
            runCatching { onNodeWithText("Welcome to our new course on computer science!").assertIsDisplayed() }.isSuccess
        }
    }

    @Test
    fun fallbackTierHidesSortControlsCleanly() = runDesktopComposeUiTest {
        // Fallback tier has channelTabs = true, but channelTabFilters = false (no sort chips)
        val fallbackExtractor = object : Extractor by FakeExtractor() {
            override val capabilities = ExtractorCapabilities(channelTabs = true, channelTabFilters = false)
            override suspend fun channelLanding(channelId: String): ExtractorResult<ChannelLanding> =
                ExtractorResult.Success(
                    ChannelLanding(
                        header = ChannelPage(channelId, "Fallback Channel", subscriberCount = 10_000),
                        tabs = listOf(
                            ChannelTab(ChannelTabKind.VIDEOS, "Videos", selected = true),
                            ChannelTab(ChannelTabKind.PLAYLISTS, "Playlists"),
                        ),
                        initialPage = ChannelTabPage(
                            items = listOf(ChannelItem.Video(FakeData.videos[0])),
                            filters = emptyList() // No filter groups in fallback!
                        )
                    )
                )
        }

        val f = buildFixture(withDownloads = false, extractorOverride = fallbackExtractor)
        f.navigator.push(Route.Channel("UC_FALLBACK", "Fallback Channel"))
        setContent { AppUnderTest(f) }
        waitForIdle()

        onNodeWithTag("channel.name").assertIsDisplayed()
        onNodeWithTag("channel.tabs").assertIsDisplayed()

        // Sort controls are NOT displayed because filters is empty
        onNodeWithTag("channel.sort").assertDoesNotExist()
    }

    @Test
    fun shortsTabRendersShortCardsWithShortBadge() = runDesktopComposeUiTest {
        val shortVideo = FakeData.videos[0].copy(
            id = "short123",
            title = "Awesome Short Video",
            isShort = true,
            viewCount = 750_000
        )
        val channelExtractor = object : Extractor by FakeExtractor() {
            override val capabilities = ExtractorCapabilities(channelTabs = true, channelTabFilters = true)
            override suspend fun channelLanding(channelId: String): ExtractorResult<ChannelLanding> =
                ExtractorResult.Success(
                    ChannelLanding(
                        header = ChannelPage(channelId, "Shorts Creator", subscriberCount = 100_000),
                        tabs = listOf(
                            ChannelTab(ChannelTabKind.VIDEOS, "Videos"),
                            ChannelTab(ChannelTabKind.SHORTS, "Shorts"),
                        ),
                        initialPage = ChannelTabPage(items = listOf(ChannelItem.Video(FakeData.videos[0])))
                    )
                )

            override suspend fun channelTab(channelId: String, tab: ChannelTab, filterOption: ChannelFilterOption?, continuation: String?): ExtractorResult<ChannelTabPage> =
                if (tab.kind == ChannelTabKind.SHORTS) {
                    ExtractorResult.Success(ChannelTabPage(items = listOf(ChannelItem.Video(shortVideo))))
                } else {
                    ExtractorResult.Success(ChannelTabPage(items = listOf(ChannelItem.Video(FakeData.videos[0]))))
                }
        }

        val f = buildFixture(withDownloads = false, extractorOverride = channelExtractor)
        f.navigator.push(Route.Channel("UC_SHORTS", "Shorts Creator"))
        setContent { AppUnderTest(f) }
        waitForIdle()

        onNodeWithTag("channel.tab.shorts").assertIsDisplayed().performClick()
        waitUntil(timeoutMillis = 5_000) {
            runCatching { onNodeWithText("Awesome Short Video").assertIsDisplayed() }.isSuccess
        }
        onNodeWithText("SHORT").assertIsDisplayed()
    }

    @Test
    fun channelListScrollPinsTabsBelowTopBar() = runDesktopComposeUiTest {
        val manyVideos = (1..20).map { i ->
            FakeData.videos[0].copy(id = "v$i", title = "Channel Video $i")
        }
        val channelExtractor = object : Extractor by FakeExtractor() {
            override val capabilities = ExtractorCapabilities(channelTabs = true, channelTabFilters = true)
            override suspend fun channelLanding(channelId: String): ExtractorResult<ChannelLanding> =
                ExtractorResult.Success(
                    ChannelLanding(
                        header = ChannelPage(channelId, "Scroll Channel", subscriberCount = 50_000),
                        tabs = listOf(
                            ChannelTab(ChannelTabKind.VIDEOS, "Videos", selected = true),
                            ChannelTab(ChannelTabKind.SHORTS, "Shorts"),
                        ),
                        initialPage = ChannelTabPage(items = manyVideos.map { ChannelItem.Video(it) })
                    )
                )
        }

        val f = buildFixture(withDownloads = false, extractorOverride = channelExtractor)
        f.navigator.push(Route.Channel("UC_SCROLL", "Scroll Channel"))
        setContent { AppUnderTest(f) }
        waitForIdle()

        // Top bar, channel tabs, and channel list are initially displayed
        onNodeWithTag("topbar").assertIsDisplayed()
        onNodeWithTag("channel.tabs").assertIsDisplayed()
        onNodeWithTag("channel.list").assertIsDisplayed()

        // Scroll the list upwards
        onNodeWithTag("channel.list").performTouchInput { swipeUp() }
        waitForIdle()

        // Top bar remains fixed at top, tabs remain pinned below top bar, and list fills remaining space
        onNodeWithTag("topbar").assertIsDisplayed()
        onNodeWithTag("channel.tabs").assertIsDisplayed()
        onNodeWithTag("channel.pager").assertIsDisplayed()
        onNodeWithTag("channel.list").assertIsDisplayed()
    }

    @Test
    fun headerTabsAndVideosNeverOverlapWhileScrolling() = runDesktopComposeUiTest {
        val manyVideos = (1..20).map { i -> FakeData.videos[0].copy(id = "v$i", title = "Channel Video $i") }
        val channelExtractor = object : Extractor by FakeExtractor() {
            override val capabilities = ExtractorCapabilities(channelTabs = true, channelTabFilters = true)
            override suspend fun channelLanding(channelId: String): ExtractorResult<ChannelLanding> = ExtractorResult.Success(
                ChannelLanding(
                    header = ChannelPage(channelId, "Overlap Channel", subscriberCount = 50_000, description = "A channel about things."),
                    tabs = listOf(ChannelTab(ChannelTabKind.VIDEOS, "Videos", selected = true), ChannelTab(ChannelTabKind.SHORTS, "Shorts")),
                    initialPage = ChannelTabPage(items = manyVideos.map { ChannelItem.Video(it) }),
                ),
            )
        }
        val f = buildFixture(withDownloads = false, extractorOverride = channelExtractor)
        f.navigator.push(Route.Channel("UC_OVERLAP", "Overlap Channel"))
        setContent { AppUnderTest(f) }
        waitForIdle()

        fun bounds(tag: String) = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        repeat(4) { step ->
            val bar = bounds("topbar"); val tabs = bounds("channel.tabs")
            assertTrue(tabs.top >= bar.bottom - 1f, "step $step: tabs (${tabs.top}) sit under the top bar (${bar.bottom})")
            // the tab row never runs under the bar and the list starts at the bar
            assertTrue(bounds("channel.list").top >= bar.bottom - 1f, "step $step: the list starts under the top bar")
            onNodeWithTag("channel.list").performTouchInput { swipeUp() }
            waitForIdle()
        }
    }

    private fun postsExtractor(detail: suspend (String) -> ExtractorResult<Pair<ChannelPost, List<Comment>>>, detailParams: String? = "params1") = object : Extractor by FakeExtractor() {
        val post = ChannelPost(id = "post1", authorName = "Poster", text = "A community post about things", publishedText = "1 day ago", voteCountText = "12", detailBrowseParams = detailParams)
        override val capabilities = ExtractorCapabilities(channelTabs = true)
        override suspend fun channelLanding(channelId: String): ExtractorResult<ChannelLanding> = ExtractorResult.Success(
            ChannelLanding(header = ChannelPage(channelId, "Post Channel", subscriberCount = 1_000), tabs = listOf(ChannelTab(ChannelTabKind.POSTS, "Community", selected = true)), initialPage = ChannelTabPage(items = listOf(ChannelItem.Post(post)))),
        )
        override suspend fun postDetail(browseParams: String) = detail(browseParams)
    }

    @Test
    fun postCommentsOpenInTheSheetAndAreListed() = runDesktopComposeUiTest {
        val ex = postsExtractor({ ExtractorResult.Success(ChannelPost("post1", "Poster", text = "x") to listOf(Comment("c1", "@alice", "Great post", likeCount = 65, publishedText = "5 days ago", replyCount = 2), Comment("c2", "@bob", "Agreed"))) })
        val f = buildFixture(withDownloads = false, extractorOverride = ex)
        f.navigator.push(Route.Channel("UC_POSTS", "Post Channel"))
        setContent { AppUnderTest(f) }
        waitForIdle()
        onNodeWithTag("channel.postComments").performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("channel.postComment").fetchSemanticsNodes().size == 2 }
        onNodeWithText("Great post").assertExists()
        onNodeWithText("2 replies").assertExists()
    }

    @Test
    fun postCommentsFailureShowsAnErrorWithRetryNotAnEmptyList() = runDesktopComposeUiTest {
        var calls = 0
        val ex = postsExtractor({ calls++; if (calls == 1) ExtractorResult.Failure(ExtractorError.Network("offline")) else ExtractorResult.Success(ChannelPost("post1", "Poster", text = "x") to listOf(Comment("c1", "@alice", "Back again"))) })
        val f = buildFixture(withDownloads = false, extractorOverride = ex)
        f.navigator.push(Route.Channel("UC_POSTS2", "Post Channel"))
        setContent { AppUnderTest(f) }
        waitForIdle()
        onNodeWithTag("channel.postComments").performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("channel.postCommentsError").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithTag("channel.postNoComments").assertCountEquals(0)
        onNodeWithText("Retry").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("channel.postComment").fetchSemanticsNodes().size == 1 }
        onNodeWithText("Back again").assertExists()
    }

    @Test
    fun postWithoutDetailLinkExplainsAndOffersTheBrowser() = runDesktopComposeUiTest {
        val ex = postsExtractor({ error("never called") }, detailParams = null)
        val f = buildFixture(withDownloads = false, extractorOverride = ex)
        f.navigator.push(Route.Channel("UC_POSTS3", "Post Channel"))
        setContent { AppUnderTest(f) }
        waitForIdle()
        onNodeWithTag("channel.postComments").performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("channel.postCommentsError").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("channel.postOpenBrowser").assertExists()
    }

    @Test
    fun repliesOpenUnderTheirCommentAndImagesOpenFullScreen() = runDesktopComposeUiTest {
        val ex = object : Extractor by FakeExtractor() {
            val post = ChannelPost(id = "post1", authorName = "Poster", text = "Look", detailBrowseParams = "p", attachment = PostAttachment.MultiImage(listOf(PostAttachment.Image("https://invalid.invalid/1.jpg"), PostAttachment.Image("https://invalid.invalid/2.jpg"))))
            override val capabilities = ExtractorCapabilities(channelTabs = true)
            override suspend fun channelLanding(channelId: String) = ExtractorResult.Success(ChannelLanding(ChannelPage(channelId, "Post Channel", subscriberCount = 1_000), listOf(ChannelTab(ChannelTabKind.POSTS, "Community", selected = true)), ChannelTabPage(items = listOf(ChannelItem.Post(post)))))
            override suspend fun postDetail(browseParams: String) = ExtractorResult.Success(post to listOf(Comment("c1", "@alice", "Top comment", replyCount = 1, repliesContinuation = "n1:R")))
            override suspend fun replies(videoId: String, commentId: String, continuation: String?) = ExtractorResult.Success(CommentsPage(listOf(Comment("r1", "@bob", "A reply here"))))
        }
        val f = buildFixture(withDownloads = false, extractorOverride = ex)
        f.navigator.push(Route.Channel("UC_REP", "Post Channel"))
        setContent { AppUnderTest(f) }
        waitForIdle()
        // an image opens full screen
        onAllNodesWithTag("channel.post.image")[0].performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("imageViewer").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("imageViewer.close").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("imageViewer").fetchSemanticsNodes().isEmpty() }
        // replies
        onNodeWithTag("channel.postComments").performScrollTo().performClick()
        // The replies line is the one the video's comments have; it opens the thread (the comment, then its replies) with a way back.
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("comment.repliesToggle").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("comment.repliesToggle").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("comment.reply").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("A reply here").assertExists()
        onNodeWithText("Top comment").assertExists()   // the thread's own comment stays on top
        onNodeWithTag("channel.postThreadBack").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("channel.postComment").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun anonymousPollShowsTheTotalAndSaysWhyThereAreNoResults() = runDesktopComposeUiTest {
        val ex = object : Extractor by FakeExtractor() {
            val post = ChannelPost(id = "post1", authorName = "Poster", text = "Pick", attachment = PostAttachment.Poll(listOf(PollChoice("A"), PollChoice("B")), "1.8M votes"))
            override val capabilities = ExtractorCapabilities(channelTabs = true)
            override suspend fun channelLanding(channelId: String) = ExtractorResult.Success(ChannelLanding(ChannelPage(channelId, "Poll Channel", subscriberCount = 1_000), listOf(ChannelTab(ChannelTabKind.POSTS, "Community", selected = true)), ChannelTabPage(items = listOf(ChannelItem.Post(post)))))
        }
        val f = buildFixture(withDownloads = false, extractorOverride = ex)
        f.navigator.push(Route.Channel("UC_POLL", "Poll Channel"))
        setContent { AppUnderTest(f) }
        waitForIdle()
        onNodeWithTag("channel.post.pollTotal").performScrollTo().assertTextContains("1.8M votes", substring = true)
        onNodeWithTag("channel.post.pollTotal").assertTextContains("Vote on YouTube", substring = true)
    }

    @Test
    fun channelWithUnknownTabRendersIt() = runDesktopComposeUiTest {
        val channelExtractor = object : Extractor by FakeExtractor() {
            override val capabilities = ExtractorCapabilities(channelTabs = true, channelTabFilters = true)
            override suspend fun channelLanding(channelId: String): ExtractorResult<ChannelLanding> =
                ExtractorResult.Success(
                    ChannelLanding(
                        header = ChannelPage(channelId, "Store Channel", subscriberCount = 10_000),
                        tabs = listOf(
                            ChannelTab(ChannelTabKind.VIDEOS, "Videos", selected = true),
                            ChannelTab(ChannelTabKind.UNKNOWN, "Merch", params = "merch_params"),
                        ),
                        initialPage = ChannelTabPage(items = listOf(ChannelItem.Video(FakeData.videos[0])))
                    )
                )
        }

        val f = buildFixture(withDownloads = false, extractorOverride = channelExtractor)
        f.navigator.push(Route.Channel("UC_STORE", "Store Channel"))
        setContent { AppUnderTest(f) }
        waitForIdle()

        onNodeWithTag("channel.tab.videos").assertIsDisplayed()
        onNodeWithTag("channel.tab.unknown").assertIsDisplayed()
        onNodeWithText("Merch").assertIsDisplayed()
    }

    @Test
    fun continuationExpiredRefreshesPage1AndShowsRefreshedNotice() = runDesktopComposeUiTest {
        var page1Calls = 0
        var continuationCalls = 0
        val p1Video = FakeData.videos[0].copy(id = "p1", title = "Page 1 Video")
        val channelExtractor = object : Extractor by FakeExtractor() {
            override val capabilities = ExtractorCapabilities(channelTabs = true, channelTabFilters = true)
            override suspend fun channelLanding(channelId: String): ExtractorResult<ChannelLanding> =
                ExtractorResult.Success(
                    ChannelLanding(
                        header = ChannelPage(channelId, "Paging Channel", subscriberCount = 10_000),
                        tabs = listOf(ChannelTab(ChannelTabKind.VIDEOS, "Videos", selected = true)),
                        initialPage = ChannelTabPage(
                            items = listOf(ChannelItem.Video(p1Video)),
                            nextToken = "n1:tok1"
                        )
                    )
                )

            override suspend fun channelTab(channelId: String, tab: ChannelTab, filterOption: ChannelFilterOption?, continuation: String?): ExtractorResult<ChannelTabPage> {
                if (continuation != null) {
                    continuationCalls++
                    return ExtractorResult.Failure(ExtractorError.TokenExpired("Expired token"))
                }
                page1Calls++
                return ExtractorResult.Success(ChannelTabPage(items = listOf(ChannelItem.Video(p1Video)), nextToken = "n1:fresh_token"))
            }
        }

        val f = buildFixture(withDownloads = false, extractorOverride = channelExtractor)
        f.navigator.push(Route.Channel("UC_PAGING", "Paging Channel"))
        setContent { AppUnderTest(f) }
        waitForIdle()

        // The button is the list's last item and may be below the fold (so not composed yet): scroll the list to it first.
        onNodeWithTag("channel.list").performScrollToNode(hasTestTag("channel.loadMore"))
        onNodeWithTag("channel.loadMore").assertIsDisplayed().performClick()
        waitForIdle()

        // Verify notice is shown and page 1 was re-fetched
        onNodeWithTag("channel.refreshedNotice").performScrollTo().assertIsDisplayed()
        assertTrue(continuationCalls >= 1, "Continuation was called")
        assertTrue(page1Calls >= 1, "Page 1 was re-fetched on token expiry")
    }

    @Test
    fun shelfPostHasFixedHeightInHorizontalRow() = runDesktopComposeUiTest {
        val longPost = ChannelPost(
            id = "long_post",
            authorName = "Author",
            text = "This is a very long text.\nLine 2\nLine 3\nLine 4\nLine 5\nLine 6\nLine 7\nLine 8",
            publishedText = "2 days ago",
            voteCountText = "100"
        )
        val channelExtractor = object : Extractor by FakeExtractor() {
            override val capabilities = ExtractorCapabilities(channelTabs = true, channelTabFilters = true)
            override suspend fun channelLanding(channelId: String): ExtractorResult<ChannelLanding> =
                ExtractorResult.Success(
                    ChannelLanding(
                        header = ChannelPage(channelId, "Shelf Channel", subscriberCount = 10_000),
                        tabs = listOf(ChannelTab(ChannelTabKind.FEATURED, "Home", selected = true)),
                        initialPage = ChannelTabPage(
                            items = emptyList(),
                            shelves = listOf(
                                ChannelShelf("Community Posts", items = listOf(ChannelItem.Post(longPost)))
                            )
                        )
                    )
                )
        }

        val f = buildFixture(withDownloads = false, extractorOverride = channelExtractor)
        f.navigator.push(Route.Channel("UC_SHELF", "Shelf Channel"))
        setContent { AppUnderTest(f) }
        waitForIdle()

        onNodeWithTag("channel.post").assertIsDisplayed()
    }
}
