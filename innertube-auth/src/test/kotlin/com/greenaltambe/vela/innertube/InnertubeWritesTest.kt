package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.auth.*
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant

class InnertubeWritesTest {
    private class FixedClock(val at: Instant = Instant.fromEpochSeconds(1_700_000_000)) : Clock { override fun now() = at }
    private val audits = mutableListOf<AuditEntry>()
    private val bodies = mutableListOf<Pair<String, String>>()
    private var answer: (String) -> String = { """{"responseContext":{}}""" }

    private fun client(limits: GovernorLimits = GovernorLimits(minSpacingMs = 0, writesPerHour = 3)): InnertubeClient {
        val transport = FakeTransport { _, url, _, body -> bodies += url.substringAfter("/v1/").substringBefore('?') to body.orEmpty(); RawResponse(200, answer(url), emptyMap(), "") }
        val jar = CookieJar(listOf(Cookie("SID", "sid-v", ".google.com"), Cookie("SAPISID", "AbCdEfGhIjKlMnOp/1234567890", ".youtube.com")))
        return InnertubeClient(AuthSession(jar, AccountGovernor("a1", FixedClock(), { limits }, audit = { audits += it }), transport, FixedClock()), WebClient("WEB", "2.0", "en", "US", "UA"))
    }

    @Test fun subscribeUnsubscribeRateAndPlaylistCallsGoToTheRightEndpointsWithTheRightBodies() {
        answer = { url -> if (url.contains("playlist/create")) """{"playlistId":"PLnew1"}""" else if (url.contains("edit_playlist")) """{"status":"STATUS_SUCCEEDED"}""" else """{}""" }
        val c = client(GovernorLimits(minSpacingMs = 0, writesPerHour = 100))
        c.subscribe("UC1"); c.unsubscribe("UC1"); c.rate("v1", Rating.LIKE); c.rate("v1", Rating.DISLIKE); c.rate("v1", Rating.NONE)
        c.addToPlaylist("WL", "v1"); c.removeFromPlaylist("WL", "v1"); c.renamePlaylist("PL1", "New name")
        assertEquals("PLnew1", c.createPlaylist("Mine")); c.deletePlaylist("PLnew1")
        assertEquals(listOf("subscription/subscribe", "subscription/unsubscribe", "like/like", "like/dislike", "like/removelike", "browse/edit_playlist", "browse/edit_playlist", "browse/edit_playlist", "playlist/create", "playlist/delete"), bodies.map { it.first })
        assertTrue("\"channelIds\":[\"UC1\"]" in bodies[0].second)
        assertTrue("\"target\":{\"videoId\":\"v1\"}" in bodies[2].second)
        assertTrue("ACTION_ADD_VIDEO" in bodies[5].second && "\"playlistId\":\"WL\"" in bodies[5].second && "addedVideoId" in bodies[5].second)
        assertTrue("ACTION_REMOVE_VIDEO_BY_VIDEO_ID" in bodies[6].second && "ACTION_SET_PLAYLIST_NAME" in bodies[7].second)
        assertTrue("\"privacyStatus\":\"PRIVATE\"" in bodies[8].second, "new playlists are private")
        assertEquals(10, audits.size); assertTrue(audits.all { it.kind == CallKind.WRITE.name || it.kind.contains("WRITE", true) }, audits.map { it.kind }.toString())
    }

    @Test fun theWriteBudgetOfTheGovernorStopsTheFourthWriteWithoutSendingIt() {
        val c = client(GovernorLimits(minSpacingMs = 0, writesPerHour = 3))
        repeat(3) { c.subscribe("UC$it") }
        assertFailsWith<GovernorDenied> { c.subscribe("UC9") }
        assertEquals(3, bodies.size, "the denied write never reached the transport")
    }

