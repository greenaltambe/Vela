package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.auth.*
import com.greenaltambe.vela.extractor.VideoSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock

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
) : AccountReads {
    private suspend fun <T> run(block: InnertubeClient.() -> T): T? {
        val account = accounts.active.value
        if (!account.isSignedIn) return null
        val jar = accounts.cookies(account.id) ?: return null
        return try {
            val session = AuthSession(jar, governors.forAccount(account.id), transport, clock, onCookiesChanged = { accounts.saveCookies(account.id, it) }, pageId = { profiles?.pageId(account.id) })
            withContext(Dispatchers.IO) { InnertubeClient(session, web, base).block() }
        } catch (e: AuthFailure.LoggedOut) { accounts.setAuthState(account.id, AuthState.NEEDS_RELOGIN); onFailure("signed out"); null
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (e: Exception) { onFailure(Redaction.redact(e.message ?: e.javaClass.simpleName)); null }
    }

    override suspend fun recommended(): List<VideoSummary>? = run {
        val page = browse("FEwhat_to_watch", kotlin.time.Duration.parse("15s")) // interactive: wait a little, then Home falls back to Trending
        if (InnertubeParsers.isSignedOutPage(page)) throw AuthFailure.LoggedOut("home is the signed-out page")
        InnertubeParsers.videos(page)
    }

    override suspend fun playlists(): List<AccountPlaylist>? = run { playlists().map { AccountPlaylist(it.id, it.title, it.videoCount) } }

    override suspend fun playlistVideos(playlistId: String): List<VideoSummary>? =
        run { playlistVideos(playlistId).map { VideoSummary(it.videoId, it.title, "", it.channelName.orEmpty(), durationSeconds = it.durationSeconds) } }

    override suspend fun history(): List<VideoSummary>? = run {
        val page = browse("FEhistory", kotlin.time.Duration.parse("70s"))
        if (InnertubeParsers.isSignedOutPage(page)) throw AuthFailure.LoggedOut("history is the signed-out page")
        InnertubeParsers.videos(page)
    }
}
