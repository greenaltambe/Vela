package com.greenaltambe.vela.debug

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.greenaltambe.vela.extractor.chain.TierId
import com.greenaltambe.vela.playback.PlaybackRuntime
import com.greenaltambe.vela.player.PlaybackStatus
import com.greenaltambe.vela.player.SessionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
            if (which == "all" || which == "strict") strictControl()
            if (which == "alert") alert()
            if (which == "focus") focus(rt, video)
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

    /** Positive control for the StrictMode check: a deliberate main-thread disk read must show up in this process's logcat. */
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
