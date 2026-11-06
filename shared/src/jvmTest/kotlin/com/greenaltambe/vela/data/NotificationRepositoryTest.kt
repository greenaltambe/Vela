package com.greenaltambe.vela.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class NotificationRepositoryTest {
    private val db = createDatabase(JvmDatabaseDriverFactory())
    private var bumps = 0
    private val repo = NotificationRepository(db, "a1") { bumps++ }
    private fun t(ms: Long) = Instant.fromEpochMilliseconds(ms)

    @Test fun theSameKindAndKeyIsKeptOnce() {
        assertTrue(repo.add(NotificationKind.UPLOAD, "v1", "First", t(1)))
        assertFalse(repo.add(NotificationKind.UPLOAD, "v1", "First again", t(2)))
        assertTrue(repo.add(NotificationKind.DOWNLOAD_DONE, "v1", "Done", t(3)))
        assertEquals(listOf("Done", "First"), repo.recent().map { it.title })
        assertEquals(2, bumps)
    }

    @Test fun readingAndRemovingChangeTheUnreadCount() {
        repo.add(NotificationKind.UPLOAD, "v1", "A", t(1)); repo.add(NotificationKind.UPLOAD, "v2", "B", t(2))
        assertEquals(2, repo.unreadCount())
        repo.markRead(repo.recent().first().id, t(5)); assertEquals(1, repo.unreadCount())
        repo.markAllRead(t(6)); assertEquals(0, repo.unreadCount())
        repo.delete(repo.recent().first().id); assertEquals(1, repo.recent().size)
        repo.clear(); assertEquals(0, repo.recent().size)
    }

    @Test fun eachAccountHasItsOwnFeed() {
        repo.add(NotificationKind.HEALTH, "h", "Health", t(1))
        assertEquals(0, NotificationRepository(db, "a2").recent().size)
    }

    @Test fun theNotifyLevelDefaultsToPersonalizedAndIsKeptPerChannel() {
        val subs = SubscriptionRepository(db, "a1")
        subs.subscribe("UC1", "One", null, t(1))
        assertEquals(NotifyLevel.PERSONALIZED, subs.notifyLevel("UC1"))
        subs.setNotifyLevel("UC1", NotifyLevel.ALL)
        assertEquals(NotifyLevel.ALL, subs.notifyLevel("UC1"))
        assertEquals(NotifyLevel.PERSONALIZED, subs.notifyLevel("UC-unknown"))
    }
}
