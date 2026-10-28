package com.greenaltambe.vela.debug

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.greenaltambe.vela.extractor.chain.TierId
import com.greenaltambe.vela.playback.PlaybackRuntime
import com.greenaltambe.vela.player.PlaybackStatus
import com.greenaltambe.vela.player.SessionState
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Debug-only on-device self-test. Runs on the MAIN thread on purpose (that is where the bugs were): StrictMode kills the
 * process on main-thread network I/O. Never uses the account. Result: files/selftest.json and a SELFTEST line in logcat.
 *   adb shell am start -n com.greenaltambe.vela/.debug.SelfTestActivity --es run all   (all | canary | play)
 */
class SelfTestActivity : Activity() {
    private val results = JSONArray()

    private fun add(name: String, ok: Boolean, detail: String, millis: Long = 0) {
        results.put(JSONObject().put("name", name).put("ok", ok).put("detail", detail).put("millis", millis))
        Log.i(TAG, "${if (ok) "PASS" else "FAIL"} $name: $detail")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A dozing phone blocks app networking, which would make every probe fail for the wrong reason.
        setShowWhenLocked(true); setTurnScreenOn(true); window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val which = intent.getStringExtra("run") ?: "all"
        val video = intent.getStringExtra("video") ?: "dQw4w9WgXcQ"
        DebugStrictMode.install(death = true)
        val rt = PlaybackRuntime.get(this)
        File(filesDir, "selftest.json").delete()
        kotlinx.coroutines.MainScope().launch {
            if (which == "all" || which == "canary") canary(rt)
            if (which == "all" || which == "play") play(rt, video)
            if (which == "all" || which == "download") download(rt)
            if (which == "handoff") handoff(rt, video)
            if (which == "live") live(rt, video)
            if (which == "all") { ytdlp(rt, listOf("dQw4w9WgXcQ", "HtVdAasjOgU")); playAge(rt, "HtVdAasjOgU"); ytdlpDownload(rt) }
            if (which == "all" || which == "strict") strictControl()
            if (which == "alert") alert()
            if (which == "captcha") captcha(rt)
            if (which == "chat") chat(video, intent.getStringExtra("liveVideo") ?: "4xDzrJKXOOY")
            if (which == "focus") focus(rt, video)
            if (which == "playage") playAge(rt, video)
            if (which == "ageoff") ageOff(rt, video)
            if (which == "ytdlpauth") {
                if (intent.getBooleanExtra("throwaway", false)) ytdlpAccount(rt, video)
                else add("account tier", false, "refused: pass --ez throwaway true only when the signed-in account (see run whoami) is the throwaway account")
            }
            if (which == "ytdlpdl") ytdlpDownload(rt)
            if (which == "whoami") { val a = rt.accounts.active.value; add("active account", true, "signedIn=${a.isSignedIn} label=${a.label} id=${a.id} ytdlp=${com.greenaltambe.vela.ytdlp.readYtDlpVersion(rt.embeddedYtDlp.ytDlpFile)} allowAge=${rt.settings.resolve(com.greenaltambe.vela.core.settings.Settings.allowAgeRestricted)} tierOrder=${rt.settings.resolve(com.greenaltambe.vela.core.settings.Settings.accountTierOrder)}") }
            if (which == "ytdlpv") ytdlpVerbose(rt, video, intent.getStringExtra("extra")?.split(" ")?.filter { it.isNotEmpty() } ?: emptyList())
            if (which == "ytdlpold") {
                // Puts the phone back in the fresh-install state: the old yt-dlp release, installed through the real updater.
                val u = com.greenaltambe.vela.ytdlp.EmbeddedYtDlpUpdater(rt.embeddedYtDlp.ytDlpFile, rt.embeddedYtDlp::ensureInit, { true })
                val ok = u.install("2025.11.12"); add("install old yt-dlp for the test", ok, u.status.value)
            }
            if (which == "ytdlpupdate") ytdlpUpdate(rt)
            if (which == "ytdlp") ytdlp(rt, intent.getStringExtra("videos")?.split(",") ?: listOf("dQw4w9WgXcQ"))
            val failed = (0 until results.length()).count { !results.getJSONObject(it).getBoolean("ok") }
            File(filesDir, "selftest.json").writeText(JSONObject().put("device", android.os.Build.MODEL).put("sdk", android.os.Build.VERSION.SDK_INT)
                .put("failed", failed).put("results", results).toString(2))
            Log.i(TAG, "SELFTEST DONE failed=$failed of ${results.length()}")
            finish()
        }
    }

