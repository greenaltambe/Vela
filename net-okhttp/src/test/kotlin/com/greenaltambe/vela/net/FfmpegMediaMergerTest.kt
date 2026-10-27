package com.greenaltambe.vela.net

import com.greenaltambe.vela.downloads.Container
import com.greenaltambe.vela.downloads.MergeException
import com.greenaltambe.vela.downloads.MergeJob
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FfmpegCommandTest {
    @Test fun mergeCopiesStreamsAndPicksOneFromEachFile() {
        val cmd = FfmpegMediaMerger.mergeCommand("ffmpeg", MergeJob("/w/v.webm", "/w/a.webm", "/w/out.webm", Container.WEBM))
        assertEquals(listOf("ffmpeg", "-hide_banner", "-loglevel", "error", "-nostdin", "-y", "-i", "/w/v.webm", "-i", "/w/a.webm", "-map", "0:v:0", "-map", "1:a:0", "-c", "copy", "/w/out.webm"), cmd)
        assertFalse("-movflags" in cmd)
    }

    @Test fun mp4GetsFaststartAndNothingIsReEncoded() {
        val cmd = FfmpegMediaMerger.mergeCommand("ffmpeg", MergeJob("v.mp4", "a.m4a", "o.mp4", Container.MP4))
        assertEquals(listOf("-movflags", "+faststart"), cmd.subList(cmd.indexOf("-movflags"), cmd.indexOf("-movflags") + 2))
        assertEquals("copy", cmd[cmd.indexOf("-c") + 1])
        assertTrue(cmd.none { it.startsWith("-c:v") && it != "copy" } && "libx264" !in cmd)
        assertEquals("o.mp4", cmd.last())
    }

    @Test fun remuxDropsVideoAndCopiesAudio() {
        val cmd = FfmpegMediaMerger.remuxCommand("ffmpeg", "in.webm", "out.opus")
        assertTrue("-vn" in cmd && cmd[cmd.indexOf("-c:a") + 1] == "copy" && cmd.last() == "out.opus")
    }

    @Test fun aMissingBinaryIsReportedNotThrownAtConstruction() {
        val merger = FfmpegMediaMerger("definitely-not-ffmpeg-xyz")
        assertFalse(merger.available)
        assertTrue("not found" in merger.description && "ffmpeg" in merger.description)
        assertFailsWith<MergeException> { runBlocking { merger.merge(MergeJob("a", "b", "c", Container.MP4)) } }
    }
}

/** Against the real ffmpeg/ffprobe: tiny streams are generated, merged, and the result inspected. Skipped without them. */
class FfmpegRealMergeTest {
    private val dir = Files.createTempDirectory("vela-ffmpeg").toFile()
    @AfterTest fun cleanUp() { dir.deleteRecursively() }

    private fun run(vararg cmd: String): String {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "timed out: ${cmd.toList()}")
        assertEquals(0, p.exitValue(), "${cmd.toList()}\n$out")
        return out
    }

    private fun encoderAvailable(name: String) = runCatching { run("ffmpeg", "-hide_banner", "-encoders").contains(" $name ") }.getOrDefault(false)

    private fun streams(file: File): List<String> = run("ffprobe", "-v", "error", "-show_entries", "stream=codec_type,codec_name", "-of", "csv=p=0", file.path).trim().lines().sorted()

    private fun duration(file: File): Double = run("ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", file.path).trim().toDouble()

    @Test fun webmAndMp4MergesKeepBothStreamsWithoutReEncoding() {
        val merger = FfmpegMediaMerger()
        assumeTrue("SKIPPED: ffmpeg is not installed", merger.available)
        assumeTrue("SKIPPED: no VP9/Opus encoders to build test input", encoderAvailable("libvpx-vp9") && encoderAvailable("libopus"))

        val v = File(dir, "v.webm"); val a = File(dir, "a.webm")
        run("ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i", "testsrc=duration=2:size=128x72:rate=10", "-c:v", "libvpx-vp9", "-b:v", "50k", v.path)
        run("ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i", "sine=frequency=440:duration=2", "-c:a", "libopus", a.path)
        val out = File(dir, "out.webm")
        runBlocking { merger.merge(MergeJob(v.path, a.path, out.path, Container.WEBM)) }
        assertEquals(listOf("opus,audio", "vp9,video"), streams(out))
        assertEquals(2.0, duration(out), 0.2)
        assertEquals(listOf("vp9,video"), streams(v), "the inputs are untouched")

        // MP4: an AAC track with an MPEG-4 video (encoders every build has) in the container H.264+AAC would use.
        if (encoderAvailable("mpeg4") && encoderAvailable("aac")) {
            val v2 = File(dir, "v.mp4"); val a2 = File(dir, "a.m4a")
            run("ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i", "testsrc=duration=2:size=128x72:rate=10", "-c:v", "mpeg4", v2.path)
            run("ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i", "sine=frequency=330:duration=2", "-c:a", "aac", a2.path)
            val out2 = File(dir, "out.mp4")
            runBlocking { merger.merge(MergeJob(v2.path, a2.path, out2.path, Container.MP4)) }
            assertEquals(listOf("aac,audio", "mpeg4,video"), streams(out2))
            assertEquals(2.0, duration(out2), 0.2)
        }
    }

    @Test fun opusIsRemuxedOutOfWebmIntoOgg() {
        val merger = FfmpegMediaMerger()
        assumeTrue("SKIPPED: ffmpeg is not installed", merger.available)
        assumeTrue("SKIPPED: no Opus encoder", encoderAvailable("libopus"))
        val a = File(dir, "a.webm")
        run("ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i", "sine=frequency=440:duration=1", "-c:a", "libopus", a.path)
        val out = File(dir, "a.opus")
        runBlocking { merger.remux(a.path, out.path) }
        assertEquals(listOf("opus,audio"), streams(out))
        assertEquals("ogg", run("ffprobe", "-v", "error", "-show_entries", "format=format_name", "-of", "csv=p=0", out.path).trim())
    }

    @Test fun aBrokenInputIsAMergeExceptionWithFfmpegsMessage() {
        val merger = FfmpegMediaMerger()
        assumeTrue("SKIPPED: ffmpeg is not installed", merger.available)
        val bad = File(dir, "bad.webm").apply { writeText("not media") }
        val e = assertFailsWith<MergeException> { runBlocking { merger.merge(MergeJob(bad.path, bad.path, File(dir, "o.webm").path, Container.WEBM)) } }
        assertTrue("ffmpeg exited" in e.message!!, e.message)
    }
}
