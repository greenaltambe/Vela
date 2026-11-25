package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.auth.*
import com.greenaltambe.vela.data.JvmDatabaseDriverFactory
import com.greenaltambe.vela.data.createDatabase
import com.greenaltambe.vela.home.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What Home does with the real feed of the test account (read-only): the app's own reads, pages gathered into a [HomeList], groups filtered and
 * placed. No video may be both in the feed and in a group, and every group must have a place in the list. Prints counts only.
 */
class LiveHomeListTest {
    @Test fun allThreePagesAndTheChips() = runBlocking<Unit> {
        val db = createDatabase(JvmDatabaseDriverFactory())
        val accounts = AccountManager(db, CredentialStore(InMemorySecretStore()))
        accounts.addGoogle(GoogleIdentity("live", "live@example.com", null, "UClive"), LiveCookies.jarOrSkip())
        val notes = mutableListOf<String>()
        val reads = InnertubeAccountReads(accounts, GovernorRegistry(kotlin.time.Clock.System, { GovernorLimits(browsePerMinute = 12) }), onNote = { notes += it }, onFailure = { notes += "failed: $it" }, region = { "IN" })

        suspend fun patient(read: suspend () -> HomePage?): HomePage = read() ?: run { delay(61_000); checkNotNull(read()) { "no answer: $notes" } }

        fun check(name: String, list: HomeList) {
            val feed = list.videos.items.map { it.id }
            val inGroups = list.groups.flatMap { g -> g.videos.map { it.id } }
            assertEquals(inGroups.size, inGroups.distinct().size, "$name: a video in two groups")
            // Only a later page may repeat, in the feed, a video an earlier group already showed.
            val passed = list.groups.flatMap { it.videos }.associateBy { it.id }
            val shown = visibleGroups(list.groups, passed, suggestedOn = true, shortsOn = true, filtering = false)
            val blocks = placeHomeBlocks(planSlots(com.greenaltambe.vela.core.settings.HomeShelf.entries, 5), shown, feed, feed)
            val groupPlaces = blocks.filterIsInstance<HomeBlock.Group>().map { it.afterVideos }
            assertTrue(groupPlaces.all { it in 0..feed.size }, "$name: a group outside the list")
            assertTrue(blocks.filterIsInstance<HomeBlock.Shelf>().filter { it.shelf != com.greenaltambe.vela.core.settings.HomeShelf.CONTINUE }.none { it.afterVideos in groupPlaces }, "$name: a shelf touches a group")
            assertTrue(list.groups.all { it.afterVideoId == null || it.afterVideoId in feed }, "$name: a group tied to a video the feed does not have")
            val withoutGroups = visibleGroups(list.groups, passed, suggestedOn = false, shortsOn = true, filtering = false).map { it.group.kind }
            println("$name: feed ${feed.size}, groups ${shown.map { "${it.group.kind}:${it.group.videos.size}" }} at $groupPlaces, with the switch off ${withoutGroups.size} left, token ${list.videos.continuation != null}")
        }

        val first = patient { reads.home() }
        var list = HomeList().append(first)
        repeat(2) { list.videos.continuation?.let { token -> list = list.append(patient { reads.home(continuation = token) }) } }
        check("All, 3 pages", list)
        first.chips.filter { it.token != null && it.kind != HomeChipKind.CREATE_CUSTOM_FEED }.take(6).forEachIndexed { i, chip ->
            check("chip $i", HomeList().append(patient { reads.home(chip = chip.token) }))
        }
        println("notes: ${notes.ifEmpty { listOf("none") }}")
    }
}
