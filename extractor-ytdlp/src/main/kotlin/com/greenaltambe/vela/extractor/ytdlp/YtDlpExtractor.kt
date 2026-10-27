package com.greenaltambe.vela.extractor.ytdlp

import com.greenaltambe.vela.extractor.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal const val TOKEN_TIER = "ytdlp"

/**
 * Tier 4: yt-dlp as an external process. Slow (hundreds of ms to seconds per call) and desktop-only, so it is the
 * last resort. Comments and replies are not supported.
 *
 * Continuations are the 1-based index of the next entry (`ytdlp:<n>`); yt-dlp pages by `--playlist-start/-end`.
 * Search has no server-side cursor (`ytsearchN` re-runs the query for N results), so YouTube may rank differently per
 * page and a few results can repeat across pages (about 4 of 20 observed); callers should de-duplicate by id.
 *
 * @param binary resolved on every call so a changed `ytdlp.path` setting applies immediately.
 * @param extraArgs flags placed before the URL, e.g. `--js-runtimes` (see [YtDlpEnvironment]).
 */
class YtDlpExtractor(
    private val runner: ProcessRunner = SystemProcessRunner(),
    private val binary: () -> String = { "yt-dlp" },
    private val extraArgs: () -> List<String> = { emptyList() },
    private val timeout: Duration = 60.seconds,
    private val maxOutputBytes: Int = 32 * 1024 * 1024,
    private val searchPageSize: Int = 20,
    private val maxSearchResults: Int = 100,
    private val channelPageSize: Int = 30,
) : Extractor {

    override suspend fun search(query: String, continuation: String?): ExtractorResult<Page<VideoSummary>> {
        val start = startIndex(continuation) ?: return ContinuationToken.foreign()
        val end = start + searchPageSize - 1
        return dump(listOf("--flat-playlist", "--playlist-start", "$start", "--playlist-end", "$end", "ytsearch$end:$query")) {
            val items = it.flatVideos()
            // A full page may be followed by more; stop at the cap because ytsearchN refetches everything up to N.
            Page(items, nextToken(it.rawEntryCount(), searchPageSize, end).takeIf { end < maxSearchResults })
        }
    }

    override suspend fun videoInfo(videoId: String) =
        dump(listOf("--no-playlist", videoUrl(videoId))) { it.toVideoInfo() }

    override suspend fun streams(videoId: String) =
        dump(listOf("--no-playlist", videoUrl(videoId))) { it.toStreamSet() }

    override suspend fun channel(channelId: String): ExtractorResult<ChannelPage> {
        val end = channelPageSize
        return dump(channelArgs(channelId, 1, end)) { json ->
            json.toChannelPage(channelId, nextToken(json.rawEntryCount(), channelPageSize, end))
        }
    }

    override suspend fun channelVideos(channelId: String, continuation: String?): ExtractorResult<Page<VideoSummary>> {
        val start = startIndex(continuation) ?: return ContinuationToken.foreign()
        val end = start + channelPageSize - 1
        return dump(channelArgs(channelId, start, end)) {
            Page(it.flatVideos(), nextToken(it.rawEntryCount(), channelPageSize, end))
        }
    }

    override suspend fun comments(videoId: String, sort: CommentSort, continuation: String?): ExtractorResult<CommentsPage> =
        notSupported()

    override suspend fun replies(videoId: String, commentId: String, continuation: String?): ExtractorResult<CommentsPage> =
        notSupported()

    private fun notSupported() = ExtractorResult.Failure(ExtractorError.Unplayable("not supported by yt-dlp tier"))

    private fun channelArgs(channelId: String, start: Int, end: Int) =
        listOf("--flat-playlist", "--playlist-start", "$start", "--playlist-end", "$end", channelVideosUrl(channelId))

    /** 1-based index of the first entry wanted; null for a token that isn't ours. */
    private fun startIndex(continuation: String?): Int? =
        if (continuation == null) 1
        else ContinuationToken.unwrap(TOKEN_TIER, continuation)?.toIntOrNull()?.takeIf { it >= 1 }

    /**
     * Entries yt-dlp returned, before we drop the ones that aren't videos: a search for "rick astley" yields 20
     * entries but only 19 videos, and that must not look like the last page.
     */
    private fun JsonObject.rawEntryCount() = (this["entries"] as? kotlinx.serialization.json.JsonArray)?.size ?: 0

    /** A full page suggests more follow; a short one is the end. */
    private fun nextToken(returned: Int, pageSize: Int, end: Int): String? =
        if (returned >= pageSize) ContinuationToken.wrap(TOKEN_TIER, "${end + 1}") else null

    private suspend fun <T> dump(args: List<String>, parse: (JsonObject) -> T): ExtractorResult<T> = try {
        val command = listOf(binary()) + extraArgs() + listOf("--dump-single-json", "--no-warnings") + args
        val output = runner.run(command, timeout, maxOutputBytes)
        if (output.exitCode != 0) {
            ExtractorResult.Failure(mapYtDlpFailure(output.exitCode, output.stderr))
        } else {
            ExtractorResult.Success(parse(Json.parseToJsonElement(output.stdout).jsonObject))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: ProcessStartException) {
        ExtractorResult.Failure(ExtractorError.TierUnavailable(e.message ?: "yt-dlp cannot be started", e))
    } catch (e: ProcessTimeoutException) {
        ExtractorResult.Failure(ExtractorError.Network("yt-dlp timed out after ${e.timeout}", e))
    } catch (e: OutputTooLargeException) {
        ExtractorResult.Failure(ExtractorError.ParseFailure("yt-dlp output larger than ${e.limitBytes} bytes", e))
    } catch (e: Exception) {
        ExtractorResult.Failure(ExtractorError.ParseFailure(e.message ?: "Unexpected yt-dlp output", e))
    }
}
