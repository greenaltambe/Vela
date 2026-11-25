package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.auth.*
import com.greenaltambe.vela.extractor.ContinuationToken
import com.greenaltambe.vela.extractor.Page
import com.greenaltambe.vela.extractor.PlaylistContents
import com.greenaltambe.vela.extractor.VideoSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/** Stamps the account's Shorts tokens, so that one is never sent without the account, nor an anonymous one with it. */
private const val SHORTS_TOKEN = "account-shorts"
private const val PLAYLIST_TOKEN = "account-playlist"

/** [AccountReads] over youtubei for the active account: BROWSE calls through that account's governor; any failure is a null so the caller falls back. */
class InnertubeAccountReads(
    private val accounts: AccountManager,
    private val governors: GovernorRegistry,
    private val transport: AuthTransport = OkHttpAuthTransport(),
    private val web: WebClient = WebClient.bundled(),
    private val clock: Clock = Clock.System,
    private val base: String = "https://www.youtube.com/youtubei/v1",
    /** The chosen profile per account (null = never a non-default one). */
    private val profiles: com.greenaltambe.vela.auth.ProfileStore? = null,
    private val onFailure: (String) -> Unit = {},
    /**
     * "Region for content", read at every Home and playlist page request (a blank value means US). Null leaves the bundled client's own.
     * The history and the playlist sync do not ask for it: those are the account's own lists, the same everywhere.
     */
    private val region: (() -> String)? = null,
    /** Told when a Home response was not entirely as expected (see [HomeParseReport.problem]). */
    private val onNote: (String) -> Unit = {},
) : AccountReads {
    private fun homeClient(): WebClient = region?.let { web.copy(gl = it().trim().ifEmpty { "US" }) } ?: web

    private suspend fun <T> run(client: () -> WebClient = { web }, block: InnertubeClient.() -> T): T? {
        val account = accounts.active.value
        if (!account.isSignedIn) return null
        val jar = accounts.cookies(account.id) ?: return null
        return try {
            val session = AuthSession(jar, governors.forAccount(account.id), transport, clock, onCookiesChanged = { accounts.saveCookies(account.id, it) }, pageId = { profiles?.pageId(account.id) })
            withContext(Dispatchers.IO) { InnertubeClient(session, client(), base).block() }
        } catch (e: AuthFailure.LoggedOut) { accounts.setAuthState(account.id, AuthState.NEEDS_RELOGIN); onFailure("signed out"); null
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (e: Exception) { onFailure(Redaction.redact(e.message ?: e.javaClass.simpleName)); null }
    }

    override suspend fun recommended(): List<VideoSummary>? = run(::homeClient) {
        val page = browse("FEwhat_to_watch", kotlin.time.Duration.parse("15s")) // interactive: wait a little, then Home falls back to Trending
        if (InnertubeParsers.isSignedOutPage(page)) throw AuthFailure.LoggedOut("home is the signed-out page")
        readHome(page).videos
    }

    private fun readHome(page: kotlinx.serialization.json.JsonElement): HomeFeed = HomeFeedParser.parse(page).also { feed -> feed.report.problem()?.let(onNote) }

    override suspend fun home(chip: String?, continuation: String?): HomePage? = run(::homeClient) {
        val interactive = kotlin.time.Duration.parse("15s") // a person is waiting: a little patience, then the caller shows Retry
        val first = chip == null && continuation == null
        val page = when {
            continuation != null -> continuation(continuation, interactive)
            chip != null -> continuation(chip, interactive)
            else -> browse("FEwhat_to_watch", interactive)
        }
        if (first && InnertubeParsers.isSignedOutPage(page)) throw AuthFailure.LoggedOut("home is the signed-out page")
        // The chips ride on the first page of the unfiltered feed only.
        val feed = readHome(page)
        HomePage(feed.videos, feed.continuation, if (first) InnertubeParsers.homeChips(page) else emptyList(), feed.groups)
    }

    override suspend fun shorts(seed: String?, continuation: String?): Page<VideoSummary>? {
        val token = if (continuation != null) ContinuationToken.unwrap(SHORTS_TOKEN, continuation) ?: return null else seed
        return run(::homeClient) {
            val page = ShortsFeed.load(token) { endpoint, body, mask ->
                try { call(endpoint, body, patience = kotlin.time.Duration.parse("15s"), fieldMask = mask) } catch (e: AuthFailure.Http) { if (e.code == 400) null else throw e }
            }
            // A sequence that begins with nothing is a failed read, not an empty feed: the caller turns to another source.
            if (token == null && page.items.isEmpty()) throw AuthFailure.Unparseable("the Shorts feed named no Short")
            Page(page.items, page.continuation?.let { ContinuationToken.wrap(SHORTS_TOKEN, it) })
        }
    }

    override suspend fun playlists(): List<AccountPlaylist>? = run { playlists().map { AccountPlaylist(it.id, it.title, it.videoCount) } }

    override suspend fun playlistVideos(playlistId: String): List<VideoSummary>? =
        run { playlistVideos(playlistId).map { it.toSummary() } }

    override suspend fun playlistListing(playlistId: String): PlaylistListing? =
        run { playlistListing(playlistId).let { (vids, complete) -> PlaylistListing(vids.map { it.toSummary() }, complete) } }

    private fun AccountPlaylistVideo.toSummary() = VideoSummary(videoId, title, channelId, channelName.orEmpty(), durationSeconds = durationSeconds)

    override suspend fun playlistPage(playlistId: String, continuation: String?): PlaylistContents? {
        val token = if (continuation != null) ContinuationToken.unwrap(PLAYLIST_TOKEN, continuation) ?: return null else null
        return run(::homeClient) {
            val patience = kotlin.time.Duration.parse("15s") // a person is waiting
            val page = if (token == null) browse("VL$playlistId", patience) else continuation(token, patience)
            if (token == null && InnertubeParsers.isSignedOutPage(page)) throw AuthFailure.LoggedOut("the playlist is the signed-out page")
            PlaylistPageParser.contents(page, playlistId, first = token == null, signedIn = true) { ContinuationToken.wrap(PLAYLIST_TOKEN, it) }
                ?: throw AuthFailure.Unparseable("the playlist page was not understood")
        }
    }

    override suspend fun history(): List<VideoSummary>? = run {
        val page = browse("FEhistory", kotlin.time.Duration.parse("70s"))
        if (InnertubeParsers.isSignedOutPage(page)) throw AuthFailure.LoggedOut("history is the signed-out page")
        InnertubeParsers.videos(page)
    }

    /** Read-only listing through the governor as BROWSE with patience (it waits for the budget rather than failing), like the playlist sync. */
    override suspend fun historyPage(continuation: String?): HistoryPage? = run {
        val listing = kotlin.time.Duration.parse("70s")
        val page = if (continuation == null) browse("FEhistory", listing) else continuation(continuation, listing)
        if (InnertubeParsers.isSignedOutPage(page)) throw AuthFailure.LoggedOut("history is the signed-out page")
        HistoryPage(InnertubeParsers.videos(page), InnertubeParsers.continuationToken(page))
    }
}
