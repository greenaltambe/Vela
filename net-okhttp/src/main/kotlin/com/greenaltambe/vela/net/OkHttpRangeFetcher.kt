package com.greenaltambe.vela.net

import com.greenaltambe.vela.core.http.TransportException
import com.greenaltambe.vela.downloads.DownloadSink
import com.greenaltambe.vela.downloads.RangeFetcher
import com.greenaltambe.vela.downloads.RangeResponse
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Streams `Range` requests straight into a [DownloadSink] (64 KB at a time, nothing held in memory). The body is
 * read on OkHttp's own thread; cancelling the coroutine cancels the call, which aborts the read. Every HTTP status is a
 * normal return, only connection-level failures throw [TransportException].
 */
class OkHttpRangeFetcher(
    client: OkHttpClient = OkHttpClient(),
    private val bufferBytes: Int = 64 * 1024,
) : RangeFetcher {
    private val client = client.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS) // a chunk can take a while; the read timeout bounds stalls
        .build()

    override suspend fun fetch(url: String, headers: Map<String, String>, start: Long, endInclusive: Long?, sink: DownloadSink): RangeResponse =
        suspendCancellableCoroutine { cont ->
            val builder = Request.Builder().url(url)
            headers.forEach { (k, v) -> builder.header(k, v) }
            // No Range header at all for a plain whole-body fetch (thumbnails): some CDNs answer 206 for "bytes=0-".
            if (start > 0 || endInclusive != null) builder.header("Range", "bytes=$start-${endInclusive ?: ""}")
            val call = client.newCall(builder.build())
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(TransportException(e.message ?: e::class.simpleName.orEmpty(), e))
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        var written = 0L
                        try {
                            if (it.code in 200..299) {
                                val source = it.body.source()
                                val buffer = ByteArray(bufferBytes)
                                while (true) {
                                    val n = source.read(buffer)
                                    if (n < 0) break
                                    sink.write(buffer, 0, n)
                                    written += n
                                }
                            }
                        } catch (e: IOException) {
                            // The bytes written so far stay in the sink; the caller resumes from the file's size.
                            if (cont.isActive) cont.resumeWithException(TransportException(e.message ?: "read failed after $written bytes", e))
                            return
                        }
                        if (cont.isActive) cont.resume(RangeResponse(it.code, written, totalLength(it)))
                    }
                }
            })
        }

    /** `Content-Range: bytes 0-99/1234` or `bytes *&#47;1234` -> 1234; a plain 200 -> its Content-Length. */
    internal fun totalLength(response: Response): Long? {
        response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()?.let { return it }
        return if (response.code == 200) response.header("Content-Length")?.toLongOrNull() else null
    }
}