    @Test fun aRefusalOrAFailedEditIsAnErrorNotASilentSuccess() {
        answer = { """{"error":{"code":400,"message":"Precondition check failed."}}""" }
        val e = assertFailsWith<AuthFailure.Unparseable> { client().subscribe("UC1") }
        assertTrue("Precondition check failed" in e.message!!)
        answer = { """{"status":"STATUS_FAILED"}""" }
        assertFailsWith<AuthFailure.Unparseable> { client().addToPlaylist("WL", "v") }
        answer = { """{}""" }
        assertFailsWith<AuthFailure.Unparseable> { client().createPlaylist("x") }
    }

    @Test fun playlistAndVideoParsersReadBothRendererStylesAndSurviveJunk() {
        val page = lenientJson.parseToJsonElement("""{"a":[{"gridPlaylistRenderer":{"playlistId":"PL1","title":{"runs":[{"text":"One"}]},"videoCountText":{"runs":[{"text":"12"},{"text":" videos"}]}}},
            {"lockupViewModel":{"contentType":"LOCKUP_CONTENT_TYPE_PLAYLIST","contentId":"PL2","metadata":{"lockupMetadataViewModel":{"title":{"content":"Two"}}}}},
            {"lockupViewModel":{"contentType":"LOCKUP_CONTENT_TYPE_VIDEO","contentId":"vid"}},{"gridPlaylistRenderer":{"title":{"simpleText":"no id"}}}]}""")
        assertEquals(listOf("PL1" to 12, "PL2" to null), InnertubeParsers.playlists(page).map { it.id to it.videoCount })
        val vids = lenientJson.parseToJsonElement("""{"x":[{"playlistVideoRenderer":{"videoId":"v1","title":{"runs":[{"text":"T"}]},"shortBylineText":{"runs":[{"text":"Chan"}]},"lengthSeconds":"61","setVideoId":"S1"}},{"playlistVideoRenderer":{"title":{"simpleText":"no id"}}}]}""")
        val v = InnertubeParsers.playlistVideos(vids).single()
        assertEquals(listOf("v1", "T", "Chan", 61L, "S1"), listOf(v.videoId, v.title, v.channelName, v.durationSeconds, v.setVideoId))
    }

    @Test fun ratingAndSubscribedStateAreReadFromTheRightFields() {
        assertEquals(Rating.LIKE, InnertubeParsers.rating(lenientJson.parseToJsonElement("""{"a":{"likeStatus":"LIKE"}}""")))
        assertEquals(Rating.NONE, InnertubeParsers.rating(lenientJson.parseToJsonElement("""{"a":[{"likeStatus":"INDIFFERENT"}]}""")))
        assertNull(InnertubeParsers.rating(lenientJson.parseToJsonElement("""{"a":1}""")))
        val page = lenientJson.parseToJsonElement("""{"subscribeButtonContent":{"subscribeState":{"subscribed":false}},"unsubscribeButtonContent":{"subscribeState":{"subscribed":true}},"payload":{"subscriptionStateEntity":{"key":"k","subscribed":true}}}""")
        assertEquals(true, InnertubeParsers.subscribed(page), "the entity, not the button templates")
        assertNull(InnertubeParsers.subscribed(lenientJson.parseToJsonElement("""{"subscribeState":{"subscribed":false}}""")))
    }

