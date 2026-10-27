package com.greenaltambe.vela.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class WatchHistoryRepositoryTest {
    private val db = createDatabase(JvmDatabaseDriverFactory())
    private val repo = SqlDelightWatchHistoryRepository(db)
    private fun at(s: Long) = Instant.fromEpochSeconds(s)

    @Test fun beginReturnsTheRowIdAndLastForVideoReadsItBack() {
        val id = repo.begin("local", "v", "UC", "Title", at(100), 5_000, 200_000, 4_000)
        val row = repo.lastForVideo("local", "v")!!
        assertEquals(id, row.id)
        assertEquals("Title", row.title)
        assertEquals("UC", row.channelId)
        assertEquals(at(100), row.watchedAt)
        assertEquals(listOf(5_000L, 200_000L, 4_000L), listOf(row.positionMs, row.durationMs, row.watchedMs))
    }

    @Test fun updateChangesTheSameRow() {
        val id = repo.begin("local", "v", "UC", "T", at(100), 0, 100, 0)
        repo.update(id, at(200), 42_000, 100_000, 40_000)
        val row = repo.lastForVideo("local", "v")!!
        assertEquals(id, row.id)
        assertEquals(at(200), row.watchedAt)
        assertEquals(42_000, row.positionMs)
        assertEquals(40_000, row.watchedMs)
        assertEquals(1, db.watchHistoryQueries.selectRecent("local", 10).executeAsList().size)
    }

    @Test fun lastForVideoIsTheMostRecentlyWatchedAndPerAccount() {
        repo.begin("local", "v", "UC", "T", at(100), 1_000, 100, 0)
        repo.begin("local", "v", "UC", "T", at(300), 3_000, 100, 0)
        repo.begin("local", "v", "UC", "T", at(200), 2_000, 100, 0)
        repo.begin("other", "v", "UC", "T", at(900), 9_000, 100, 0)
        assertEquals(3_000, repo.lastForVideo("local", "v")!!.positionMs)
        assertEquals(9_000, repo.lastForVideo("other", "v")!!.positionMs)
        assertNull(repo.lastForVideo("local", "nope"))
    }
}