    private suspend fun canary(rt: PlaybackRuntime) {
        val t0 = System.currentTimeMillis()
        rt.health.run()
        add("canary run (main thread)", true, "finished", System.currentTimeMillis() - t0)
        // The newest run, by its run id (never "the last two minutes", which could mix two runs).
        val newest = rt.health.results.value.maxOf { it.run }
        val latest = rt.health.results.value.filter { it.run == newest }
        for (tier in TierId.entries) {
            val rs = latest.filter { it.tier == tier }
            if (rs.isEmpty()) continue
            val bad = rs.filter { !it.ok }
            add("tier ${tier.name}", bad.isEmpty(), if (bad.isEmpty()) "${rs.size} checks ok" else bad.joinToString("; ") { "${it.check}: ${it.detail}" }.take(400))
        }
    }

    private suspend fun play(rt: PlaybackRuntime, video: String) {
        val t0 = System.currentTimeMillis()
        rt.session.start(video)
        val ready = withTimeoutOrNull(40_000) { while (rt.session.state.value !is SessionState.Ready) { if (rt.session.state.value is SessionState.Failed) return@withTimeoutOrNull false; delay(200) }; true }
        add("session resolves $video", ready == true, rt.session.state.value::class.simpleName.orEmpty() + " " + rt.log.attempts.value.lastOrNull()?.detail.orEmpty(), System.currentTimeMillis() - t0)
        if (ready != true) return
        val advanced = withTimeoutOrNull(30_000) { while (rt.controller.state.value.status != PlaybackStatus.PLAYING || rt.controller.state.value.positionMs < 2000) delay(250); true }
        add("playback starts and advances", advanced == true, "status=${rt.controller.state.value.status} pos=${rt.controller.state.value.positionMs}")
        if (advanced != true) return
        val p0 = rt.controller.state.value.positionMs; delay(20_000); val p1 = rt.controller.state.value.positionMs
        add("position advances for 20 s", p1 - p0 in 15_000..26_000, "advanced ${p1 - p0} ms")
        rt.session.seekTo(p1 + 30_000); delay(4_000)
        add("seek", rt.controller.state.value.positionMs >= p1 + 29_000, "pos=${rt.controller.state.value.positionMs}")
        rt.session.pause(); delay(1_500); val ps = rt.controller.state.value
        add("pause", ps.status == PlaybackStatus.PAUSED, "status=${ps.status}")
        rt.session.play(); delay(3_000)
        add("resume", rt.controller.state.value.status == PlaybackStatus.PLAYING, "status=${rt.controller.state.value.status}")
        rt.session.stop()
    }

    /** Live stream measurement: telemetry every 2 s for 40 s, then 60 s back (DVR), then back to the live edge. Records the numbers; passes when playing at the end. */
    private suspend fun live(rt: PlaybackRuntime, video: String) {
        rt.session.start(video)
        withTimeoutOrNull(40_000) { while (rt.controller.state.value.status != PlaybackStatus.PLAYING) delay(250) }
        val sb = StringBuilder(); var last = rt.controller.state.value.positionMs; var stalls = 0
        repeat(20) { i ->
            delay(2_000); val st = rt.controller.state.value
            if (st.status != PlaybackStatus.PLAYING) stalls++
            sb.append("${i * 2}s ${st.status.name.take(4)} +${st.positionMs - last}; "); last = st.positionMs
        }
        add("live: 40 s telemetry (status, ms advanced per 2 s)", stalls <= 3, "stalls=$stalls $sb")
        val edge = rt.controller.state.value.positionMs
        rt.session.seekTo(edge - 60_000); delay(6_000)
        val back = rt.controller.state.value
        add("live: DVR seek 60 s back", back.status == PlaybackStatus.PLAYING && back.positionMs < edge - 40_000, "edge=$edge now=${back.positionMs} ${back.status}")
        rt.session.seekTo(edge + 600_000); delay(6_000)
        val again = rt.controller.state.value
        add("live: seek past the end lands at the live edge", again.status == PlaybackStatus.PLAYING, "pos=${again.positionMs} ${again.status} dur=${again.durationMs}")
        rt.session.stop()
    }

