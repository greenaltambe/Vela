package com.greenaltambe.vela.platform

import java.util.concurrent.ConcurrentHashMap

/**
 * Every helper process the app starts (yt-dlp, ffmpeg) while it runs. When the JVM is asked to stop (SIGTERM, logout) Java does not
 * stop its children, so the shutdown hook calls [destroyAll]: no yt-dlp or ffmpeg is left behind to finish a download nobody wants.
 */
object ChildProcesses {
    private val live: MutableSet<Process> = ConcurrentHashMap.newKeySet()

    fun track(process: Process): Process { live += process; return process }
    fun untrack(process: Process) { live -= process }
    val count: Int get() = live.size

    /** Kills every tracked process that is still alive and returns how many were. */
    fun destroyAll(): Int = live.toList().count { p -> (p.isAlive.also { if (it) p.destroyForcibly() }) }.also { live.clear() }
}
