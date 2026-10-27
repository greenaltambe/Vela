package com.greenaltambe.vela.extractor.newpipe

import com.greenaltambe.vela.extractor.ChannelPage
import com.greenaltambe.vela.extractor.Comment
import com.greenaltambe.vela.extractor.VideoSummary
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/** Null when the item has no recognisable video id (nothing downstream could open it). */
internal fun StreamInfoItem.toVideoSummary(): VideoSummary? {
    val videoId = videoIdFromUrl(url) ?: return null
    return VideoSummary(
        id = videoId,
        title = name.orEmpty(),
        channelId = channelIdFromUrl(uploaderUrl).orEmpty(),
        channelName = uploaderName.orEmpty(),
        thumbnailUrl = thumbnails.bestUrl(),
        durationSeconds = duration.takeIf { it >= 0 },
        viewCount = viewCount.takeIf { it >= 0 },
        isShort = isShortFormContent,
    )
}

internal fun List<InfoItem>.toVideoSummaries(): List<VideoSummary> =
    filterIsInstance<StreamInfoItem>().mapNotNull { it.toVideoSummary() }

internal fun ChannelInfo.toChannelPage(videos: List<InfoItem>, continuation: String?) = ChannelPage(
    id = id,
    name = name.orEmpty(),
    description = description.orEmpty(),
    avatarUrl = avatars.bestUrl(),
    subscriberCount = subscriberCount.takeIf { it >= 0 },
    videos = videos.toVideoSummaries(),
    continuation = continuation,
)

internal fun CommentsInfoItem.toComment() = Comment(
    id = commentId.orEmpty(),
    author = uploaderName.orEmpty(),
    text = commentText?.content.orEmpty(),
    likeCount = likeCount.coerceAtLeast(0).toLong(),
    publishedText = textualUploadDate.orEmpty(),
    replyCount = replyCount.coerceAtLeast(0),
    isPinned = isPinned,
    repliesContinuation = replies.toTokenOrNull(),
)
