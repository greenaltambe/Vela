package com.greenaltambe.vela.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.HttpUtil
import androidx.media3.datasource.TransferListener

/**
 * Reads one resource as a series of bounded `Range` requests of [chunkBytes] each instead of one open-ended request.
 *
 * Why: googlevideo throttles open-ended requests to roughly the media bitrate (measured: 5.7 Mbit/s against
 * 71-84 Mbit/s for bounded 10 MB ranges, see docs/reports/07-playback-tuning.md), which is too slow for high
 * resolutions. Bounded ranges are not throttled.
 *
 * - Chunks are requested one after the other on the single [upstream] source, so every request carries the headers
 *   the upstream was configured with.
 * - The total size comes from the first response's `Content-Range` and is reported from [open] as the bytes left, so
 *   the player knows the length exactly as with an open-ended request.
 * - Seeking is the player's own close + [open] at the new position; each [open] starts a fresh chunk there.
 * - If the server does not answer a range with `Content-Range` (it ignored the range), the request is repeated
 *   once, as originally asked, and streamed through unchunked.
 * - A chunk that comes back short, or a 416 past the end, is the end of the resource.
 */
@UnstableApi
class ChunkedDataSource(
    private val upstream: DataSource,
    private val chunkBytes: Long = DEFAULT_CHUNK_BYTES,
) : DataSource {
    private var spec: DataSpec? = null
    private var position = 0L
    private var remaining = C.LENGTH_UNSET.toLong() // bytes still to deliver, or LENGTH_UNSET
    private var chunkOpen = false
    private var chunkRequested = 0L
    private var chunkRead = 0L
    private var passThrough = false

    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        require(chunkBytes > 0) { "chunkBytes must be positive" }
        spec = dataSpec
        position = dataSpec.position
        remaining = dataSpec.length
        passThrough = false
        openChunk() ?: return 0 // 416 at the very start: empty
        val total = HttpUtil.getDocumentSize(contentRange())
        if (total == C.LENGTH_UNSET.toLong()) {
            // The server ignored the range. The upstream cut that response off at the chunk length, so ask again for
            // everything the caller wanted and stream that through untouched.
            upstream.close()
            chunkOpen = false
            passThrough = true
            remaining = upstream.open(dataSpec.buildUpon().build())
            chunkOpen = true
            return remaining
        }
        val left = (total - position).coerceAtLeast(0)
        remaining = if (remaining == C.LENGTH_UNSET.toLong()) left else minOf(remaining, left)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (passThrough) return upstream.read(buffer, offset, length)
        while (true) {
            if (remaining == 0L) return C.RESULT_END_OF_INPUT
            if (!chunkOpen && openChunk() == null) return C.RESULT_END_OF_INPUT
            val read = upstream.read(buffer, offset, length)
            if (read == C.RESULT_END_OF_INPUT) {
                upstream.close()
                chunkOpen = false
                // A chunk that ended before the bytes asked for is the end of the resource.
                if (chunkRead < chunkRequested) return C.RESULT_END_OF_INPUT
                continue
            }
            chunkRead += read
            position += read
            if (remaining != C.LENGTH_UNSET.toLong()) remaining -= read
            return read
        }
    }

    override fun getUri(): Uri? = upstream.uri ?: spec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        try {
            if (chunkOpen) upstream.close()
        } finally {
            chunkOpen = false
            spec = null
        }
    }

    /** Opens the chunk at [position]; returns the bytes the response carries, or null for 416 (past the end). */
    private fun openChunk(): Long? {
        val base = checkNotNull(spec)
        val want = if (remaining == C.LENGTH_UNSET.toLong()) chunkBytes else minOf(chunkBytes, remaining)
        val chunkSpec = base.buildUpon().setPosition(position).setLength(want).build()
        val length = try {
            upstream.open(chunkSpec)
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            if (e.responseCode == 416 && position > 0) return null
            throw e
        }
        chunkOpen = true
        chunkRequested = want
        chunkRead = 0
        return length
    }

    private fun contentRange(): String? =
        upstream.responseHeaders.entries.firstOrNull { it.key.equals("Content-Range", ignoreCase = true) }?.value?.firstOrNull()

    class Factory(
        private val upstream: DataSource.Factory,
        private val chunkBytes: Long = DEFAULT_CHUNK_BYTES,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = ChunkedDataSource(upstream.createDataSource(), chunkBytes)
    }

    companion object {
        /** The size measured in the report: 10 MB chunks ran at 71-84 Mbit/s with ~100 ms to first byte. */
        const val DEFAULT_CHUNK_BYTES = 10L * 1024 * 1024
    }
}