    /** Plays for 75 s while the host toggles Wi-Fi/mobile data with adb; passes if playback is PLAYING and advancing at the end. */
    private suspend fun handoff(rt: PlaybackRuntime, video: String) {
        rt.session.start(video)
        withTimeoutOrNull(40_000) { while (rt.controller.state.value.status != PlaybackStatus.PLAYING) delay(250) }
        val samples = StringBuilder(); var firstPos = rt.controller.state.value.positionMs
        repeat(75) { i ->
            delay(1_000)
            val st = rt.controller.state.value
            if (i % 5 == 0) { samples.append("t=$i ${st.status} pos=${st.positionMs} net=${rt.network.type.value}; "); Log.i(TAG, "handoff t=$i ${st.status} pos=${st.positionMs} net=${rt.network.type.value} note=${rt.session.recoveryNote.value}") }
        }
        val end = rt.controller.state.value
        add("handoff keeps playing", end.status == PlaybackStatus.PLAYING && end.positionMs > firstPos + 40_000, "$samples final=${end.status} pos=${end.positionMs} note=${rt.session.recoveryNote.value}")
        rt.session.stop()
    }

    /**
     * Another audio client takes focus from the player (a same-process stand-in for another app or a call): a transient loss must
     * pause and resume by itself on release, a ducking request must keep playing, a permanent loss must pause and stay paused.
     */
    private suspend fun focus(rt: PlaybackRuntime, video: String) {
        rt.session.start(video)
        withTimeoutOrNull(40_000) { while (rt.controller.state.value.status != PlaybackStatus.PLAYING) delay(250) }
        delay(4_000)
        val am = getSystemService(android.media.AudioManager::class.java)
        val attrs = android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build()
        fun status() = rt.controller.state.value.status
        suspend fun take(gain: Int, hold: Long): Pair<PlaybackStatus, PlaybackStatus> {
            val req = android.media.AudioFocusRequest.Builder(gain).setAudioAttributes(attrs).setOnAudioFocusChangeListener {}.build()
            val granted = am.requestAudioFocus(req)
            delay(2_500); val during = status()
            delay(hold)
            am.abandonAudioFocusRequest(req); delay(3_500)
            Log.i(TAG, "focus gain=$gain granted=$granted during=$during after=${status()}")
            return during to status()
        }
        val (d1, a1) = take(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, 2_000)
        add("transient focus loss pauses, then resumes by itself", d1 == PlaybackStatus.PAUSED && a1 == PlaybackStatus.PLAYING, "during=$d1 after=$a1")
        val (d2, a2) = take(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, 1_000)
        add("ducking request keeps playing", d2 == PlaybackStatus.PLAYING && a2 == PlaybackStatus.PLAYING, "during=$d2 after=$a2")
        val perm = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attrs).setOnAudioFocusChangeListener {}.build()
        am.requestAudioFocus(perm); delay(3_000); val d3 = status(); am.abandonAudioFocusRequest(perm); delay(3_000)
        add("permanent focus loss pauses and stays paused", d3 == PlaybackStatus.PAUSED && status() == PlaybackStatus.PAUSED, "during=$d3 after release=${status()}")
        rt.session.stop()
    }

    /** Posts the health notification with a made-up alert; the host then checks `dumpsys notification` for it. */
    private fun alert() {
        com.greenaltambe.vela.playback.HealthNotifier.show(this, com.greenaltambe.vela.health.HealthAlert(listOf(TierId.ANONYMOUS, TierId.ALT_CLIENT), null, 2))
        add("health notification posted (made-up alert)", true, "check dumpsys notification for channel health_alerts")
    }

    /**
     * Captcha flow without a real captcha: (1) the exemption cookie store accepts a value, sends it only to YouTube hosts and expires it;
     * (2) the captcha activity opens on Google's public reCAPTCHA demo page, shows it, and returns "Done"/"Cancel" to the waiting request.
     * The host taps Done with adb. The real unblock (YouTube accepting the cookie) stays unproven.
     */
    private suspend fun captcha(rt: PlaybackRuntime) {
        var now = kotlin.time.Instant.fromEpochSeconds(1_800_000_000)
        val clock = object : kotlin.time.Clock { override fun now() = now }
        val store = com.greenaltambe.vela.extractor.SettingsExemptionCookie(com.greenaltambe.vela.core.settings.InMemorySettingsStore(), clock, kotlin.time.Duration.parse("6h"))
        store.save("testvalue123")
        val ok = store.headerFor("www.youtube.com") == "GOOGLE_ABUSE_EXEMPTION=testvalue123" && store.headerFor("rr1---sn-x.googlevideo.com") == null && store.headerFor("i.ytimg.com") == null
        now += kotlin.time.Duration.parse("7h")
        add("exemption cookie: accepted, YouTube hosts only, expires after 6 h", ok && store.headerFor("www.youtube.com") == null, "sentToYouTube=$ok expiredAfter7h=${store.headerFor("www.youtube.com") == null}")
        val url = "https://www.google.com/recaptcha/api2/demo"
        val waiting = kotlinx.coroutines.MainScope().async { rt.captcha.request(url, kotlin.time.Duration.parse("60s")) }
        delay(500)
        add("captcha challenge is published for the banner", rt.captcha.pending.value == url, "pending=${rt.captcha.pending.value}")
        startActivity(android.content.Intent(this, com.greenaltambe.vela.auth.CaptchaActivity::class.java).putExtra(com.greenaltambe.vela.auth.CaptchaActivity.EXTRA_URL, url))
        Log.i(TAG, "CAPTCHA activity started; waiting up to 60 s for Done/Cancel")
        val solved = waiting.await()
        add("captcha activity returned (Done without an exemption cookie = not solved)", !solved && rt.captcha.pending.value == null, "solved=$solved pending=${rt.captcha.pending.value}")
    }

    /** Chat on the phone: the replay of an ended stream (offsets, seek) and a live chat; anonymous, through the real client. */
    private suspend fun chat(replayVideo: String, liveVideo: String) {
        val client = com.greenaltambe.vela.innertube.LiveChatClient()
        val replay = client.open(replayVideo)
        add("chat replay opens (isReplay)", replay?.isReplay == true, "video=$replayVideo replay=${replay?.isReplay}")
        if (replay != null) {
            val first = replay.poll()
            add("chat replay first chunk has video offsets", first.messages.isNotEmpty() && first.messages.all { it.offsetMs != null }, "n=${first.messages.size} offsets=${first.messages.take(3).map { it.offsetMs }} next=${first.nextPollMs}")
            replay.seek(600_000)
            val seeked = replay.poll()
            val offs = seeked.messages.mapNotNull { it.offsetMs }
            add("chat replay seek to 600 s returns a chunk that covers 600 s (the panel then shows only messages up to the playhead)", offs.isNotEmpty() && offs.min() <= 600_000 && offs.max() >= 600_000, "n=${offs.size} min=${offs.minOrNull()} max=${offs.maxOrNull()}")
        }
        val live = client.open(liveVideo)
        val batch = live?.poll()
        add("live chat polls", live != null && !live.isReplay && (batch?.messages?.isNotEmpty() == true), "video=$liveVideo n=${batch?.messages?.size} next=${batch?.nextPollMs}")
    }

    /** Positive control for the StrictMode check: a deliberate main-thread disk read must show up in this process's logcat. */
    /**
     * The embedded yt-dlp tier ALONE (not through the chain): init cost, then per video the resolve time, the stream counts and a real
     * 206 on the best video and audio URL. Anonymous: never touches the account.
     */
    private suspend fun ytdlp(rt: PlaybackRuntime, videos: List<String>) {
        val yt = rt.embeddedYtDlp
        val t0 = System.currentTimeMillis()
        val version = try { yt.ensureInit(); com.greenaltambe.vela.ytdlp.readYtDlpVersion(yt.ytDlpFile) } catch (e: Throwable) { add("yt-dlp unpack + --version", false, "${e::class.simpleName}: ${e.message} cause=${e.cause}".take(400), System.currentTimeMillis() - t0); return }
        add("yt-dlp unpack + --version", version != null, "version=$version", System.currentTimeMillis() - t0)
        if (version == null) return
        val ex = com.greenaltambe.vela.extractor.ytdlp.YtDlpExtractor(com.greenaltambe.vela.ytdlp.EmbeddedProcessRunner(yt), timeout = kotlin.time.Duration.parse("120s"))
        val probe = com.greenaltambe.vela.innertube.OkHttpStreamProbe()
        // CPU of finished child processes (python) in ms: /proc/self/stat fields cutime+cstime, in clock ticks (100/s).
        fun childCpuMs(): Long = File("/proc/self/stat").readText().substringAfterLast(')').trim().split(' ').let { (it[13].toLong() + it[14].toLong()) * 10 }
        for (v in videos) {
            val cpu0 = android.os.Process.getElapsedCpuTime(); val child0 = childCpuMs(); val t = System.currentTimeMillis()
            val r = ex.streams(v)
            val ms = System.currentTimeMillis() - t
            when (r) {
                is com.greenaltambe.vela.extractor.ExtractorResult.Failure -> add("yt-dlp streams $v", false, "${r.error::class.simpleName}/${r.error.reason}: ${r.error.message}".take(300), ms)
                is com.greenaltambe.vela.extractor.ExtractorResult.Success -> {
                    val set = r.value
                    val vs = set.videoStreams.firstOrNull(); val au = set.audioStreams.firstOrNull()
                    val vCode = vs?.let { probe.status(it.url, set.headers + it.headers) }; val aCode = au?.let { probe.status(it.url, set.headers + it.headers) }
                    val hCode = set.hlsUrl?.let { probe.status(it, set.headers) }
                    add("yt-dlp streams $v", (vCode == 206 || aCode == 206 || hCode in 200..206) , "video=${set.videoStreams.size} (best ${vs?.height}p) audio=${set.audioStreams.size} hls=${set.hlsUrl != null} GET hls=$hCode video=$vCode audio=$aCode appCpu=${android.os.Process.getElapsedCpuTime() - cpu0}ms pythonCpu=${childCpuMs() - child0}ms", ms)
                }
            }
        }
        val rt2 = Runtime.getRuntime(); add("memory after", true, "java heap used ${(rt2.totalMemory() - rt2.freeMemory()) / 1_000_000} MB; native heap ${android.os.Debug.getNativeHeapAllocatedSize() / 1_000_000} MB")
    }

    /**
     * The account tier ALONE (yt-dlp with the active account's cookies, through its governor) on an age-restricted video. Checks the cookie
     * file is gone afterwards, that the governor audit recorded the call, and that the returned URL answers 206. Only with --ez throwaway true.
     */
    private suspend fun ytdlpAccount(rt: PlaybackRuntime, video: String) {
        val a = rt.accounts.active.value
        if (!a.isSignedIn) { add("account tier", false, "no account signed in"); return }
        val tier = com.greenaltambe.vela.extractor.ytdlp.AuthenticatedYtDlpExtractor(
            governors = rt.governors,
            account = { rt.accounts.cookies(a.id)?.let { jar -> com.greenaltambe.vela.extractor.ytdlp.AccountCookies(a.id, a.label, jar) } },
            onCookiesUpdated = { id, jar -> rt.accounts.saveCookies(id, jar) },
            tempDir = rt.embeddedYtDlp.cookieDir, runner = com.greenaltambe.vela.ytdlp.EmbeddedProcessRunner(rt.embeddedYtDlp),
        )
        val before = rt.audit.recent(a.id).size
        val t0 = System.currentTimeMillis()
        val r = tier.streams(video)
        val ms = System.currentTimeMillis() - t0
        val left = rt.embeddedYtDlp.cookieDir.list()?.toList().orEmpty()
        val audited = rt.audit.recent(a.id).size - before
        when (r) {
            is com.greenaltambe.vela.extractor.ExtractorResult.Failure -> add("account tier $video", false, "${r.error.reason}: ${r.error.message}".take(300) + " | cookie files left=$left audit+$audited", ms)
            is com.greenaltambe.vela.extractor.ExtractorResult.Success -> {
                val s = r.value; val u = s.videoStreams.firstOrNull() ?: s.audioStreams.firstOrNull()?.let { au -> com.greenaltambe.vela.extractor.VideoStream(au.url, au.mimeType, au.codec, au.bitrate, 0, 0, 0) }
                val code = u?.let { com.greenaltambe.vela.innertube.OkHttpStreamProbe().status(it.url, s.headers + it.headers) }
                add("account tier $video", left.isEmpty() && audited > 0 && (code == 206), "label=${a.label} video=${s.videoStreams.size} GET=$code cookie files left=$left audit entries +$audited", ms)
            }
        }
    }

    /** With "Allow age-restricted videos" OFF (switched off for the test, then restored): friendly failure, and neither yt-dlp nor the account is called. */
    private suspend fun ageOff(rt: PlaybackRuntime, video: String) {
        val store = com.greenaltambe.vela.data.SqlDelightSettingsStore(rt.database)
        val key = com.greenaltambe.vela.core.settings.Settings.allowAgeRestricted.key
        val old = store.get(key, com.greenaltambe.vela.core.settings.SettingLayer.Global)
        store.put(key, com.greenaltambe.vela.core.settings.SettingLayer.Global, "false")
        try {
            val mark = rt.log.attempts.value.size; val t0 = System.currentTimeMillis()
            val r = rt.chain.streams(video)
            val tried = rt.log.attempts.value.drop(mark).filter { it.outcome != com.greenaltambe.vela.extractor.chain.AttemptOutcome.NOTE }
            val err = (r as? com.greenaltambe.vela.extractor.ExtractorResult.Failure)?.error
            add("age-restricted OFF: friendly failure, no yt-dlp/account call", err is com.greenaltambe.vela.extractor.ExtractorError.AgeRestrictedDisabled && tried.none { it.tier == TierId.YT_DLP || it.tier == TierId.AUTHENTICATED },
                "error=${err?.let { it::class.simpleName }} tiers=${tried.joinToString { it.tier.name + ":" + it.outcome }}", System.currentTimeMillis() - t0)
        } finally {
            if (old == null) store.remove(key, com.greenaltambe.vela.core.settings.SettingLayer.Global) else store.put(key, com.greenaltambe.vela.core.settings.SettingLayer.Global, old)
        }
    }

    /** The age-restricted video through the whole chain and the player, with "Allow age-restricted videos" switched on for the test only. */
    private suspend fun playAge(rt: PlaybackRuntime, video: String) {
        val store = com.greenaltambe.vela.data.SqlDelightSettingsStore(rt.database)
        val key = com.greenaltambe.vela.core.settings.Settings.allowAgeRestricted.key
        val old = store.get(key, com.greenaltambe.vela.core.settings.SettingLayer.Global)
        store.put(key, com.greenaltambe.vela.core.settings.SettingLayer.Global, "true")
        try {
            val mark = rt.log.attempts.value.size
            play(rt, video)
            val tried = rt.log.attempts.value.drop(mark).filter { it.subject.startsWith("streams(") }
            add("tiers tried for $video", tried.any { it.outcome == com.greenaltambe.vela.extractor.chain.AttemptOutcome.SUCCESS }, tried.joinToString("; ") { "${it.tier}:${it.outcome}${it.reason?.let { r -> "($r)" } ?: ""} ${it.duration.inWholeMilliseconds}ms" }.take(500))
        } finally {
            if (old == null) store.remove(key, com.greenaltambe.vela.core.settings.SettingLayer.Global) else store.put(key, com.greenaltambe.vela.core.settings.SettingLayer.Global, old)
        }
    }

    /** A download whose streams MUST come from the yt-dlp tier (tier order put first for the test, then restored), merged and finished. */
    private suspend fun ytdlpDownload(rt: PlaybackRuntime) {
        val store = com.greenaltambe.vela.data.SqlDelightSettingsStore(rt.database)
        val key = com.greenaltambe.vela.core.settings.Settings.accountTierOrder.key
        val old = store.get(key, com.greenaltambe.vela.core.settings.SettingLayer.Global)
        store.put(key, com.greenaltambe.vela.core.settings.SettingLayer.Global, "YT_DLP,ANONYMOUS,ALT_CLIENT,AUTHENTICATED")
        try {
            val t0 = System.currentTimeMillis()
            val id = rt.downloads.manager.enqueue(com.greenaltambe.vela.downloads.DownloadRequest(
                com.greenaltambe.vela.extractor.VideoSummary("jNQXAC9IVRw", "Me at the zoo", "UC4QobU6STFB0P71PMvOGN5A", "jawed"), maxHeight = 144))
            val done = withTimeoutOrNull(150_000) {
                while (true) {
                    val it = rt.downloads.manager.items.value.firstOrNull { x -> x.id == id }
                    if (it != null && (it.state == com.greenaltambe.vela.data.DownloadState.COMPLETED || it.state == com.greenaltambe.vela.data.DownloadState.FAILED)) return@withTimeoutOrNull it
                    delay(500)
                }
                @Suppress("UNREACHABLE_CODE") null
            }
            val served = rt.log.attempts.value.lastOrNull { it.outcome == com.greenaltambe.vela.extractor.chain.AttemptOutcome.SUCCESS && it.subject.startsWith("streams(") }
            add("download through the yt-dlp tier", done?.state == com.greenaltambe.vela.data.DownloadState.COMPLETED && served?.tier == TierId.YT_DLP, "state=${done?.state} servedBy=${served?.tier} ${served?.detail?.take(120)}", System.currentTimeMillis() - t0)
            rt.downloads.manager.remove(id)
        } finally {
            if (old == null) store.remove(key, com.greenaltambe.vela.core.settings.SettingLayer.Global) else store.put(key, com.greenaltambe.vela.core.settings.SettingLayer.Global, old)
        }
    }

    /** One verbose yt-dlp call with a wall-clock stamp per stderr line, to see where the seconds go. Written to files/ytdlp-verbose.txt. */
    private suspend fun ytdlpVerbose(rt: PlaybackRuntime, video: String, extra: List<String>) {
        val yt = rt.embeddedYtDlp; yt.ensureInit()
        val t0 = System.currentTimeMillis()
        val out = try { com.greenaltambe.vela.ytdlp.EmbeddedProcessRunner(yt).run(listOf("yt-dlp", "-v") + extra + listOf("--dump-single-json", "--no-playlist", "https://www.youtube.com/watch?v=$video"), kotlin.time.Duration.parse("120s"), 64_000_000) } catch (e: Exception) { add("verbose", false, e.toString()); return }
        File(filesDir, "ytdlp-verbose.txt").writeText("exit=${out.exitCode}\n" + out.stderr)
        add("verbose run $extra", out.exitCode == 0, "exit=${out.exitCode} stdoutBytes=${out.stdout.length}", System.currentTimeMillis() - t0)
    }

    /** Updater contract on the real runtime: bad checksum and failed health check change nothing; a real update lands and works. */
    private suspend fun ytdlpUpdate(rt: PlaybackRuntime) {
        val yt = rt.embeddedYtDlp; yt.ensureInit()
        val before = com.greenaltambe.vela.ytdlp.readYtDlpVersion(yt.ytDlpFile)
        val client = okhttp3.OkHttpClient()
        val tag = withContext(kotlinx.coroutines.Dispatchers.IO) { client.newCall(okhttp3.Request.Builder().url("https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest").build()).execute().use { org.json.JSONObject(it.body.string()).getString("tag_name") } }
        add("latest release tag", true, "installed=$before latest=$tag")
        val real = com.greenaltambe.vela.ytdlp.OkHttpFileDownloader()
        val badSums = com.greenaltambe.vela.extractor.ytdlp.Downloader { url, target -> if (url.endsWith("SHA2-256SUMS")) target.writeText("${"0".repeat(64)}  yt-dlp\n") else real.download(url, target) }
        val t0 = System.currentTimeMillis()
        val r1 = com.greenaltambe.vela.ytdlp.EmbeddedYtDlpUpdater(yt.ytDlpFile, yt::ensureInit, { true }, badSums)
        val ok1 = r1.install(tag)
        add("update with a wrong checksum is refused", !ok1 && com.greenaltambe.vela.ytdlp.readYtDlpVersion(yt.ytDlpFile) == before, "${r1.status.value}; version=${com.greenaltambe.vela.ytdlp.readYtDlpVersion(yt.ytDlpFile)}", System.currentTimeMillis() - t0)
        val r2 = com.greenaltambe.vela.ytdlp.EmbeddedYtDlpUpdater(yt.ytDlpFile, yt::ensureInit, { false }, real)
        val ok2 = r2.install(tag)
        add("failed health check rolls back", !ok2 && com.greenaltambe.vela.ytdlp.readYtDlpVersion(yt.ytDlpFile) == before, "${r2.status.value}; version=${com.greenaltambe.vela.ytdlp.readYtDlpVersion(yt.ytDlpFile)}", System.currentTimeMillis() - t0)
        val ex = com.greenaltambe.vela.extractor.ytdlp.YtDlpExtractor(com.greenaltambe.vela.ytdlp.EmbeddedProcessRunner(yt), timeout = kotlin.time.Duration.parse("120s"))
        val r3 = com.greenaltambe.vela.ytdlp.EmbeddedYtDlpUpdater(yt.ytDlpFile, yt::ensureInit, { val r = ex.streams("dQw4w9WgXcQ"); r is com.greenaltambe.vela.extractor.ExtractorResult.Success && r.value.videoStreams.isNotEmpty() }, real)
        val t1 = System.currentTimeMillis()
        val ok3 = r3.install(tag)
        val after = com.greenaltambe.vela.ytdlp.readYtDlpVersion(yt.ytDlpFile)
        add("real update installs and works", ok3 && after == tag, "${r3.status.value}; version=$after; previous kept=${java.io.File(yt.ytDlpFile.parentFile, "yt-dlp.previous").isFile}; leftovers=${yt.ytDlpFile.parentFile?.list()?.toList()}", System.currentTimeMillis() - t1)
    }

    private suspend fun strictControl() {
        File(filesDir, "strict-control.txt").writeText("x")
        val since = System.currentTimeMillis()
        File(filesDir, "strict-control.txt").readText() // deliberate main-thread disk read (and write above)
        delay(500)
        val log = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-s", "StrictMode:*")).inputStream.bufferedReader().readText()
        val hit = log.lineSequence().any { "StrictMode policy violation" in it && (it.contains("DiskRead") || it.contains("DiskWrite")) }
        add("StrictMode positive control (deliberate main-thread disk read is logged)", hit, if (hit) "violation line found in logcat" else "no violation line; the log check would miss real violations", System.currentTimeMillis() - since)
    }

    /** A 19 s video at <=144p: video + audio download and the merge into one file, then removal. */
    private suspend fun download(rt: PlaybackRuntime) {
        val t0 = System.currentTimeMillis()
        val id = rt.downloads.manager.enqueue(com.greenaltambe.vela.downloads.DownloadRequest(
            com.greenaltambe.vela.extractor.VideoSummary("jNQXAC9IVRw", "Me at the zoo", "UC4QobU6STFB0P71PMvOGN5A", "jawed"), maxHeight = 144))
        val done = withTimeoutOrNull(90_000) {
            while (true) {
                val it = rt.downloads.manager.items.value.firstOrNull { x -> x.id == id }
                if (it != null && (it.state == com.greenaltambe.vela.data.DownloadState.COMPLETED || it.state == com.greenaltambe.vela.data.DownloadState.FAILED)) return@withTimeoutOrNull it
                delay(500)
            }
            @Suppress("UNREACHABLE_CODE") null
        }
        add("download + merge", done?.state == com.greenaltambe.vela.data.DownloadState.COMPLETED, done?.toString()?.take(300) ?: "timeout", System.currentTimeMillis() - t0)
        rt.downloads.manager.remove(id)
    }

    companion object { const val TAG = "VelaSelfTest" }
}