    @Test fun feedVideosAreParsedFromRendererAndLockupShapesHandBuiltFromTheDocumentedFormat() {
        // NOT a recording: the throwaway account's real Home feed was empty. This only checks the parser against the documented shapes.
        val page = lenientJson.parseToJsonElement("""{"contents":[{"richItemRenderer":{"content":{"videoRenderer":{"videoId":"v1","title":{"runs":[{"text":"First"}]},
            "ownerText":{"runs":[{"text":"Chan","navigationEndpoint":{"browseEndpoint":{"browseId":"UC123"}}}]},"lengthText":{"simpleText":"1:02:03"},"viewCountText":{"simpleText":"1,234 views"},
            "thumbnail":{"thumbnails":[{"url":"https://i/a.jpg"},{"url":"https://i/b.jpg"}]},"badges":[]}}}},
            {"lockupViewModel":{"contentType":"LOCKUP_CONTENT_TYPE_VIDEO","contentId":"v2","metadata":{"lockupMetadataViewModel":{"title":{"content":"Second"},"metadata":{"contentMetadataViewModel":{"metadataRows":[{"metadataParts":[{"text":{"content":"Chan2"}}]}]}}}}}},
            {"videoRenderer":{"title":{"simpleText":"no id"}}},{"videoRenderer":{"videoId":"v1","title":{"simpleText":"duplicate of v1"}}}]}""")
        val v = InnertubeParsers.videos(page)
        assertEquals(listOf("v1", "v2"), v.map { it.id })
        assertEquals(listOf("First", "UC123", "Chan", 3723L, 1234L, "https://i/b.jpg"), listOf(v[0].title, v[0].channelId, v[0].channelName, v[0].durationSeconds, v[0].viewCount, v[0].thumbnailUrl))
        assertEquals("Chan2", v[1].channelName)
    }

    @Test fun subscriptionsComeFromTheAllSubscriptionsPageAndStopWhenPagesRepeat() {
        val page = """{"x":[{"channelRenderer":{"channelId":"UC1","title":{"simpleText":"Only one"}}}],"c":{"continuationCommand":{"token":"T"}}}"""
        val urls = mutableListOf<String>()
        val transport = FakeTransport { _, url, _, body -> urls += body.orEmpty().let { if ("browseId" in it) it.substringAfter("browseId\":\"").substringBefore('"') else "continuation" }; RawResponse(200, page, emptyMap(), "") }
        val jar = CookieJar(listOf(Cookie("SID", "sid-v", ".google.com"), Cookie("SAPISID", "AbCdEfGhIjKlMnOp/1234567890", ".youtube.com")))
        val client = InnertubeClient(AuthSession(jar, AccountGovernor("a1", FixedClock(), { GovernorLimits(minSpacingMs = 0, browsePerMinute = 1000) }), transport, FixedClock()), WebClient("WEB", "2.0", "en", "US", "UA"))
        val subs = client.subscriptions()
        assertEquals(listOf("UC1"), subs.map { it.channelId })
        assertEquals("FEchannels", urls.first(), "never FEsubscriptions, whose suggestions look like channels")
        assertTrue(urls.size <= 3, "stopped after two pages with nothing new, not 40 calls: $urls")
    }
}

class LiveChatParserTest {
    // Hand-built from the shapes seen on a real stream (names and texts are made up); not a recording.
    private val live = """{"continuationContents":{"liveChatContinuation":{"continuations":[{"timedContinuationData":{"timeoutMs":10000,"continuation":"NEXT1"}}],"actions":[
        {"addChatItemAction":{"item":{"liveChatTextMessageRenderer":{"id":"m1","authorName":{"simpleText":"Alice"},"message":{"runs":[{"text":"hello "},{"emoji":{"shortcuts":[":wave:"]}}]},"timestampUsec":"1700000000000000"}}}},
        {"addChatItemAction":{"item":{"liveChatPaidMessageRenderer":{"id":"p1","authorName":{"simpleText":"Bob"},"purchaseAmountText":{"simpleText":"$5.00"},"message":{"runs":[{"text":"thanks!"}]}}}}},
        {"addChatItemAction":{"item":{"liveChatMembershipItemRenderer":{"id":"b1","authorName":{"simpleText":"Cy"},"headerSubtext":{"runs":[{"text":"Welcome"}]}}}}},
        {"addChatItemAction":{"item":{"somethingNew":{}}}},{"addChatItemAction":{"item":{"liveChatTextMessageRenderer":{"authorName":{"simpleText":"no id"}}}}}]}}}"""

