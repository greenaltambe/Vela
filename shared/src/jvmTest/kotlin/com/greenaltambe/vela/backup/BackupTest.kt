package com.greenaltambe.vela.backup

import com.greenaltambe.vela.core.settings.SettingLayer
import com.greenaltambe.vela.data.*
import com.greenaltambe.vela.extractor.VideoSummary
import kotlin.test.*
import kotlin.time.Instant

class BackupTest {
    private val at = Instant.fromEpochSeconds(1_800_000_000)
    private fun v(id: String) = VideoSummary(id, "T $id", "UC$id", "Chan $id", "https://t/$id.jpg", 61)

    private fun filled(): com.greenaltambe.vela.data.db.VelaDatabase {
        val db = createDatabase(JvmDatabaseDriverFactory())
        SubscriptionRepository(db).apply { subscribe("UC1", "One", null, at); subscribe("UC2", "Two", "https://a/2.png", at) }
        PlaylistRepository(db).apply { val id = create("Road trip", at); add(id, listOf(v("a"), v("b")), at); add(watchLaterId(), listOf(v("w")), at) }
        val h = SqlDelightWatchHistoryRepository(db); val id = h.begin("local", "a", "UCa", "T a", at, 42_000, 100_000, 40_000); h.update(id, at, 42_000, 100_000, 40_000)
        SearchHistoryRepository(db).add("lofi", at)
        SqlDelightSettingsStore(db).apply {
            put("playback.defaultSpeed", SettingLayer.Global, "1.25"); put("playback.defaultSpeed", SettingLayer.channel("UC1"), "2.0")
            put("sponsorblock.userId", SettingLayer.Global, "SECRET-ID"); put("health.results", SettingLayer.Global, "cache"); put("remoteConfig.version", SettingLayer.Global, "9"); put("session.nowPlaying", SettingLayer.Global, "x"); put("governor.google-abc.state", SettingLayer.Global, "OPEN"); put("governor.google-abc.until", SettingLayer.Global, "99")
        }
        return db
    }

    @Test fun roundTripIntoAFreshDatabaseRestoresEverythingPersonalAndNothingSecret() {
        val text = BackupService(filled()).create()
        listOf("SECRET-ID", "cache", "remoteConfig", "cookie", "SAPISID", "session.nowPlaying", "governor.", "OPEN").forEach { assertFalse(it in text, "$it must not be in the backup") }
        val fresh = createDatabase(JvmDatabaseDriverFactory())
        val svc = BackupService(fresh)
        val p = svc.preview(text)
        assertEquals(2, p.newSubscriptions); assertEquals(1, p.newHistory); assertEquals(1, p.newSearches); assertEquals(listOf("Road trip"), p.newPlaylists)
        assertEquals(0, SubscriptionRepository(fresh).all().size, "a preview writes nothing")
        svc.restore(text)
        assertEquals(setOf("UC1", "UC2"), SubscriptionRepository(fresh).all().map { it.channelId }.toSet())
        val lists = PlaylistRepository(fresh)
        assertEquals(listOf("a", "b"), lists.items(lists.all().single { it.name == "Road trip" }.id).map { it.videoId })
        assertEquals(listOf("w"), lists.items(lists.watchLaterId()).map { it.videoId })
        assertEquals(42_000, HistoryLibrary(fresh).recent().single().positionMs)
        assertEquals(listOf("lofi"), SearchHistoryRepository(fresh).recent())
        val s = SqlDelightSettingsStore(fresh)
        assertEquals("1.25", s.get("playback.defaultSpeed", SettingLayer.Global)); assertEquals("2.0", s.get("playback.defaultSpeed", SettingLayer.channel("UC1")), "per-channel overrides come back")
        assertNull(s.get("sponsorblock.userId", SettingLayer.Global))
    }

    @Test fun restoringTwiceOrOverExistingDataMergesAndNeverDeletesOrDuplicates() {
        val text = BackupService(filled()).create()
        val db = createDatabase(JvmDatabaseDriverFactory())
        SubscriptionRepository(db).subscribe("UCmine", "Already mine", null, at); SubscriptionRepository(db).subscribe("UC1", "One", null, at)
        PlaylistRepository(db).apply { val id = create("road trip", at); add(id, listOf(v("zzz"), v("a")), at) }
        val svc = BackupService(db)
        val p = svc.restore(text)
        assertEquals(1, p.newSubscriptions); assertEquals(1, p.alreadySubscribed)
        assertTrue(SubscriptionRepository(db).isSubscribed("UCmine"), "local-only data stays")
        val lists = PlaylistRepository(db)
        assertEquals(1, lists.all().count { it.name.equals("road trip", true) }, "matched by name, not duplicated")
        assertEquals(setOf("zzz", "a", "b"), lists.items(lists.all().single { it.name.equals("road trip", true) }.id).map { it.videoId }.toSet())
        val again = svc.restore(text)
        assertEquals(0, again.newSubscriptions); assertEquals(0, again.newHistory); assertEquals(0, again.newSearches); assertTrue(again.mergedPlaylists.isEmpty()); assertTrue(again.settingsChanged.isEmpty())
        assertEquals(1, HistoryLibrary(db).recent().size)
    }

    @Test fun anOldFormatIsUpgradedAndANewerOrForeignFileIsRefusedWithAMessage() {
        val v1 = """{"format":"vela-backup","schemaVersion":1,"createdAt":1,"settings":[{"key":"playback.defaultSpeed","value":"1.5"}],"searchHistory":["old query"],"subscriptions":[{"channelId":"UCold","name":"Old","subscribedAt":5}]}"""
        val db = createDatabase(JvmDatabaseDriverFactory()); val svc = BackupService(db)
        val p = svc.restore(v1)
        assertEquals(1, p.fromVersion); assertEquals(1, p.newSubscriptions)
        assertEquals("1.5", SqlDelightSettingsStore(db).get("playback.defaultSpeed", SettingLayer.Global)); assertEquals(listOf("old query"), SearchHistoryRepository(db).recent())
        assertTrue("newer Vela" in assertFailsWith<BackupException> { svc.preview("""{"format":"vela-backup","schemaVersion":99,"createdAt":1}""") }.message!!)
        assertTrue(assertFailsWith<BackupException> { svc.preview("""{"hello":1}""") }.message!!.contains("not a Vela backup"))
        assertTrue(assertFailsWith<BackupException> { svc.preview("not json at all") }.message!!.contains("not valid JSON"))
    }

    @Test fun historyAndSearchHistoryAreOptionalAndAccountMirroredPlaylistsAreLeftOut() {
        val db = filled()
        PlaylistRepository(db).mirror("WL", "Watch later (YouTube)", at)
        val text = BackupService(db).create(includeHistory = false, includeSearchHistory = false)
        val file = BackupService(db).parse(text).first
        assertTrue(file.history.isEmpty() && file.searchHistory.isEmpty()); assertTrue(file.playlists.none { it.name.contains("YouTube") })
    }
}
