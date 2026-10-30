package com.greenaltambe.vela.extractor.newpipe

import com.greenaltambe.vela.extractor.ChannelPage
import com.greenaltambe.vela.extractor.ChannelRef
import com.greenaltambe.vela.extractor.Comment
import com.greenaltambe.vela.extractor.PlaylistRef
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
        channelAvatarUrl = uploaderAvatars.bestUrl(),
        publishedText = textualUploadDate?.takeIf { it.isNotBlank() },
        thumbnailUrl = thumbnails.bestUrl(),
        durationSeconds = duration.takeIf { it >= 0 },
        viewCount = viewCount.takeIf { it >= 0 },
        isShort = isShortFormContent,
        isLive = streamType == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM || streamType == org.schabi.newpipe.extractor.stream.StreamType.AUDIO_LIVE_STREAM,
        uploadedAt = uploadDate?.instant?.let { kotlin.time.Instant.fromEpochMilliseconds(it.toEpochMilli()) },
    )
}

internal fun List<InfoItem>.toPlaylistRefs(): List<PlaylistRef> = filterIsInstance<org.schabi.newpipe.extractor.playlist.PlaylistInfoItem>().mapNotNull { p ->
    val id = queryParam(p.url.orEmpty(), "list") ?: return@mapNotNull null
    PlaylistRef(id, p.name.orEmpty(), p.thumbnails.bestUrl(), p.streamCount.takeIf { it >= 0 }, p.uploaderName.orEmpty())
}

internal fun List<InfoItem>.toChannelRefs(): List<ChannelRef> = filterIsInstance<org.schabi.newpipe.extractor.channel.ChannelInfoItem>().mapNotNull { c ->
    val id = channelIdFromUrl(c.url) ?: return@mapNotNull null
    ChannelRef(id, c.name.orEmpty(), c.thumbnails.bestUrl(), c.subscriberCount.takeIf { it >= 0 }, c.description.orEmpty())
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
    bannerUrl = banners.bestUrl(),
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
