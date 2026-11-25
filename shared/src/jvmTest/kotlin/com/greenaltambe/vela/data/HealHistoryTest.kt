package com.greenaltambe.vela.data

import kotlin.test.*
import kotlin.time.Instant

class HealHistoryTest {
    private val at = Instant.fromEpochSeconds(1_800_000_000)
    private val db = createDatabase(JvmDatabaseDriverFactory())
    private val history = SqlDelightWatchHistoryRepository(db)
    private fun row(id: String, channel: String, title: String) { history.begin("local", id, channel, title, at, 10_000, 60_000, 5_000) }
    private fun read(id: String) = history.lastForVideo("local", id)!!.let { it.title to it.channelId }

    @Test fun aRowGetsWhatItLacksAndKeepsWhatItHas() {
        row("a", "", "Listed title"); row("b", "", ""); row("c", "UCkept", ""); row("d", "UCkept", "Kept")
        listOf("a", "b", "c", "d").forEach { history.healMissing("local", it, "Found title", "UCfound") }
        assertEquals("Listed title" to "UCfound", read("a"))
        assertEquals("Found title" to "UCfound", read("b"))
        assertEquals("Found title" to "UCkept", read("c"))
        assertEquals("Kept" to "UCkept", read("d"))
    }

    @Test fun anotherAccountsRowIsNotTouched() {
        history.begin("other", "a", "", "T", at, 0, 0, 0)
        history.healMissing("local", "a", "Found", "UCfound")
        assertEquals("", history.lastForVideo("other", "a")!!.channelId)
    }
}
