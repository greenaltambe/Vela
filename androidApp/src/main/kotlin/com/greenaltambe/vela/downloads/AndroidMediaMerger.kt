package com.greenaltambe.vela.downloads

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext

/**
 * Merges with the platform's own [MediaMuxer]: samples are copied from two [MediaExtractor]s into one file, no
 * re-encoding, nothing bundled in the APK.
 *
 * It writes **MP4 and WebM** (and Ogg for an Opus remux on API 29+), not Matroska, and [Containers.forAndroidMuxer]
 * picks between them from the codecs per Android's "Supported media formats" table (H.264 and AV1 only fit MP4, VP9 fits
 * WebM or MP4, Opus fits WebM, Ogg or MP4 from Android 10). A pair the device's muxer still rejects fails the download
 * with the muxer's message and the two streams are kept, so another quality or codec can be chosen.
 *
 * Why not ffmpeg: ffmpeg-kit was retired and its binaries withdrawn, and a community fork would be an unaudited
 * native dependency of unknown maintenance; see the task 11 report.
 */
class AndroidMediaMerger : MediaMerger {
    override val available = true
    override val description = "Android MediaMuxer (no re-encoding; MP4 and WebM output)"
    override val containers = setOf(Container.MP4, Container.WEBM)

    override fun containerFor(videoCodec: String, audioCodec: String): Container = Containers.forAndroidMuxer(videoCodec, audioCodec)

    /** Measured on a Pixel-class OPPO CPH2707 (Android 16): VP9 into MP4 is rejected ("Failed to add the track to the muxer"), and VP9 + AAC fits no other container. */
    override fun canMerge(videoCodec: String, audioCodec: String): Boolean =
        !(com.greenaltambe.vela.player.StreamSelector.codecOf(videoCodec) == com.greenaltambe.vela.player.VideoCodec.VP9 && containerFor(videoCodec, audioCodec) == Container.MP4)

    override suspend fun merge(job: MergeJob) = withContext(Dispatchers.IO) {
        val format = when (job.container) {
            Container.MP4 -> MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            Container.WEBM -> MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
            Container.MKV -> throw MergeException("Android cannot write Matroska; choose H.264 (MP4) or VP9/AV1 with Opus (WebM) for downloads")
        }
        val video = MediaExtractor()
        val audio = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            video.setDataSource(job.videoPath)
            audio.setDataSource(job.audioPath)
            val vTrack = video.firstTrack("video/") ?: throw MergeException("no video track in ${job.videoPath}")
            val aTrack = audio.firstTrack("audio/") ?: throw MergeException("no audio track in ${job.audioPath}")
            video.selectTrack(vTrack); audio.selectTrack(aTrack)
            muxer = MediaMuxer(job.outputPath, format)
            val vOut = try { muxer.addTrack(video.getTrackFormat(vTrack)) } catch (e: RuntimeException) { throw MergeException("this device's muxer cannot put ${video.getTrackFormat(vTrack).getString(MediaFormat.KEY_MIME)} in ${job.container.extension} (${e.message})", e) }
            val aOut = try { muxer.addTrack(audio.getTrackFormat(aTrack)) } catch (e: RuntimeException) { throw MergeException("this device's muxer cannot put ${audio.getTrackFormat(aTrack).getString(MediaFormat.KEY_MIME)} in ${job.container.extension} (${e.message})", e) }
            muxer.start()
            val buffer = ByteBuffer.allocate(BUFFER_BYTES)
            val info = MediaCodec.BufferInfo()
            // Interleave by timestamp so players can read both streams in one pass.
            while (true) {
                coroutineContext.ensureActive()
                val vTime = video.sampleTime
                val aTime = audio.sampleTime
                if (vTime < 0 && aTime < 0) break
                val useVideo = aTime < 0 || (vTime in 0..aTime)
                val (extractor, track) = if (useVideo) video to vOut else audio to aOut
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) { extractor.advance(); continue }
                info.set(0, size, extractor.sampleTime, if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                muxer.writeSampleData(track, buffer, info)
                extractor.advance()
            }
            muxer.stop()
        } catch (e: MergeException) {
            throw e
        } catch (e: java.io.IOException) {
            throw MergeException("could not read the downloaded streams: ${e.message}", e)
        } catch (e: IllegalStateException) {
            throw MergeException("the muxer failed: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw MergeException("the muxer rejected the streams: ${e.message}", e)
        } finally {
            runCatching { muxer?.release() }
            video.release(); audio.release()
        }
    }

    /** Opus out of WebM into Ogg: a stream copy, API 29+. */
    override suspend fun remux(inputPath: String, outputPath: String) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) throw MergeException("remuxing to Ogg needs Android 10")
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(inputPath)
            val track = extractor.firstTrack("audio/") ?: throw MergeException("no audio track in $inputPath")
            extractor.selectTrack(track)
            muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG)
            val out = muxer.addTrack(extractor.getTrackFormat(track))
            muxer.start()
            val buffer = ByteBuffer.allocate(BUFFER_BYTES)
            val info = MediaCodec.BufferInfo()
            while (true) {
                coroutineContext.ensureActive()
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.set(0, size, extractor.sampleTime, 0)
                muxer.writeSampleData(out, buffer, info)
                extractor.advance()
            }
            muxer.stop()
        } catch (e: MergeException) {
            throw e
        } catch (e: Exception) {
            throw MergeException("remux failed: ${e.message}", e)
        } finally {
            runCatching { muxer?.release() }
            extractor.release()
        }
    }

    private fun MediaExtractor.firstTrack(mimePrefix: String): Int? =
        (0 until trackCount).firstOrNull { getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith(mimePrefix) == true }

    private companion object {
        /** A 4K key frame can be a few MB. */
        const val BUFFER_BYTES = 4 * 1024 * 1024
    }
}
