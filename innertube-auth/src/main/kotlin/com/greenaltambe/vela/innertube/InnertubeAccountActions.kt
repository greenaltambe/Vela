package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.auth.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/** [AccountActions] over youtubei for the active account. Every call is a `CallKind.WRITE` through that account's governor. */
class InnertubeAccountActions(
    private val accounts: AccountManager,
    private val governors: GovernorRegistry,
    private val transport: AuthTransport = OkHttpAuthTransport(),
    private val web: WebClient = WebClient.bundled(),
    private val clock: Clock = Clock.System,
    private val base: String = "https://www.youtube.com/youtubei/v1",
) : AccountActions {

    private suspend fun <T> run(block: InnertubeClient.() -> T): Pair<ActionResult, T?> {
        val account = accounts.active.value
        if (!account.isSignedIn) return ActionResult.LocalOnly to null
        val jar = accounts.cookies(account.id) ?: return ActionResult.Failed("Credentials are missing; sign in again.") to null
        return try {
            val session = AuthSession(jar, governors.forAccount(account.id), transport, clock, onCookiesChanged = { accounts.saveCookies(account.id, it) })
            ActionResult.Ok to withContext(Dispatchers.IO) { InnertubeClient(session, web, base).block() }
        } catch (e: AuthFailure.LoggedOut) {
            accounts.setAuthState(account.id, AuthState.NEEDS_RELOGIN); ActionResult.Failed("Signed out; sign in again.") to null
        } catch (e: GovernorDenied) {
            ActionResult.Failed(e.message ?: "Account traffic is paused.") to null
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (e: Exception) {
            ActionResult.Failed(Redaction.redact(e.message ?: e.javaClass.simpleName)) to null
        }
    }

    private suspend fun unit(block: InnertubeClient.() -> Unit) = run(block).first

    override suspend fun subscribe(channelId: String) = unit { subscribe(channelId) }
    override suspend fun unsubscribe(channelId: String) = unit { unsubscribe(channelId) }
    override suspend fun rate(videoId: String, rating: VideoRating) = unit { rate(videoId, rating) }
    override suspend fun addToPlaylist(playlistId: String, videoId: String) = unit { addToPlaylist(playlistId, videoId) }
    override suspend fun removeFromPlaylist(playlistId: String, videoId: String) = unit { removeFromPlaylist(playlistId, videoId) }
    override suspend fun createPlaylist(title: String): Pair<ActionResult, String?> = run { createPlaylist(title) }
    override suspend fun renamePlaylist(playlistId: String, title: String) = unit { renamePlaylist(playlistId, title) }
    override suspend fun deletePlaylist(playlistId: String) = unit { deletePlaylist(playlistId) }
}
