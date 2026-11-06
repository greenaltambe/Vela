package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.auth.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    /** The chosen profile per account (null = never a non-default one). */
    private val profiles: com.greenaltambe.vela.auth.ProfileStore? = null,
) : AccountActions {

    private sealed interface Attempt<out T> {
        class Done<T>(val result: ActionResult, val value: T?) : Attempt<T>
        class Denied(val denial: GovernorDenied) : Attempt<Nothing>
    }

    private suspend fun <T> attempt(block: InnertubeClient.() -> T): Attempt<T> {
        val account = accounts.active.value
        if (!account.isSignedIn) return Attempt.Done(ActionResult.LocalOnly, null)
        if (profiles?.blocksWrites(account.id) == true) return Attempt.Done(ActionResult.Failed("The chosen profile is not verified; nothing is written to it."), null)
        val jar = accounts.cookies(account.id) ?: return Attempt.Done(ActionResult.Failed("Credentials are missing; sign in again."), null)
        return try {
            val session = AuthSession(jar, governors.forAccount(account.id), transport, clock, onCookiesChanged = { accounts.saveCookies(account.id, it) }, pageId = { profiles?.pageId(account.id) })
            Attempt.Done(ActionResult.Ok, withContext(Dispatchers.IO) { InnertubeClient(session, web, base).block() })
        } catch (e: AuthFailure.LoggedOut) {
            accounts.setAuthState(account.id, AuthState.NEEDS_RELOGIN); Attempt.Done(ActionResult.Failed("Signed out; sign in again."), null)
        } catch (e: GovernorDenied) {
            Attempt.Denied(e)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (e: Exception) {
            Attempt.Done(ActionResult.Failed(Redaction.redact(e.message ?: e.javaClass.simpleName)), null)
        }
    }

    private suspend fun <T> run(block: InnertubeClient.() -> T): Pair<ActionResult, T?> = when (val a = attempt(block)) {
        is Attempt.Done -> a.result to a.value
        is Attempt.Denied -> ActionResult.Failed(friendly(a.denial)) to null
    }

    /** What the viewer reads when the governor refuses: plain words and the viewer's own clock, not "write budget used up until 05:07 UTC". */
    internal fun friendly(d: GovernorDenied): String {
        val at = d.until?.let { localTime(it) }
        return when (d.reason) {
            DenyReason.BUDGET -> "Hourly account-action limit reached." + (at?.let { " Try again after $it." } ?: "")
            DenyReason.COOLING_DOWN -> "YouTube asked Vela to slow down; the account is resting" + (at?.let { " until $it." } ?: ".")
            DenyReason.KILL_SWITCH -> "Account traffic is switched off in Settings."
        }
    }

    private fun localTime(t: kotlin.time.Instant): String =
        java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.getDefault()).withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(t.toEpochMilliseconds()))

    // ---- the queue: a write the hourly limit refused is kept (the UI keeps its local change) and sent when the limit frees a slot ----

    private class Pending(val accountId: String, val block: InnertubeClient.() -> Unit)
    private val pending = LinkedHashMap<String, Pending>()
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    private var drain: kotlinx.coroutines.Job? = null
    private var nextTry: kotlin.time.Instant = kotlin.time.Instant.DISTANT_PAST

    /** Writes waiting for the limit (shown in tests and diagnostics). */
    val queuedCount: Int get() = synchronized(pending) { pending.size }

    /**
     * Runs a write. When only the hourly limit stands in the way, the write is queued under [key] (a later write on the same thing replaces an
     * earlier one: like then un-like sends only the last) and the result is [ActionResult.Queued].
     */
    private suspend fun queueable(key: String, block: InnertubeClient.() -> Unit): ActionResult = when (val a = attempt(block)) {
        is Attempt.Done -> a.result
        is Attempt.Denied -> {
            val until = a.denial.until
            if (a.denial.reason != DenyReason.BUDGET || until == null) ActionResult.Failed(friendly(a.denial))
            else {
                val account = accounts.active.value.id
                synchronized(pending) { pending.remove(key); pending[key] = Pending(account, block); nextTry = until }
                startDrain()
                ActionResult.Queued("Saved here. It will reach your account after ${localTime(until)} (hourly account-action limit).")
            }
        }
    }

    private fun startDrain() {
        synchronized(pending) {
            if (drain?.isActive == true) return
            drain = scope.launch {
                while (true) {
                    val wait = (nextTry - clock.now()).inWholeMilliseconds.coerceAtLeast(1_000) + 500
                    kotlinx.coroutines.delay(wait)
                    val batch = synchronized(pending) { pending.entries.toList().map { it.key to it.value } }
                    if (batch.isEmpty()) break
                    for ((key, op) in batch) {
                        if (accounts.active.value.id != op.accountId) { synchronized(pending) { pending.remove(key) }; continue }
                        when (val a = attempt(op.block)) {
                            is Attempt.Denied -> { a.denial.until?.let { synchronized(pending) { nextTry = it } }; break }
                            is Attempt.Done -> synchronized(pending) { pending.remove(key) }
                        }
                    }
                    if (synchronized(pending) { pending.isEmpty() }) break
                }
            }
        }
    }

    private suspend fun unit(block: InnertubeClient.() -> Unit) = run(block).first

    override suspend fun subscribe(channelId: String) = queueable("sub:$channelId") { subscribe(channelId) }
    override suspend fun unsubscribe(channelId: String) = queueable("sub:$channelId") { unsubscribe(channelId) }
    override suspend fun rate(videoId: String, rating: VideoRating) = queueable("rate:$videoId") { rate(videoId, rating) }
    override suspend fun addToPlaylist(playlistId: String, videoId: String) = queueable("pl:$playlistId:$videoId") { addToPlaylist(playlistId, videoId) }
    override suspend fun removeFromPlaylist(playlistId: String, videoId: String) = queueable("pl:$playlistId:$videoId") { removeFromPlaylist(playlistId, videoId) }
    override suspend fun createPlaylist(title: String): Pair<ActionResult, String?> = run { createPlaylist(title) }
    override suspend fun renamePlaylist(playlistId: String, title: String) = unit { renamePlaylist(playlistId, title) }
    override suspend fun deletePlaylist(playlistId: String) = unit { deletePlaylist(playlistId) }
}
