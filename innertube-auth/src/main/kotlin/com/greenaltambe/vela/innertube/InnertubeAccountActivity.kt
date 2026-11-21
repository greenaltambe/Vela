package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.auth.*
import com.greenaltambe.vela.player.ActivityReporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Tells the signed-in account what is watched, the way YouTube's own player does: the video's `playbackTracking` addresses are fetched once with
 * the account's cookies (a signed-in /player request), the "playback" address is pinged when playback starts and the "watchtime" address
 * as time is watched, with the segments played, ending with `final=1`. This is what puts a video in the account's history.
 *
 * Gates, all required: [enabled] (the setting is on and "keep history on this device" is off), a signed-in Google account whose profile is not an
 * unverified one, and, for a downloaded file, [offlineAllowed]. Nothing here may disturb playback: every call returns at once, the work runs on its
 * own scope in order per viewing, at most [MAX_PENDING] pings wait (later ones are dropped; the last one is always kept), the governor's refusals
 * and any failure only count, and [MAX_FAILURES] failures in a row stop reporting for that viewing. No address, token or cookie is ever logged.
 */
class InnertubeAccountActivity(
    private val accounts: AccountManager,
    private val governors: GovernorRegistry,
    private val enabled: () -> Boolean,
    private val offlineAllowed: () -> Boolean = { false },
    /** One diagnostics line saying why a viewing is not reported (fixed wording, no addresses). */
    private val note: (String) -> Unit = {},
    private val transport: AuthTransport = OkHttpAuthTransport(),
    private val web: WebClient = WebClient.bundled(),
    private val clock: Clock = Clock.System,
    private val base: String = "https://www.youtube.com/youtubei/v1",
    private val profiles: ProfileStore? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : ActivityReporter {

    class Tracking(val playbackUrl: String, val watchtimeUrl: String?)

    private class Viewing(val accountId: String, val videoId: String, val durationMs: Long, val startedAt: Instant) {
        val cpn: String = newCpn()
        val queue = Channel<suspend (Viewing) -> Unit>(Channel.UNLIMITED)
        @Volatile var pending = 0
        @Volatile var failures = 0
        @Volatile var stopped = false
        var tracking: Tracking? = null
        var session: AuthSession? = null
    }

    private val viewings = HashMap<String, Viewing>()

    override fun started(key: String, videoId: String, durationMs: Long, offline: Boolean) {
        synchronized(viewings) { viewings.remove(key)?.queue?.close() }
        if (!enabled()) return // the setting is off (or history stays on this device): nothing is said, nothing is sent
        val account = accounts.active.value
        val why = when {
            !account.isSignedIn -> "not signed in to a Google account"
            profiles?.blocksWrites(account.id) == true -> "the chosen profile is not verified"
            offline && !offlineAllowed() -> "this is a downloaded video"
            else -> null
        }
        if (why != null) { note("Not reporting this video to your account: $why."); return }
        val v = Viewing(account.id, videoId, durationMs, clock.now())
        synchronized(viewings) { viewings[key] = v }
        scope.launch { for (job in v.queue) { try { if (!v.stopped) job(v) } finally { v.pending-- } } }
        enqueue(v, always = true) { it.playbackPing() }
    }

    override fun watchTime(key: String, videoId: String, positionMs: Long, durationMs: Long, segments: List<LongRange>, playing: Boolean, final: Boolean) {
        val v = synchronized(viewings) { if (final) viewings.remove(key) else viewings[key] } ?: return
        if (v.videoId != videoId) return
        enqueue(v, always = final) { it.watchTimePing(positionMs, durationMs, segments, playing, final) }
        if (final) v.queue.close()
    }

    private fun enqueue(v: Viewing, always: Boolean, job: suspend (Viewing) -> Unit) {
        if (v.stopped) return
        // Pings that cannot be sent as fast as they are made are dropped (never queued without end); the first and last of a viewing are always kept.
        if (!always && v.pending >= MAX_PENDING) return
        v.pending++
        if (v.queue.trySend(job).isFailure) v.pending--
    }

    // ---- one viewing's requests (run in order, off the caller's thread) ----

    private suspend fun Viewing.playbackPing() {
        val t = tracking ?: fetchTracking(this) ?: return
        send(this, withQuery(t.playbackUrl, mapOf("ver" to "2", "cpn" to cpn, "cmt" to "0", "el" to "detailpage", "len" to seconds(durationMs))))
    }

    private suspend fun Viewing.watchTimePing(positionMs: Long, durationMs: Long, segments: List<LongRange>, playing: Boolean, final: Boolean) {
        if (tracking == null) playbackPing() // the first ping did not get through (a failed request): try again before reporting time
        val url = (tracking ?: return).watchtimeUrl ?: return
        val spans = segments.ifEmpty { listOf(positionMs..positionMs) }
        val params = linkedMapOf(
            "ver" to "2", "cpn" to cpn, "cmt" to seconds(positionMs), "el" to "detailpage",
            "st" to spans.joinToString(",") { seconds(it.first) }, "et" to spans.joinToString(",") { seconds(it.last) },
            "state" to if (playing) "playing" else "paused",
            "rt" to seconds((clock.now() - startedAt).inWholeMilliseconds), "len" to seconds(if (durationMs > 0) durationMs else this.durationMs),
        )
        if (final) params["final"] = "1"
        send(this, withQuery(url, params))
    }

    private fun sessionFor(v: Viewing): AuthSession? {
        v.session?.let { return it }
        val jar = accounts.cookies(v.accountId) ?: return null
        return AuthSession(jar, governors.forAccount(v.accountId), transport, clock, onCookiesChanged = { accounts.saveCookies(v.accountId, it) }, pageId = { profiles?.pageId(v.accountId) }).also { v.session = it }
    }

    private suspend fun fetchTracking(v: Viewing): Tracking? {
        val session = sessionFor(v) ?: return null.also { stop(v, "credentials are missing") }
        val found = attempt(v) {
            val response = InnertubeClient(session, web, base).call("player", buildJsonObject { put("videoId", v.videoId); put("contentCheckOk", true); put("racyCheckOk", true) }, kind = CallKind.REPORT)
            parseTracking(response)
        }
        if (found == null && !v.stopped && v.failures == 0) stop(v, "YouTube returned no tracking address for this video")
        v.tracking = found
        return found
    }

    private suspend fun send(v: Viewing, url: String) {
        val session = sessionFor(v) ?: return
        attempt(v) { session.send(CallKind.REPORT, "GET", url) }
    }

    /** Runs one request off the caller's thread; a failure is only counted, three in a row end this viewing's reporting. */
    private suspend fun <T> attempt(v: Viewing, block: () -> T): T? = try {
        kotlinx.coroutines.withContext(Dispatchers.IO) { block() }.also { v.failures = 0 }
    } catch (e: CancellationException) { throw e
    } catch (e: AuthFailure.LoggedOut) {
        accounts.setAuthState(v.accountId, AuthState.NEEDS_RELOGIN); stop(v, "the account is signed out"); null
    } catch (e: Exception) { // GovernorDenied, HTTP errors, unparseable answers: playback is never told
        if (++v.failures >= MAX_FAILURES) stop(v, "reporting failed $MAX_FAILURES times in a row")
        null
    }

    private fun stop(v: Viewing, why: String) {
        if (v.stopped) return
        v.stopped = true
        note("Stopped reporting this video to your account: $why.")
    }

    companion object {
        const val MAX_PENDING = 3
        const val MAX_FAILURES = 3
        private const val CPN_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

        /** YouTube's client playback nonce: 16 characters of the url-safe alphabet. */
        fun newCpn(): String { val r = java.security.SecureRandom(); return (0 until 16).map { CPN_ALPHABET[r.nextInt(64)] }.joinToString("") }

        internal fun parseTracking(response: kotlinx.serialization.json.JsonElement): Tracking? {
            val tracking = response.at("playbackTracking")
            val playback = tracking.at("videostatsPlaybackUrl", "baseUrl").string?.takeIf { it.startsWith("https://") } ?: return null
            return Tracking(playback, tracking.at("videostatsWatchtimeUrl", "baseUrl").string?.takeIf { it.startsWith("https://") })
        }

        /** Seconds with three decimals, as the web player writes them. */
        internal fun seconds(ms: Long): String = "%d.%03d".format(Math.max(0, ms) / 1000, Math.max(0, ms) % 1000)

        /** [url] with [params] set (an existing parameter of the same name is replaced; the others stay as YouTube sent them). */
        internal fun withQuery(url: String, params: Map<String, String>): String {
            val head = url.substringBefore('?')
            val kept = url.substringAfter('?', "").split('&').filter { it.isNotEmpty() && it.substringBefore('=') !in params.keys }
            val added = params.map { (k, v) -> k + "=" + java.net.URLEncoder.encode(v, "UTF-8") }
            return head + "?" + (kept + added).joinToString("&")
        }
    }
}
