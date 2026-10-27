package com.greenaltambe.vela.core.http

class HttpRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val contentType: String? = null,
)

class HttpResponse(val status: Int, val body: String)

/** The connection failed (no network, DNS, timeout, TLS...): no response came back. */
class TransportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * One request, one response; no policy. Implementations map every I/O failure to [TransportException] and enforce the
 * connect / read timeouts; retries, caching and the rest are [ApiClient]'s job so they are the same on every platform.
 */
interface HttpTransport {
    suspend fun execute(request: HttpRequest): HttpResponse
}
