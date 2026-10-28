package com.greenaltambe.vela.net

import com.greenaltambe.vela.downloads.Container
import com.greenaltambe.vela.downloads.MediaMerger
import com.greenaltambe.vela.downloads.MergeException
import com.greenaltambe.vela.downloads.MergeJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Merges with the system `ffmpeg` (`-c copy`, no re-encoding). It is looked up once, lazily; if it is missing
 * [available] is false and [description] says so for Diagnostics, and downloads that need a merge fail with that message
 * but keep their downloaded streams, so installing ffmpeg and retrying costs no re-download.
 */
class FfmpegMediaMerger(private val binary: String = "ffmpeg", private val timeoutMinutes: Long = 30) : MediaMerger {
    private val version: String? by lazy {
        runCatching {
            val p = ProcessBuilder(binary, "-version").redirectErrorStream(true).start()
            val line = p.inputStream.bufferedReader().readLine()
            if (!p.waitFor(10, TimeUnit.SECONDS)) { p.destroyForcibly(); null } else if (p.exitValue() == 0) line else null
        }.getOrNull()
    }

    override val available: Boolean get() = version != null
    override val description: String get() = version ?: "ffmpeg was not found ('$binary'): install it (Fedora: sudo dnf install ffmpeg-free) to merge video and audio"

    override suspend fun merge(job: MergeJob) = run(mergeCommand(binary, job))

    override suspend fun remux(inputPath: String, outputPath: String) = run(remuxCommand(binary, inputPath, outputPath))

    private suspend fun run(command: List<String>) = withContext(Dispatchers.IO) {
        if (!available) throw MergeException(description)
        val process = try {
            com.greenaltambe.vela.platform.ChildProcesses.track(ProcessBuilder(command).redirectErrorStream(true).start())
        } catch (e: java.io.IOException) {
            throw MergeException("could not start ffmpeg: ${e.message}", e)
        }
        try {
            val output = process.inputStream.bufferedReader().readText()
            if (!process.waitFor(timeoutMinutes, TimeUnit.MINUTES)) { process.destroyForcibly(); throw MergeException("ffmpeg timed out") }
            if (process.exitValue() != 0) throw MergeException("ffmpeg exited ${process.exitValue()}: ${output.trim().lines().takeLast(4).joinToString(" | ")}")
        } catch (e: CancellationException) {
            process.destroyForcibly()
            throw e
        } finally {
            com.greenaltambe.vela.platform.ChildProcesses.untrack(process)
        }
    }

    companion object {
        /**
         * Streams are copied, never re-encoded. `-map 0:v:0 -map 1:a:0` takes the first video of the first file and the
         * first audio of the second, whatever else the files carry; MP4 gets the index at the front (`faststart`) so
         * other apps can start playing before the whole file is read.
         */
        fun mergeCommand(binary: String, job: MergeJob): List<String> = buildList {
            addAll(listOf(binary, "-hide_banner", "-loglevel", "error", "-nostdin", "-y"))
            addAll(listOf("-i", job.videoPath, "-i", job.audioPath))
            addAll(listOf("-map", "0:v:0", "-map", "1:a:0", "-c", "copy"))
            if (job.container == Container.MP4) addAll(listOf("-movflags", "+faststart"))
            add(job.outputPath)
        }

        fun remuxCommand(binary: String, input: String, output: String): List<String> =
            listOf(binary, "-hide_banner", "-loglevel", "error", "-nostdin", "-y", "-i", input, "-vn", "-c:a", "copy", output)
    }
}
