package com.greenaltambe.vela.feed

import com.greenaltambe.vela.core.settings.InMemorySettingsStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubscriptionGroupsTest {
    private val store = InMemorySettingsStore()
    private fun g(account: String = "a") = SubscriptionGroups(store, account)

    @Test fun createRenameDeleteAndTheNamesStayUnique() {
        val s = g()
        val music = assertNotNull(s.create("Music", setOf("UC1")))
        assertNull(s.create("music"), "a name is taken whatever its case")
        assertNull(s.create("   "))
        val learn = assertNotNull(s.create("Learning"))
        assertFalse(s.rename(learn.id, "MUSIC"))
        assertTrue(s.rename(learn.id, "Study"))
        assertEquals(listOf("Music", "Study"), s.groups().map { it.name })
        s.delete(music.id)
        assertEquals(listOf("Study"), s.groups().map { it.name })
    }

    @Test fun everyGroupKeepsItsOwnIdWhateverIsDeleted() {
        val s = g()
        val a = assertNotNull(s.create("A")); val b = assertNotNull(s.create("B"))
        s.delete(a.id)
        val c = assertNotNull(s.create("C"))
        assertEquals(setOf(b.id, c.id).size, 2, "a new group never takes the id of one that is still there")
    }

    @Test fun aChannelCanBeInSeveralGroupsAndToggled() {
        val s = g()
        val a = assertNotNull(s.create("A")); val b = assertNotNull(s.create("B"))
        s.toggle(a.id, "UC1"); s.toggle(b.id, "UC1"); s.toggle(b.id, "UC2")
        assertEquals(listOf("A", "B"), s.groupsOf("UC1").map { it.name })
        s.toggle(a.id, "UC1")
        assertEquals(listOf("B"), s.groupsOf("UC1").map { it.name })
        assertEquals(setOf("UC1", "UC2"), s.groups().first { it.id == b.id }.channelIds)
    }

    @Test fun groupsBelongToTheirAccount() {
        g("a").create("Mine"); 
        assertEquals(emptyList(), g("b").groups())
    }

    @Test fun deleteCanBeUndoneAndEveryEditChangesTheRevision() {
        val s = g("c"); val r0 = SubscriptionGroups.revision.value
        val x = assertNotNull(s.create("X", setOf("UC1")))
        val before = s.groups()
        s.delete(x.id); assertEquals(emptyList(), s.groups())
        s.restore(before); assertEquals(before, s.groups())
        assertEquals(r0 + 3, SubscriptionGroups.revision.value)
    }

    @Test fun namesWithTabsOrBreaksAreKeptReadable() {
        val s = g("d")
        assertEquals("Odd name", assertNotNull(s.create("Odd\tname\n")).name)
        assertEquals("Odd name", s.groups().single().name)
    }
}
