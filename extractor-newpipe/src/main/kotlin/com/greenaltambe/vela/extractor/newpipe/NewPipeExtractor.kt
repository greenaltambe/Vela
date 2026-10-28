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
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.Page as NpPage

/**
 * Tier 1: anonymous YouTube access through NewPipeExtractor. NewPipe is blocking, so every call runs on the IO
 * dispatcher and is interruptible; failures become [ExtractorError]s, cancellation propagates.
 *
 * Continuation tokens are NewPipe pages in string form (see Continuation.kt) and only work with this tier.
 * Comment sorting is not supported: NewPipeExtractor v0.26.5 has no sort option for YouTube comments, so
 * [CommentSort.NEWEST] is ignored and [capabilities] says so.
 */
class NewPipeExtractor(downloader: () -> Downloader = { OkHttpDownloader() }) : Extractor {
    init {
        NewPipeBootstrap.ensureInitialised(downloader)
    }

    private val youtube get() = ServiceList.YouTube

    override val capabilities = ExtractorCapabilities(supportsCommentSort = false)

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
        val page = continuation?.let { pageFromContinuationToken(it) ?: return ContinuationToken.foreign() }
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

private inline fun <T, R> ExtractorResult<T>.map(transform: (T) -> R): ExtractorResult<R> = when (this) {
    is ExtractorResult.Success -> ExtractorResult.Success(transform(value))
    is ExtractorResult.Failure -> this
}
