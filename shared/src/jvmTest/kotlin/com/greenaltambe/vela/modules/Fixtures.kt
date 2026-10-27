package com.greenaltambe.vela.modules

import com.greenaltambe.vela.core.http.HttpRequest
import com.greenaltambe.vela.core.http.HttpResponse

/** Real responses captured by resources/fixtures/modules/capture_fixtures.py. */
object Fixtures {
    const val SPONSORED_VIDEO = "DN953UW8iJ8"
    const val PLAIN_VIDEO = "dQw4w9WgXcQ"

    fun text(name: String): String =
        Fixtures::class.java.getResourceAsStream("/fixtures/modules/$name")?.use { it.readBytes().decodeToString() }
            ?: error("missing fixture $name")

    /** Answers like the three real servers, from the captured files. */
    fun server(request: HttpRequest): HttpResponse = when {
        "/api/skipSegments/efd7" in request.url -> HttpResponse(200, text("sb_skipsegments_$SPONSORED_VIDEO.json"))
        "/api/skipSegments/" in request.url && request.method == "GET" -> HttpResponse(404, "")
        "/votes?videoId=$SPONSORED_VIDEO" in request.url -> HttpResponse(200, text("ryd_votes_$SPONSORED_VIDEO.json"))
        "/votes?videoId=$PLAIN_VIDEO" in request.url -> HttpResponse(200, text("ryd_votes_$PLAIN_VIDEO.json"))
        "/votes?videoId=" in request.url -> HttpResponse(404, "")
        "/api/branding/efd7" in request.url -> HttpResponse(200, text("dearrow_branding_efd7.json"))
        "/api/branding/" in request.url && request.method == "GET" -> HttpResponse(200, "{}")
        request.method == "POST" -> HttpResponse(200, "OK")
        else -> HttpResponse(500, "unexpected ${request.url}")
    }
}
