package com.greenaltambe.vela.core.settings

/** Resolves video -> channel -> global -> default, skipping layers the setting's scope doesn't allow. */
class SettingsResolver(private val store: SettingsStore) {

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

    fun <T> set(setting: Setting<T>, value: T, layer: SettingLayer = SettingLayer.Global) {
        require(layer.scope <= setting.scope) { "${setting.key} cannot be overridden at ${layer.scope} level" }
        store.put(setting.key, layer, setting.codec.encode(value))
    }

    fun clear(setting: Setting<*>, layer: SettingLayer) = store.remove(setting.key, layer)
}
