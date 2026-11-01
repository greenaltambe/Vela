package com.greenaltambe.vela.extractor.newpipe

import com.greenaltambe.vela.extractor.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonWriter
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.services.youtube.YoutubeChannelHelper
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamInfoItemExtractor
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamInfoItemLockupExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector
import org.schabi.newpipe.extractor.Page as NpPage

/**
 * Tier 1: anonymous YouTube access through NewPipeExtractor. NewPipe is blocking, so every call runs on the IO
 * dispatcher and is interruptible; failures become [ExtractorError]s, cancellation propagates.
 *
 * Continuation tokens are NewPipe pages in string form (see Continuation.kt) and only work with this tier.
 * Comment sorting is not supported: NewPipeExtractor v0.26.5 has no sort option for YouTube comments, so
 * [CommentSort.NEWEST] is ignored and [capabilities] says so.
 */
class NewPipeExtractor(downloader: () -> Downloader = { OkHttpDownloader() }, private val region: () -> String = { "US" }) : Extractor {
    init {
        NewPipeBootstrap.ensureInitialised(downloader)
    }

    // The region setting is read on every call (a static in NewPipe), so a change applies from the next request on.
    private val youtube get() = ServiceList.YouTube.also { NewPipeBootstrap.applyRegion(region()) }

    override val capabilities = ExtractorCapabilities(
        supportsCommentSort = false,
        channelTabs = true,
        channelTabFilters = false,
    )

