package com.greenaltambe.vela.feed

import com.greenaltambe.vela.core.settings.SettingLayer
import com.greenaltambe.vela.core.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A named set of subscribed channels ("Music", "Learning"). A channel can be in several groups. */
data class SubscriptionGroup(val id: String, val name: String, val channelIds: Set<String>)

/**
 * The viewer's subscription groups for one account, kept as one plain settings row (one group per line: `id<TAB>name<TAB>channelId,channelId`) so Backup and
 * restore carries them. [revision] is shared by every instance: it changes with each edit, so screens refilter.
 */
class SubscriptionGroups(private val store: SettingsStore, accountId: String) {
    private val key = "subscriptions.groups.$accountId"

    fun groups(): List<SubscriptionGroup> = (store.get(key, SettingLayer.Global) ?: "").lines().mapNotNull { line ->
        val p = line.trimEnd('\r').split('\t')
        if (p.size < 2 || p[0].isBlank() || p[1].isBlank()) null
        else SubscriptionGroup(p[0], p[1], p.getOrElse(2) { "" }.split(',').filter { it.isNotBlank() }.toSet())
    }

    fun groupsOf(channelId: String): List<SubscriptionGroup> = groups().filter { channelId in it.channelIds }

    /** Null for a blank name or one that is taken (ignoring case). */
    fun create(name: String, channelIds: Set<String> = emptySet()): SubscriptionGroup? {
        val n = clean(name)
        val all = groups()
        if (n.isEmpty() || all.any { it.name.equals(n, ignoreCase = true) }) return null
        val next = (all.mapNotNull { it.id.removePrefix("g").toIntOrNull() }.maxOrNull() ?: 0) + 1
        val g = SubscriptionGroup("g$next", n, channelIds)
        write(all + g); return g
    }

    /** False when the name is blank or another group has it. */
    fun rename(id: String, name: String): Boolean {
        val n = clean(name)
        val all = groups()
        if (n.isEmpty() || all.any { it.id != id && it.name.equals(n, ignoreCase = true) }) return false
        write(all.map { if (it.id == id) it.copy(name = n) else it }); return true
    }

    fun setMembers(id: String, channelIds: Set<String>) = write(groups().map { if (it.id == id) it.copy(channelIds = channelIds) else it })

    fun toggle(id: String, channelId: String) = write(groups().map {
        if (it.id == id) it.copy(channelIds = if (channelId in it.channelIds) it.channelIds - channelId else it.channelIds + channelId) else it
    })

    fun delete(id: String) = write(groups().filter { it.id != id })

    /** Puts the groups back as they were (the Undo of a delete). */
    fun restore(groups: List<SubscriptionGroup>) = write(groups)

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim()
    private fun write(list: List<SubscriptionGroup>) {
        store.put(key, SettingLayer.Global, list.joinToString("\n") { "${it.id}\t${clean(it.name)}\t${it.channelIds.joinToString(",")}" })
        _revision.value++
    }

    companion object {
        private val _revision = MutableStateFlow(0)
        val revision: StateFlow<Int> get() = _revision
    }
}
