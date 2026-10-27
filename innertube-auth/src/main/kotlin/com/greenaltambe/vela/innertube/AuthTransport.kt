package com.greenaltambe.vela.innertube

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** A response with its headers kept (the shared HttpTransport drops them, and Set-Cookie matters here). */
class RawResponse(val code: Int, val body: String, val headers: Map<String, List<String>>, val finalUrl: String) {
    fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, true) }?.value?.firstOrNull()
    fun headerValues(name: String): List<String> = headers.entries.filter { it.key.equals(name, true) }.flatMap { it.value }
    override fun toString() = "RawResponse($code, ${body.length} chars)"
}

/** Where authenticated requests go; a seam so tests can answer without a network. */
interface AuthTransport {
    fun execute(method: String, url: String, headers: Map<String, String>, body: String?): RawResponse
}

class OkHttpAuthTransport(private val client: OkHttpClient = defaultClient) : AuthTransport {
    override fun execute(method: String, url: String, headers: Map<String, String>, body: String?): RawResponse {
        val b = Request.Builder().url(url)
        headers.forEach { (k, v) -> b.header(k, v) }
        val contentType = (headers.entries.firstOrNull { it.key.equals("Content-Type", true) }?.value ?: "application/json").toMediaType()
        b.method(method, body?.toRequestBody(contentType) ?: if (method == "POST") "".toRequestBody(contentType) else null)
        client.newCall(b.build()).execute().use { r ->
            return RawResponse(r.code, r.body.string(), r.headers.toMultimap(), r.request.url.toString())
        }
    }

    companion object {
        /** No cookie jar and no redirects followed for POSTs to accounts.google.com: cookies are handled by our own [com.greenaltambe.vela.auth.CookieJar]. */
        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS)
            .build()
    }
}
