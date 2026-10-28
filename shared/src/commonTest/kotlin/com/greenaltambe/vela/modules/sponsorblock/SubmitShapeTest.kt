package com.greenaltambe.vela.modules.sponsorblock

import com.greenaltambe.vela.core.http.FakeTransport
import com.greenaltambe.vela.core.http.HttpResponse
import com.greenaltambe.vela.core.http.TestClient
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

/**
 * The request shape of a segment submission against the official API docs (wiki.sponsor.ajay.app, "POST /api/skipSegments", JSON body, read
 * 2026-10-28): videoID, userID (a random private id of 30+ chars), userAgent "Name/Version", service, optional videoDuration (seconds) and
 * segments[{segment:[start,end] seconds, category, actionType}]. A FAKE transport only: nothing here ever reaches the real server (the docs also say
 * automated submissions are not allowed, so submit is only called after the viewer confirms).
 */
class SubmitShapeTest {
    private val t = TestClient(FakeTransport { HttpResponse(200, """[{"UUID":"x","category":"sponsor","segment":[10.5,20.25]}]""") })
    private val api = SponsorBlockApi(t.client, server = { "https://sb.example.invalid/" })

    @Test fun theBodyMatchesTheDocumentedShape() = runTest {
        api.submit("dQw4w9WgXcQ", 10_500, 20_250, SegmentCategory.SPONSOR, "a".repeat(36), 212_000)
        val r = t.transport.requests.single()
        assertEquals("POST", r.method); assertEquals("https://sb.example.invalid/api/skipSegments", r.url, "the server's trailing slash does not double")
        assertEquals("application/json", r.contentType)
        val o = Json.parseToJsonElement(r.body!!).jsonObject
        assertEquals("dQw4w9WgXcQ", o["videoID"]!!.jsonPrimitive.content); assertEquals("a".repeat(36), o["userID"]!!.jsonPrimitive.content)
        assertTrue(o["userAgent"]!!.jsonPrimitive.content.matches(Regex("""[^/\s]+/[\d.]+.*""")), "userAgent is Name/Version: ${o["userAgent"]}")
        assertEquals("YouTube", o["service"]!!.jsonPrimitive.content); assertEquals(212.0, o["videoDuration"]!!.jsonPrimitive.double)
        val seg = o["segments"]!!.jsonArray.single().jsonObject
        assertEquals(listOf(10.5, 20.25), seg["segment"]!!.jsonArray.map { it.jsonPrimitive.double })
        assertEquals("sponsor", seg["category"]!!.jsonPrimitive.content); assertEquals("skip", seg["actionType"]!!.jsonPrimitive.content)
    }

    @Test fun aHighlightIsAPointInTimeWithTheDocumentedActionType() = runTest {
        api.submit("v", 5_000, 9_000, SegmentCategory.POI_HIGHLIGHT, "b".repeat(32), 100_000)
        val seg = Json.parseToJsonElement(t.transport.requests.single().body!!).jsonObject["segments"]!!.jsonArray.single().jsonObject
        assertEquals("poi", seg["actionType"]!!.jsonPrimitive.content)
    }

    @Test fun everyDocumentedErrorIsAFailureTheViewerSeesAndNothingIsRetried() = runTest {
        for (code in listOf(400, 403, 409, 429)) {
            val x = TestClient(FakeTransport { HttpResponse(code, "reason from the server") })
            val r = SponsorBlockApi(x.client, server = { "https://sb.example.invalid" }).submit("v", 1000, 2000, SegmentCategory.SPONSOR, "c".repeat(32), 10_000)
            assertIs<com.greenaltambe.vela.core.http.ApiResult.Failed>(r, "HTTP $code"); assertEquals(1, x.transport.requests.size, "a submission is never retried (HTTP $code)")
        }
    }
}
