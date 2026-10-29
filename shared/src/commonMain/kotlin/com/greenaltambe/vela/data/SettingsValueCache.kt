package com.greenaltambe.vela.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Read-through cache of the `Setting` table, one per database (so every store on the same database shares it and a write through
 * any of them is seen by all). Feed filters, DeArrow and the card badges ask for a setting once per VIDEO; before this each ask was a
 * SQLite query on the main thread (measured: ~1.5 s of main-thread time to open Subscriptions with 500 cached videos).
 *
 * A value of `null` is cached too ("no override stored"). Copy-on-write over an atomic reference: reads never lock, and the table
 * of settings is small. Every write path must call [put] or [clear]; the only ones are in [SqlDelightSettingsStore].
 */
class SettingsValueCache {
    private val map = MutableStateFlow<Map<String, String?>>(emptyMap())

    fun lookup(key: String, load: () -> String?): String? {
        val m = map.value
        if (m.containsKey(key)) return m[key]
        val v = load()
        map.update { if (it.size > MAX_ENTRIES) mapOf(key to v) else it + (key to v) }
        return v
    }

    fun put(key: String, value: String?) = map.update { it + (key to value) }
    fun clear() { map.value = emptyMap() }

    private companion object { const val MAX_ENTRIES = 20_000 }
}

/** The cache that belongs to [owner] (a database's query object); weakly held so a closed test database is not kept alive. */
internal expect fun settingsCacheFor(owner: Any): SettingsValueCache