    override suspend fun search(query: String, continuation: String?): ExtractorResult<Page<VideoSummary>> =
        withPage(continuation) { page ->
            val handler: SearchQueryHandler =
                youtube.searchQHFactory.fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), "")
            if (page == null) {
                SearchInfo.getInfo(youtube, handler).let { it.relatedItems.toVideoSummaries() to it.nextPage }
            } else {
                SearchInfo.getMoreItems(youtube, handler, page).let { it.items.toVideoSummaries() to it.nextPage }
            }
        }.map { (items, next) -> Page(items, next.toTokenOrNull()) }

    override suspend fun searchChannels(query: String, continuation: String?): ExtractorResult<Page<ChannelRef>> =
        searchOf(query, continuation, YoutubeSearchQueryHandlerFactory.CHANNELS) { it.toChannelRefs() }

    override suspend fun searchPlaylists(query: String, continuation: String?): ExtractorResult<Page<PlaylistRef>> =
        searchOf(query, continuation, YoutubeSearchQueryHandlerFactory.PLAYLISTS) { it.toPlaylistRefs() }

    private suspend fun <T> searchOf(query: String, continuation: String?, filter: String, map: (List<InfoItem>) -> List<T>): ExtractorResult<Page<T>> =
        withPage(continuation) { page ->
            val handler: SearchQueryHandler = youtube.searchQHFactory.fromQuery(query, listOf(filter), "")
            if (page == null) SearchInfo.getInfo(youtube, handler).let { map(it.relatedItems) to it.nextPage }
            else SearchInfo.getMoreItems(youtube, handler, page).let { map(it.items) to it.nextPage }
        }.map { (items, next) -> Page(items, next.toTokenOrNull()) }

    override suspend fun suggestions(query: String): ExtractorResult<List<String>> = guarded { youtube.suggestionExtractor.suggestionList(query).take(8) }

    override suspend fun videoInfo(videoId: String): ExtractorResult<VideoInfo> = guarded {
        StreamInfo.getInfo(youtube, videoUrl(videoId)).toVideoInfo()
    }

    override suspend fun streams(videoId: String): ExtractorResult<StreamSet> = guarded {
        StreamInfo.getInfo(youtube, videoUrl(videoId)).toStreamSet()
    }

    override suspend fun channel(channelId: String): ExtractorResult<ChannelPage> = guarded {
        val info = ChannelInfo.getInfo(youtube, channelUrl(channelId))
        val videosTab = info.tabs.firstOrNull { ChannelTabs.VIDEOS in it.contentFilters }
        val tab = videosTab?.let { ChannelTabInfo.getInfo(youtube, it) }
        info.toChannelPage(tab?.relatedItems.orEmpty(), tab?.nextPage.toTokenOrNull())
    }

    override suspend fun channelVideos(channelId: String, continuation: String?): ExtractorResult<Page<VideoSummary>> =
        withPage(continuation) { page ->
            val tab = youtube.getChannelTabExtractorFromId(channelId, ChannelTabs.VIDEOS)
            val result: ListExtractor.InfoItemsPage<InfoItem> =
                if (page == null) tab.also { it.fetchPage() }.initialPage else tab.getPage(page)
            result.items.toVideoSummaries() to result.nextPage
        }.map { (items, next) -> Page(items, next.toTokenOrNull()) }

    override suspend fun channelVideosSorted(channelId: String, sort: ChannelVideoSort, continuation: String?): ExtractorResult<Page<VideoSummary>> {
        if (sort == ChannelVideoSort.LATEST) return channelVideos(channelId, continuation)
        return withPage(continuation) { page ->
            if (page != null) {
                // Later pages are ordinary append-continuations: the tab extractor reads them (the token already carries the order).
                val result = youtube.getChannelTabExtractorFromId(channelId, ChannelTabs.VIDEOS).getPage(page)
                result.items.toVideoSummaries() to result.nextPage
            } else sortedFirstPage(channelId, sort)
        }.map { (items, next) -> Page(items, next.toTokenOrNull()) }
    }

    /**
     * The first page in [sort] order: the Videos tab carries one "chip" per order, each with a continuation token; posting that token returns the
     * list already sorted by YouTube (as a reload command, which NewPipe's own paging does not read, hence the parsing here).
     */
    private fun sortedFirstPage(channelId: String, sort: ChannelVideoSort): Pair<List<VideoSummary>, NpPage?> {
        val loc = NewPipe.getPreferredLocalization(); val country = NewPipe.getPreferredContentCountry()
        val id = YoutubeChannelHelper.resolveChannelId(channelId)
        val tab = YoutubeChannelHelper.getChannelResponse(id, VIDEOS_TAB_PARAMS, loc, country).jsonResponse
            .getObject("contents").getObject("twoColumnBrowseResultsRenderer").getArray("tabs").filterIsInstance<JsonObject>()
            .map { it.getObject("tabRenderer") }.firstOrNull { it.getObject("endpoint").getObject("commandMetadata").getObject("webCommandMetadata").getString("url", "").endsWith("/videos") }
            ?: throw ParsingException("The Videos tab was not found")
        val chips = tab.getObject("content").getObject("richGridRenderer").getObject("header").getObject("chipBarViewModel").getArray("chips")
        // YouTube's order of the chips: Latest, Popular, Oldest (checked against the labels when they are in English).
        val chip = chips.getObject(sort.ordinal).getObject("chipViewModel")
        val label = chip.getString("text", "")
        if (label.isNotEmpty() && !label.equals(sort.name, ignoreCase = true) && !label.equals(if (sort == ChannelVideoSort.LATEST) "Latest" else sort.name, ignoreCase = true)) throw ParsingException("Unexpected sort chip \"$label\"")
        val token = chip.getObject("tapCommand").getObject("innertubeCommand").getObject("continuationCommand").getString("token")
        if (token.isNullOrEmpty()) throw ParsingException("No $sort sort on this channel")
        val body = continuationBody(token, loc, country)
        val response = YoutubeParsingHelper.getJsonPostResponse("browse", body, loc)
        val items = response.getArray("onResponseReceivedActions").filterIsInstance<JsonObject>()
            .mapNotNull { a -> (a.getObject("reloadContinuationItemsCommand").takeIf { it.has("continuationItems") } ?: a.getObject("appendContinuationItemsAction").takeIf { it.has("continuationItems") }) }
            // The response holds two reload commands (the chip bar, then the list): the list is the one with videos in it.
            .map { it.getArray("continuationItems") }.lastOrNull { l -> l.filterIsInstance<JsonObject>().any { it.has("richItemRenderer") } } ?: throw ParsingException("The sorted list was empty")
        return parseSortedItems(items, loc, country)
    }

    private fun continuationBody(token: String, loc: org.schabi.newpipe.extractor.localization.Localization, country: org.schabi.newpipe.extractor.localization.ContentCountry): ByteArray =
        JsonWriter.string(YoutubeParsingHelper.prepareDesktopJsonBuilder(loc, country).value("continuation", token).done()).toByteArray(Charsets.UTF_8)

    private fun parseSortedItems(items: JsonArray, loc: org.schabi.newpipe.extractor.localization.Localization, country: org.schabi.newpipe.extractor.localization.ContentCountry): Pair<List<VideoSummary>, NpPage?> {
        val ago = youtube.getTimeAgoParser(loc)
        val collector = StreamInfoItemsCollector(youtube.serviceId)
        var next: NpPage? = null
        items.filterIsInstance<JsonObject>().forEach { item ->
            val content = item.getObject("richItemRenderer").getObject("content")
            when {
                content.has("videoRenderer") -> collector.commit(YoutubeStreamInfoItemExtractor(content.getObject("videoRenderer"), ago))
                content.getObject("lockupViewModel").getString("contentType") == "LOCKUP_CONTENT_TYPE_VIDEO" ->
                    collector.commit(object : YoutubeStreamInfoItemLockupExtractor(content.getObject("lockupViewModel"), ago) {
                        override fun isChannelOrCoursePlaylistLockupItem() = true
                        override fun getUploaderAvatars() = emptyList<org.schabi.newpipe.extractor.Image>()
                    })
            }
            item.getObject("continuationItemRenderer").takeIf { it.isNotEmpty() }?.let { c ->
                val t = c.getObject("continuationEndpoint").getObject("continuationCommand").getString("token")
                if (!t.isNullOrEmpty()) next = NpPage("https://www.youtube.com/youtubei/v1/browse?prettyPrint=false", null, emptyList(), null, continuationBody(t, loc, country))
            }
        }
        return collector.items.toVideoSummaries() to next
    }

    override suspend fun comments(videoId: String, sort: CommentSort, continuation: String?): ExtractorResult<CommentsPage> =
        withPage(continuation) { page ->
            if (page == null) {
                CommentsInfo.getInfo(youtube, videoUrl(videoId))
                    ?.let { it.relatedItems.orEmpty() to it.nextPage } ?: (emptyList<CommentsInfoItem>() to null)
            } else {
                CommentsInfo.getMoreItems(youtube, videoUrl(videoId), page).let { it.items to it.nextPage }
            }
        }.map { (items, next) -> CommentsPage(items.map { it.toComment() }, next.toTokenOrNull()) }

    override suspend fun replies(videoId: String, commentId: String, continuation: String?): ExtractorResult<CommentsPage> =
        withPage(continuation) { page ->
            if (page != null) {
                CommentsInfo.getMoreItems(youtube, videoUrl(videoId), page).let { it.items to it.nextPage }
            } else {
                // No token given: the reply page hangs off the comment itself, so find the comment in the first page.
                val comment = CommentsInfo.getInfo(youtube, videoUrl(videoId))?.relatedItems.orEmpty()
                    .firstOrNull { it.commentId == commentId }
                    ?: throw CommentNotFound("Comment $commentId is not on the first page; pass Comment.repliesContinuation")
                val first = comment.replies?.takeIf { NpPage.isValid(it) }
                    ?: throw CommentNotFound("Comment $commentId has no replies")
                CommentsInfo.getMoreItems(youtube, videoUrl(videoId), first).let { it.items to it.nextPage }
            }
        }.map { (items, next) -> CommentsPage(items.map { it.toComment() }, next.toTokenOrNull()) }

    override suspend fun trending(continuation: String?): ExtractorResult<Page<VideoSummary>> =
        withPage(continuation) { page ->
            val extractor = youtube.kioskList.getDefaultKioskExtractor(page)
            val result: ListExtractor.InfoItemsPage<out InfoItem> =
                if (page == null) extractor.also { it.fetchPage() }.initialPage else extractor.getPage(page)
            result.items.toVideoSummaries() to result.nextPage
        }.map { (items, next) -> Page(items, next.toTokenOrNull()) }

    override suspend fun related(videoId: String): ExtractorResult<List<VideoSummary>> = guarded {
        StreamInfo.getInfo(youtube, videoUrl(videoId)).relatedItems.orEmpty().toVideoSummaries()
    }

    override suspend fun channelShorts(channelId: String, continuation: String?): ExtractorResult<Page<VideoSummary>> =
        withPage(continuation) { page ->
            val tab = youtube.getChannelTabExtractorFromId(channelId, ChannelTabs.SHORTS)
            val result: ListExtractor.InfoItemsPage<InfoItem> =
                if (page == null) tab.also { it.fetchPage() }.initialPage else tab.getPage(page)
            result.items.toVideoSummaries() to result.nextPage
        }.map { (items, next) -> Page(items, next.toTokenOrNull()) }

    override suspend fun channelLive(channelId: String, continuation: String?): ExtractorResult<Page<VideoSummary>> =
        withPage(continuation) { page ->
            val tab = youtube.getChannelTabExtractorFromId(channelId, ChannelTabs.LIVESTREAMS)
            val result: ListExtractor.InfoItemsPage<InfoItem> =
                if (page == null) tab.also { it.fetchPage() }.initialPage else tab.getPage(page)
            result.items.toVideoSummaries() to result.nextPage
        }.map { (items, next) -> Page(items, next.toTokenOrNull()) }

    override suspend fun channelPlaylists(channelId: String, continuation: String?): ExtractorResult<Page<PlaylistRef>> =
        withPage(continuation) { page ->
            val tab = youtube.getChannelTabExtractorFromId(channelId, ChannelTabs.PLAYLISTS)
            val result: ListExtractor.InfoItemsPage<InfoItem> =
                if (page == null) tab.also { it.fetchPage() }.initialPage else tab.getPage(page)
            result.items.toPlaylistRefs() to result.nextPage
        }.map { (items, next) -> Page(items, next.toTokenOrNull()) }

    override suspend fun playlistVideos(playlistId: String, continuation: String?): ExtractorResult<Page<VideoSummary>> =
        withPage(continuation) { page ->
            val url = "https://www.youtube.com/playlist?list=$playlistId"
            if (page == null) {
                val info = PlaylistInfo.getInfo(youtube, url)
                info.relatedItems.toVideoSummaries() to info.nextPage
            } else {
                val more = PlaylistInfo.getMoreItems(youtube, url, page)
                more.items.toVideoSummaries() to more.nextPage
            }
        }.map { (items, next) -> Page(items, next.toTokenOrNull()) }

    override suspend fun channelLanding(channelId: String): ExtractorResult<ChannelLanding> =
        channel(channelId).map { ch ->
            val fallbackTabs = listOf(
                ChannelTab(ChannelTabKind.VIDEOS, "Videos", selected = true),
                ChannelTab(ChannelTabKind.SHORTS, "Shorts"),
                ChannelTab(ChannelTabKind.STREAMS, "Live"),
                ChannelTab(ChannelTabKind.PLAYLISTS, "Playlists"),
            )
            val initialPage = ChannelTabPage(items = ch.videos.map { ChannelItem.Video(it) }, nextToken = ch.continuation)
            ChannelLanding(header = ch, tabs = fallbackTabs, initialPage = initialPage)
        }

    override suspend fun channelTab(channelId: String, tab: ChannelTab, filterOption: ChannelFilterOption?, continuation: String?): ExtractorResult<ChannelTabPage> {
        if (continuation != null && continuation.startsWith("n1:")) {
            return ExtractorResult.Failure(ExtractorError.TokenNotMine("Native token passed to NewPipe"))
        }
        return when (tab.kind) {
            ChannelTabKind.VIDEOS, ChannelTabKind.FEATURED -> channelVideos(channelId, continuation).map { p ->
                ChannelTabPage(items = p.items.map { ChannelItem.Video(it) }, nextToken = p.continuation)
            }
            ChannelTabKind.SHORTS -> channelShorts(channelId, continuation).map { p ->
                ChannelTabPage(items = p.items.map { ChannelItem.Video(it) }, nextToken = p.continuation)
            }
            ChannelTabKind.STREAMS -> channelLive(channelId, continuation).map { p ->
                ChannelTabPage(items = p.items.map { ChannelItem.Video(it) }, nextToken = p.continuation)
            }
            ChannelTabKind.PLAYLISTS -> channelPlaylists(channelId, continuation).map { p ->
                ChannelTabPage(items = p.items.map { ChannelItem.Playlist(it) }, nextToken = p.continuation)
            }
            ChannelTabKind.COURSES, ChannelTabKind.POSTS -> ExtractorResult.Failure(ExtractorError.TierUnavailable("${tab.kind.name.lowercase().replaceFirstChar(Char::uppercase)} are not available on fallback"))
            else -> ExtractorResult.Failure(ExtractorError.TierUnavailable("Tab ${tab.kind} is not available on fallback"))
        }
    }

    override suspend fun channelTabByContinuation(token: String): ExtractorResult<ChannelTabPage> {
        if (token.startsWith("n1:")) return ExtractorResult.Failure(ExtractorError.TokenNotMine("Native token passed to NewPipe"))
        val page = pageFromContinuationToken(token) ?: return ExtractorResult.Failure(ExtractorError.TokenNotMine("Token cannot be parsed by NewPipe"))
        return guarded {
            val url = page.url
            if (url.contains("playlist")) {
                val more = PlaylistInfo.getMoreItems(youtube, url, page)
                ChannelTabPage(items = more.items.toPlaylistRefs().map { ChannelItem.Playlist(it) }, nextToken = more.nextPage.toTokenOrNull())
            } else {
                val chanId = channelIdFromUrl(url).orEmpty()
                val tab = if (url.contains("shorts")) ChannelTabs.SHORTS
                else if (url.contains("streams") || url.contains("live")) ChannelTabs.LIVESTREAMS
                else ChannelTabs.VIDEOS
                val result = youtube.getChannelTabExtractorFromId(chanId, tab).getPage(page)
                ChannelTabPage(items = result.items.toVideoSummaries().map { ChannelItem.Video(it) }, nextToken = result.nextPage.toTokenOrNull())
            }
        }
    }

    override suspend fun channelAbout(channelId: String, token: String?): ExtractorResult<ChannelAbout> =
        channel(channelId).map { ch ->
            ChannelAbout(
                description = ch.description,
                subscriberCountText = ch.subscriberCount?.let { "$it subscribers" },
                isPartial = true,
            )
        }

    override suspend fun channelSearch(channelId: String, query: String, continuation: String?): ExtractorResult<ChannelTabPage> =
        ExtractorResult.Failure(ExtractorError.TierUnavailable("In-channel search is not supported on fallback"))

    override suspend fun postDetail(browseParams: String): ExtractorResult<Pair<ChannelPost, List<Comment>>> =
        ExtractorResult.Failure(ExtractorError.Unplayable("Post details not supported on NewPipe fallback"))

    /**
     * The public Atom feed first (one small request, carries publish times), the channel's Videos tab if that is refused.
     * Shorts are not told apart by the feed, so they are not flagged here.
     */
    override suspend fun recentUploads(channelId: String): ExtractorResult<List<VideoSummary>> {
        val rss = guarded {
            val response = NewPipe.getDownloader().get("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId")
            check(response.responseCode() == 200) { "feed answered HTTP ${response.responseCode()}" }
            parseChannelFeed(response.responseBody(), channelId)
        }
        if (rss is ExtractorResult.Success && rss.value.isNotEmpty()) return rss
        return channelVideos(channelId).map { it.items }
    }

    private class CommentNotFound(message: String) : Exception(message)

    /** Runs [block] with the decoded continuation (null for a first page); a token from another tier fails cleanly. */
    private suspend fun <T> withPage(continuation: String?, block: (NpPage?) -> T): ExtractorResult<T> {
        val page = continuation?.let {
            if (it.startsWith("n1:")) return ExtractorResult.Failure(ExtractorError.TokenNotMine("Native token passed to NewPipe"))
            pageFromContinuationToken(it) ?: return ExtractorResult.Failure(ExtractorError.TokenNotMine("Token is not from NewPipe"))
        }
        return guarded { block(page) }
    }

    private suspend fun <T> guarded(block: () -> T): ExtractorResult<T> = try {
        ExtractorResult.Success(runInterruptible(Dispatchers.IO, block = block))
    } catch (e: CancellationException) {
        throw e
    } catch (e: CommentNotFound) {
        ExtractorResult.Failure(ExtractorError.Unplayable(e.message.orEmpty(), e))
    } catch (e: Exception) {
        ExtractorResult.Failure(e.toExtractorError())
    }
}

/** The `params` of the Videos tab in a browse request (the same value the channel page itself sends). */
private const val VIDEOS_TAB_PARAMS = "EgZ2aWRlb3PyBgQKAjoA"
