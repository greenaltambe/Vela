package com.greenaltambe.vela.downloads

import com.greenaltambe.vela.core.http.TransportException
import com.greenaltambe.vela.player.StreamLeases
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The server no longer accepts this URL (expired, or the signature no longer matches): resolve again. */
class StreamDeadException(val status: Int) : Exception("HTTP $status")

/** How long to wait before retry number [attempt] (1-based) of a transient failure. */
fun interface Backoff {
    fun delayFor(attempt: Int): Duration

    companion object {
        /** 1, 2, 4, 8, 16 s, capped at 30 s. */
        val Exponential = Backoff { attempt -> minOf(30.seconds, 1.seconds * (1 shl (attempt - 1).coerceIn(0, 10))) }
    }
}

/** Counts bytes on their way into the file, so progress is live without waiting for a chunk to finish. */
private class CountingSink(private val inner: DownloadSink, private val onBytes: (Long) -> Unit) : DownloadSink {
    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        inner.write(buffer, offset, length)
        onBytes(length.toLong())
    }

    override fun close() = inner.close()
}

/**
 * Downloads one stream into [storage] in bounded ranges (default 10 MB). googlevideo throttles open-ended requests to
 * about 5 Mbit/s but serves bounded ranges at line rate (measured in task 07: 12-17x faster), so a download is a series
 * of `Range: bytes=a-b` requests, each appended to the same partial file.
 *
 * **The file is the progress.** Where to continue is always the partial file's size, never a counter kept elsewhere,
 * so a crash, a pause or a failure in the middle of a chunk loses at most that chunk's already-written bytes' worth
 * of nothing: the next range simply starts at the size on disk. A finished file is renamed to its final name, which is
 * how a resume knows a stream is done.
 *
 * Failures: [StreamDeadException] for a dead URL (the caller resolves again and calls this again); a transient failure
 * (connection error, 5xx, 429) is retried up to [maxRetries] times with [backoff], the count resetting after any chunk
 * that succeeds; anything else is a [DownloadFailure].
 */
class ChunkedFetch(
    private val fetcher: RangeFetcher,
    private val storage: DownloadStorage,
    private val chunkBytes: Long = 10L * 1024 * 1024,
    private val maxRetries: Int = 5,
    private val backoff: Backoff = Backoff.Exponential,
    private val sleep: suspend (Duration) -> Unit = { delay(it) },
) {
    /**
     * Returns the stream's total size. [onBytes] gets every byte as it is written; [onChunk] gets (bytes in the file,
     * total or null) after each chunk.
     */
    suspend fun download(
        stream: ResolvedStream,
        knownTotal: Long?,
        onBytes: (Long) -> Unit,
        onChunk: (Long, Long?) -> Unit,
    ): Long {
        val s = stream.stored
        storage.size(s.fileName)?.let { done ->
            // Already renamed: finished in an earlier run.
            onBytes(0)
            return knownTotal ?: done
        }
        var total = knownTotal
        var failures = 0
        var restarted = false
        while (true) {
            val done = storage.size(s.partName) ?: 0L
            if (total != null && done >= total) break
            val end = if (total != null) minOf(done + chunkBytes, total) - 1 else done + chunkBytes - 1
            val sink = CountingSink(storage.append(s.partName), onBytes)
            val response = try {
                try {
                    fetcher.fetch(stream.url, stream.headers, done, end, sink)
                } finally {
                    sink.close()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: TransportException) {
                failures = retryOrFail(failures, e.message ?: "connection failed", e); continue
            }

            when {
                StreamLeases.isDeadStatus(response.status) -> throw StreamDeadException(response.status)
                response.status == 416 -> {
                    // The range starts at or past the end: the file is already whole.
                    if (total == null) total = done
                    break
                }
                response.status == 206 || (response.status == 200 && done == 0L) -> {
                    failures = 0
                    response.totalLength?.let { total = it }
                    // A 200 to a range request is the whole body: the server ignored Range; fine from offset 0.
                    if (response.status == 200 && total == null) total = response.bytes
                    if (response.bytes == 0L) throw DownloadFailure("The server answered with an empty range at byte $done")
                }
                response.status == 200 -> {
                    // Asked to continue at `done` but got the beginning again: Range is not honoured. Start over once.
                    if (restarted) throw DownloadFailure("The server ignores Range requests, so the download cannot resume")
                    restarted = true
                    storage.delete(s.partName)
                    continue
                }
                response.status == 429 || response.status >= 500 -> { failures = retryOrFail(failures, "HTTP ${response.status}", null); continue }
                else -> throw DownloadFailure("Unexpected HTTP ${response.status} while downloading")
            }
            onChunk(storage.size(s.partName) ?: 0L, total)
        }

        val size = storage.size(s.partName) ?: 0L
        val expected = total
        if (expected != null && size != expected) {
            storage.delete(s.partName)
            throw DownloadFailure("Size mismatch for ${s.fileName}: got $size bytes, expected $expected")
        }
        storage.rename(s.partName, s.fileName)
        return size
    }

    private suspend fun retryOrFail(failures: Int, reason: String, cause: Throwable?): Int {
        val next = failures + 1
        if (next > maxRetries) throw DownloadFailure("Giving up after $maxRetries retries: $reason", cause)
        sleep(backoff.delayFor(next))
        return next
    }
}
