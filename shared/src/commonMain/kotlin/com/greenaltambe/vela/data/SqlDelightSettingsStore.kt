package com.greenaltambe.vela.data

import com.greenaltambe.vela.core.settings.SettingLayer
import com.greenaltambe.vela.core.settings.SettingsStore
import com.greenaltambe.vela.data.db.SettingQueries
import com.greenaltambe.vela.data.db.VelaDatabase

class SqlDelightSettingsStore(private val queries: SettingQueries) : SettingsStore {
    constructor(db: VelaDatabase) : this(db.settingQueries)

    private val cache = settingsCacheFor(queries)
    private fun cacheKey(key: String, layer: SettingLayer) = "$key\u0000${layer.scope.name}\u0000${layer.rowId()}"

    override fun get(key: String, layer: SettingLayer): String? =
        cache.lookup(cacheKey(key, layer)) { queries.selectValue(key, layer.scope.name, layer.rowId()).executeAsOneOrNull() }

    override fun put(key: String, layer: SettingLayer, raw: String) {
        queries.upsert(key, layer.scope.name, layer.rowId(), raw)
        cache.put(cacheKey(key, layer), raw)
    }

    override fun remove(key: String, layer: SettingLayer) {
        queries.delete(key, layer.scope.name, layer.rowId())
        cache.put(cacheKey(key, layer), null)
    }

    /** Deletes every row whose key starts with [prefix] (the cache-like `health.*`/`release.*` rows). */
    fun removePrefix(prefix: String) {
        queries.deleteByPrefix(prefix)
        cache.clear()
    }

    /** Every stored override, for backup. */
    fun entries(): List<StoredSetting> = queries.selectAll().executeAsList().map { StoredSetting(it.key, it.layerScope, it.layerId, it.value_) }

    /** The global layer has no id; the table stores it as '' to keep the primary key unique. */
    private fun SettingLayer.rowId() = id.orEmpty()
}

/** One raw row of the Setting table. */
data class StoredSetting(val key: String, val scope: String, val id: String, val value: String)
