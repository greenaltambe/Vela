package com.greenaltambe.vela.core.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Where an override lives: the global layer, or one channel / video. */
data class SettingLayer(val scope: SettingScope, val id: String? = null) {
    init {
        require((scope == SettingScope.GLOBAL) == (id == null)) { "Only the GLOBAL layer has no id" }
    }

    companion object {
        val Global = SettingLayer(SettingScope.GLOBAL)
        fun channel(id: String) = SettingLayer(SettingScope.CHANNEL, id)
        fun video(id: String) = SettingLayer(SettingScope.VIDEO, id)
    }
}

/** Raw string storage; typing is handled by [Setting.codec]. */
interface SettingsStore {
    fun get(key: String, layer: SettingLayer): String?
    fun put(key: String, layer: SettingLayer, raw: String)
    fun remove(key: String, layer: SettingLayer)
}

/** Safe for concurrent use: state is an immutable map swapped atomically. */
class InMemorySettingsStore : SettingsStore {
    private val values = MutableStateFlow<Map<Pair<String, SettingLayer>, String>>(emptyMap())

    override fun get(key: String, layer: SettingLayer) = values.value[key to layer]
    override fun put(key: String, layer: SettingLayer, raw: String) = values.update { it + ((key to layer) to raw) }
    override fun remove(key: String, layer: SettingLayer) = values.update { it - (key to layer) }
}