    @Test fun readsTextPaidAndMembershipMessagesTheNextTokenAndTheDelayAndIgnoresWhatItDoesNotKnow() {
        val p = LiveChatParser.parse(lenientJson.parseToJsonElement(live))
        assertEquals(listOf("m1", "p1", "b1"), p.messages.map { it.id })
        assertEquals("hello :wave:", p.messages[0].text); assertEquals(1_700_000_000_000, p.messages[0].timestampMs)
        assertEquals("$5.00", p.messages[1].amount); assertEquals(com.greenaltambe.vela.extractor.ChatKind.SUPERCHAT, p.messages[1].kind)
        assertEquals("Welcome", p.messages[2].text)
        assertEquals("NEXT1", p.nextToken); assertEquals(10_000, p.timeoutMs)
    }

    @Test fun replayActionsAreUnwrappedAndAMissingContinuationEndsTheChat() {
        val replay = """{"continuationContents":{"liveChatContinuation":{"actions":[{"replayChatItemAction":{"actions":[{"addChatItemAction":{"item":{"liveChatTextMessageRenderer":{"id":"r1","authorName":{"simpleText":"Dee"},"message":{"runs":[{"text":"replayed"}]}}}}}],"videoOffsetTimeMsec":"1000"}}]}}}"""
        val p = LiveChatParser.parse(lenientJson.parseToJsonElement(replay))
        assertEquals(listOf("replayed"), p.messages.map { it.text }); assertNull(p.nextToken)
    }

    @Test fun theClientDedupesAcrossPollsClampsTheDelayAndFindsNoChatWhenThereIsNoRenderer() = kotlinx.coroutines.runBlocking<Unit> {
        var polls = 0
        val t = FakeTransport { _, url, _, _ -> RawResponse(200, when {
            "/next" in url -> """{"contents":{"liveChatRenderer":{"isReplay":false,"continuations":[{"reloadContinuationData":{"continuation":"START"}}]}}}"""
            else -> if (polls++ == 0) live else live.replace("NEXT1", "NEXT2").replace("\"timeoutMs\":10000", "\"timeoutMs\":100")
        }, emptyMap(), "") }
        val chat = LiveChatClient(t, WebClient("WEB", "2.0", "en", "US", "UA")).open("vid")!!
        assertEquals(3, chat.poll().messages.size)
        val second = chat.poll(); assertEquals(0, second.messages.size, "already seen"); assertEquals(3_000, second.nextPollMs, "never faster than 3 s")
        assertNull(LiveChatClient(FakeTransport { _, _, _, _ -> RawResponse(200, """{"contents":{}}""", emptyMap(), "") }, WebClient("WEB", "2.0", "en", "US", "UA")).open("none"))
    }
}

class ProfilesTest {
    @Test fun profilesAreReadAndAPageIdIsSentOnlyWhenOneIsChosen() {
        val json = lenientJson.parseToJsonElement("""{"actions":[{"x":[{"accountItem":{"accountName":{"simpleText":"Main"},"isSelected":true,"serviceEndpoint":{"selectActiveIdentityEndpoint":{"supportedTokens":[{"offlineCacheKeyToken":{"clientCacheKey":"k"},"datasyncIdToken":{"datasyncIdToken":"u1||"}}]}},"datasyncId":"u1||"}},
            {"accountItem":{"accountName":{"simpleText":"Brand"},"isSelected":false,"datasyncId":"page9||u1"}}]}]}""")
        val p = InnertubeParsers.profiles(json)
        assertEquals(listOf("Main" to null, "Brand" to "page9"), p.map { it.name to it.pageId }); assertTrue(p[0].selected && !p[1].selected)
        fun headers(page: String?) = AuthSession(CookieJar(listOf(Cookie("SID", "s", ".google.com"), Cookie("SAPISID", "p", ".youtube.com"))), AccountGovernor("a"), FakeTransport { _, _, _, _ -> RawResponse(200, "{}", emptyMap(), "") }, pageId = { page }).headersFor("https://www.youtube.com/youtubei/v1/browse")
        assertNull(headers(null)["X-Goog-PageId"]); assertEquals("page9", headers("page9")["X-Goog-PageId"])
    }
}
