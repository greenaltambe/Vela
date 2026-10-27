package com.greenaltambe.vela.net

import com.greenaltambe.vela.core.http.HttpRequest
import com.greenaltambe.vela.core.http.HttpResponse
import com.greenaltambe.vela.core.http.HttpTransport
import com.greenaltambe.vela.core.http.TransportException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** [HttpTransport] over OkHttp, with the timeouts the modules need: a slow third-party API must never hold anything up. */
class OkHttpTransport(
    client: OkHttpClient = OkHttpClient(),
    connectTimeoutMs: Long = 5_000,
    readTimeoutMs: Long = 8_000,
    callTimeoutMs: Long = 12_000,
) : HttpTransport {
    private val client = client.newBuilder()
        .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
        .writeTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
        .callTimeout(callTimeoutMs, TimeUnit.MILLISECONDS)
        .build()

    override suspend fun execute(request: HttpRequest): HttpResponse = suspendCancellableCoroutine { cont ->
        val builder = Request.Builder().url(request.url)
        request.headers.forEach { (k, v) -> builder.header(k, v) }
        val body = request.body?.toRequestBody((request.contentType ?: "application/json").toMediaType())
        builder.method(request.method, body ?: if (request.method == "POST" || request.method == "PUT") "".toRequestBody() else null)
        val call = client.newCall(builder.build())
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(TransportException(e.message ?: e::class.simpleName.orEmpty(), e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val text = try { it.body.string() } catch (e: IOException) {
                        if (cont.isActive) cont.resumeWithException(TransportException(e.message ?: "read failed", e))
                        return
                    }
                    if (cont.isActive) cont.resume(HttpResponse(it.code, text))
                }
            }
        })
    }
}
