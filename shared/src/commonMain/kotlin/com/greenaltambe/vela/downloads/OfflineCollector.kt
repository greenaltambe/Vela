package com.greenaltambe.vela.downloads

import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.data.db.Download
import com.greenaltambe.vela.extractor.Comment
import com.greenaltambe.vela.extractor.CommentSort
import com.greenaltambe.vela.extractor.Extractor
import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.library.effectiveChapters
import com.greenaltambe.vela.modules.Modules
import com.greenaltambe.vela.modules.dearrow.DeArrowSurface
import com.greenaltambe.vela.modules.sponsorblock.SegmentCategory
import com.greenaltambe.vela.modules.sponsorblock.SegmentsResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** DeArrow's choice for a video at download time; the image is kept so the offline page shows the same thumbnail. */
@Serializable
class StoredBranding(val title: String? = null, val thumbnailPath: String? = null)

/**
 * Everything a page needs to work with no connection, gathered when a download finishes: the video's info and
 * description, chapters, thumbnail, SponsorBlock segments, DeArrow's title and thumbnail, and the first comment pages.
 * Each part is best effort: a part that fails is left out (and listed in [Result.missing]) and never fails the download.
 */
class OfflineCollector(
    private val extractor: Extractor,
    private val modules: Modules,
    private val fetcher: RangeFetcher,
    private val storage: () -> DownloadStorage,
    private val settings: SettingsResolver,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    class Result(val data: OfflineData, val missing: List<String>)

    suspend fun collect(row: Download): Result {
        val missing = mutableListOf<String>()
        val videoId = row.videoId

        val info = (extractor.videoInfo(videoId) as? ExtractorResult.Success)?.value
        if (info == null) missing += "info"
        val chapters = info?.let { effectiveChapters(it.chapters, it.description, it.durationSeconds) }.orEmpty()

        val thumbnailPath = info?.thumbnailUrl?.let { saveImage(it, "${videoId}.jpg") }
        if (thumbnailPath == null) missing += "thumbnail"

        val segments = try {
            (modules.segments.get(videoId, SegmentCategory.entries) as? SegmentsResult.Loaded)?.segments
        } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
        if (segments == null) missing += "sponsorblock"

        val branding = try {
            modules.dearrow.prefetch(listOf(videoId))
            val title = modules.dearrow.title(videoId, info?.title ?: row.title, DeArrowSurface.VIDEO_PAGE).takeIf { it != (info?.title ?: row.title) }
            val thumb = modules.dearrow.thumbnail(videoId, DeArrowSurface.VIDEO_PAGE)?.let { saveImage(it, "${videoId}.dearrow.jpg") }
            if (title != null || thumb != null) StoredBranding(title, thumb) else null
        } catch (e: CancellationException) { throw e } catch (e: Exception) { null }

        val pages = settings.resolve(Settings.downloadsCommentPages).toInt()
        val comments = mutableListOf<Comment>()
        var continuation: String? = null
        var commentsOk = pages == 0
        for (i in 0 until pages) {
            val page = (extractor.comments(videoId, CommentSort.TOP, continuation) as? ExtractorResult.Success)?.value ?: break
            comments += page.comments
            commentsOk = true
            continuation = page.continuation ?: break
        }
        if (!commentsOk) missing += "comments"

        val stored = info?.let {
            StoredInfo(it.title, it.description, it.channelId, it.channelName, it.durationSeconds, it.viewCount, it.likeCount, it.uploadDate?.toEpochMilliseconds())
        }
        val data = OfflineData(
            downloadId = row.id,
            description = info?.description,
            infoJson = stored?.let { json.encodeToString(StoredInfo.serializer(), it) },
            chaptersJson = json.encodeToString(ListSerializer(com.greenaltambe.vela.extractor.Chapter.serializer()), chapters),
            commentsJson = json.encodeToString(ListSerializer(Comment.serializer()), comments),
            sponsorBlockJson = segments?.let { json.encodeToString(ListSerializer(com.greenaltambe.vela.modules.sponsorblock.Segment.serializer()), it) },
            deArrowJson = branding?.let { json.encodeToString(StoredBranding.serializer(), it) },
            thumbnailPath = thumbnailPath,
        )
        return Result(data, missing)
    }

    private suspend fun saveImage(url: String, name: String): String? = try {
        val buffer = ByteArrayCollector()
        val response = fetcher.fetch(url, emptyMap(), 0, null, buffer)
        if (response.status in 200..299 && buffer.size() > 0) storage().writeAsset(name, buffer.toByteArray()) else null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}

/** Collects a small download in memory. */
class ByteArrayCollector : DownloadSink {
    private var data = ByteArray(1024)
    private var length = 0

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        if (this.length + length > data.size) data = data.copyOf(maxOf(data.size * 2, this.length + length))
        buffer.copyInto(data, this.length, offset, offset + length)
        this.length += length
    }

    override fun close() {}
    fun size() = length
    fun toByteArray(): ByteArray = data.copyOf(length)
}
