package com.greenaltambe.vela.data

import com.greenaltambe.vela.core.settings.SettingLayer
import com.greenaltambe.vela.core.settings.SettingsStore
import com.greenaltambe.vela.data.db.SettingQueries
import com.greenaltambe.vela.data.db.VelaDatabase

class SqlDelightSettingsStore(private val queries: SettingQueries) : SettingsStore {
    constructor(db: VelaDatabase) : this(db.settingQueries)

    override fun get(key: String, layer: SettingLayer): String? =
        queries.selectValue(key, layer.scope.name, layer.rowId()).executeAsOneOrNull()

    override fun put(key: String, layer: SettingLayer, raw: String) {
        queries.upsert(key, layer.scope.name, layer.rowId(), raw)
    }

    override fun remove(key: String, layer: SettingLayer) {
        queries.delete(key, layer.scope.name, layer.rowId())
    }

    /** Every stored override, for backup. */
    fun entries(): List<StoredSetting> = queries.selectAll().executeAsList().map { StoredSetting(it.key, it.layerScope, it.layerId, it.value_) }

    /** The global layer has no id; the table stores it as '' to keep the primary key unique. */
    private fun SettingLayer.rowId() = id.orEmpty()
}

/** One raw row of the Setting table. */
data class StoredSetting(val key: String, val scope: String, val id: String, val value: String)
