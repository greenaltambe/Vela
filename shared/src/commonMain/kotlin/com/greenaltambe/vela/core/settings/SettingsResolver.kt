package com.greenaltambe.vela.core.settings

import kotlinx.coroutines.flow.update

/** Resolves video -> channel -> global -> default, skipping layers the setting's scope doesn't allow. */
class SettingsResolver(private val store: SettingsStore) {
    private val _revision = kotlinx.coroutines.flow.MutableStateFlow(0)

    /** Bumped after every write through this resolver, so UI can re-read what it shows. */
    val revision: kotlinx.coroutines.flow.StateFlow<Int> = _revision

    fun <T> resolve(setting: Setting<T>, channelId: String? = null, videoId: String? = null): T {
        val layers = buildList {
            if (videoId != null && setting.scope >= SettingScope.VIDEO) add(SettingLayer.video(videoId))
            if (channelId != null && setting.scope >= SettingScope.CHANNEL) add(SettingLayer.channel(channelId))
            add(SettingLayer.Global)
        }
        for (layer in layers) {
            val value = store.get(setting.key, layer)?.let(setting.codec::decode)
            if (value != null) return value
        }
        return setting.default
    }

    /** True if [layer] holds its own value for [setting], as opposed to inheriting. */
    fun hasValue(setting: Setting<*>, layer: SettingLayer): Boolean = store.get(setting.key, layer) != null

    private var batchDepth = 0
    private var batchDirty = false
    private fun changed() { if (batchDepth > 0) batchDirty = true else _revision.update { it + 1 } }

    /** Runs [block]; every write inside it counts as ONE change for observers (one revision bump, so the UI recomposes once). */
    fun batch(block: () -> Unit) {
        batchDepth++
        try { block() } finally { batchDepth--; if (batchDepth == 0 && batchDirty) { batchDirty = false; _revision.update { it + 1 } } }
    }

    fun <T> set(setting: Setting<T>, value: T, layer: SettingLayer = SettingLayer.Global) {
        require(layer.scope <= setting.scope) { "${setting.key} cannot be overridden at ${layer.scope} level" }
        store.put(setting.key, layer, setting.codec.encode(value))
        changed()
    }

    fun clear(setting: Setting<*>, layer: SettingLayer) {
        store.remove(setting.key, layer)
        changed()
    }
}
