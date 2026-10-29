package com.greenaltambe.vela.extractor.ytdlp

import com.greenaltambe.vela.auth.BlockPatterns
import com.greenaltambe.vela.auth.CallKind
import com.greenaltambe.vela.auth.CookieJar
import com.greenaltambe.vela.auth.GovernorDenied
import com.greenaltambe.vela.auth.GovernorRegistry
import com.greenaltambe.vela.extractor.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID

/** The signed-in account tier 2 acts as; null from the provider means nobody is signed in. [label] is safe to log. */
class AccountCookies(val accountId: String, val label: String, val jar: CookieJar)

/**
 * Tier 2 on desktop: yt-dlp with `--cookies`. The jar is written to a private temp file (0600) for the duration of one
 * call and deleted afterwards; yt-dlp rewrites that file with the cookies Google rotated during the call, and those
 * are handed to [onCookiesUpdated] so the stored credentials stay fresh. Calls are serialised because the file path is
 * passed through the shared argument provider.
 *
 * Measured (docs/reports/13-accounts.md): with cookies and a JS runtime, an age-restricted video that is unplayable
 * anonymously returns its formats; without a JS runtime yt-dlp returns storyboards only, which this reports as Unplayable.
 */
class AuthenticatedYtDlpExtractor(
    private val account: () -> AccountCookies?,
    private val governors: GovernorRegistry,
    private val onCookiesUpdated: (accountId: String, jar: CookieJar) -> Unit,
    private val tempDir: File = File(System.getProperty("java.io.tmpdir")),
    private val runner: ProcessRunner = SystemProcessRunner(),
    private val binary: () -> String = { "yt-dlp" },
    private val extraArgs: () -> List<String> = { emptyList() },
) : Extractor {
    private val lock = Mutex()
    @Volatile private var cookieFile: File? = null

    private val inner = YtDlpExtractor(
        runner = runner, binary = binary,
        extraArgs = { extraArgs() + (cookieFile?.let { listOf("--cookies", it.path) } ?: emptyList()) },
    )

    private suspend fun <T> withAccount(kind: CallKind = CallKind.BROWSE, call: suspend (Extractor) -> ExtractorResult<T>): ExtractorResult<T> {
        val acct = account() ?: return ExtractorResult.Failure(ExtractorError.TierUnavailable("No signed-in account"))
        val governor = governors.forAccount(acct.accountId)
        return try {
            governor.call(kind, "www.youtube.com", inspect = { r: ExtractorResult<T> ->
                // yt-dlp reports a block as text on stderr, which the extractor turned into the failure message.
                (r as? ExtractorResult.Failure)?.error?.let { e ->
                    if (e.reason == FailureReason.SIGN_IN_REQUIRED) governor.loginRequired()
                    BlockPatterns.scan(e.message)
                }
            }) {
                lock.withLock {
                    val file = File(tempDir, "vela-${UUID.randomUUID()}.cookies.txt")
                    try {
                        writePrivate(file, acct.jar.toNetscape())
                        cookieFile = file
                        val result = call(inner)
                        runCatching { CookieJar.parseNetscape(file.readText()) }.getOrNull()
                            ?.takeIf { it.looksSignedIn && it.toNetscape() != acct.jar.toNetscape() }
                            ?.let { onCookiesUpdated(acct.accountId, it) }
                        result
                    } finally {
                        cookieFile = null
                        file.delete()
                    }
                }
            }
        } catch (d: GovernorDenied) {
            ExtractorResult.Failure(ExtractorError.TierUnavailable(d.message ?: "Account traffic is paused"))
        }
    }

    private fun writePrivate(file: File, text: String) {
        try {
            Files.createFile(file.toPath(), java.nio.file.attribute.PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        } catch (_: UnsupportedOperationException) { file.createNewFile() }
        file.writeText(text)
    }

    override val capabilities get() = inner.capabilities
    override suspend fun search(query: String, continuation: String?) = withAccount { it.search(query, continuation) }
    override suspend fun videoInfo(videoId: String) = withAccount(CallKind.PLAYBACK) { it.videoInfo(videoId) }
    override suspend fun streams(videoId: String) = withAccount(CallKind.PLAYBACK) { it.streams(videoId) }
    override suspend fun channel(channelId: String) = withAccount { it.channel(channelId) }
    override suspend fun channelVideos(channelId: String, continuation: String?) = withAccount { it.channelVideos(channelId, continuation) }
    override suspend fun comments(videoId: String, sort: CommentSort, continuation: String?) = withAccount { it.comments(videoId, sort, continuation) }
    override suspend fun replies(videoId: String, commentId: String, continuation: String?) = withAccount { it.replies(videoId, commentId, continuation) }
    override suspend fun trending(continuation: String?) = withAccount { it.trending(continuation) }
    override suspend fun related(videoId: String) = withAccount { it.related(videoId) }
    override suspend fun channelShorts(channelId: String, continuation: String?) = withAccount { it.channelShorts(channelId, continuation) }
    override suspend fun channelLive(channelId: String, continuation: String?) = withAccount { it.channelLive(channelId, continuation) }
    override suspend fun channelPlaylists(channelId: String, continuation: String?) = withAccount { it.channelPlaylists(channelId, continuation) }
    override suspend fun playlistVideos(playlistId: String, continuation: String?) = withAccount { it.playlistVideos(playlistId, continuation) }
    override suspend fun recentUploads(channelId: String) = withAccount { it.recentUploads(channelId) }
}
