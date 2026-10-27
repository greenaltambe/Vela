package com.greenaltambe.vela.extractor.newpipe

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Thrown for HTTP 429 (always) and 403 (YouTube page/API hosts only) so the error mapper can tell rate limiting and
 * IP blocks apart from content problems NewPipe reports itself.
 */
class HttpStatusException(val code: Int, val url: String) : IOException("HTTP $code for $url")

/**
 * NewPipe's network layer on OkHttp. OkHttp adds `Accept-Encoding: gzip` and decodes the body itself,
 * so any Accept-Encoding NewPipe sets is dropped (an explicit one would turn that off).
 */
class OkHttpDownloader(
    private val client: OkHttpClient = defaultClient(),
    private val userAgent: String = DEFAULT_USER_AGENT,
    /** Hosts where a 403 means "we are blocked" rather than "this one resource is forbidden". */
    private val isBlockableHost: (String) -> Boolean = ::isYoutubeApiHost,
) : Downloader() {
    override fun execute(request: Request): Response {
        val body = request.dataToSend()?.toRequestBody()
        val builder = okhttp3.Request.Builder().url(request.url())
            // OkHttp insists a POST has a body even when NewPipe sends none.
            .method(request.httpMethod(), body ?: if (request.httpMethod() == "POST") ByteArray(0).toRequestBody() else null)
        var hasUserAgent = false
        request.headers().forEach { (name, values) ->
            if (name.equals("Accept-Encoding", ignoreCase = true)) return@forEach
            if (name.equals("User-Agent", ignoreCase = true)) hasUserAgent = true
            // Replace rather than add, so a repeated header from NewPipe doesn't pile up.
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }
        if (!hasUserAgent) builder.header("User-Agent", userAgent)

        client.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) throw HttpStatusException(429, request.url())
            // Stream URLs (googlevideo.com) also answer 403 for a single expired URL, and NewPipe inspects those itself.
            if (response.code == 403 && isBlockableHost(response.request.url.host)) throw HttpStatusException(403, request.url())
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body.string(),
                response.request.url.toString(),
            )
        }
    }

    companion object {
        /** youtube.com and its subdomains plus the InnerTube API host; deliberately not googlevideo.com. */
        fun isYoutubeApiHost(host: String): Boolean {
            val h = host.lowercase()
            return h == "youtube.com" || h.endsWith(".youtube.com") || h == "youtubei.googleapis.com"
        }

        /** Firefox ESR on Windows: what NewPipe itself presents for web requests. */
        const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; rv:140.0) Gecko/20100101 Firefox/140.0"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
