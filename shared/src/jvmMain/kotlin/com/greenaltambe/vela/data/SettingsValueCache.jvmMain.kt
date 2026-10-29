package com.greenaltambe.vela.data

private val caches = java.util.Collections.synchronizedMap(java.util.WeakHashMap<Any, SettingsValueCache>())

internal actual fun settingsCacheFor(owner: Any): SettingsValueCache = caches.getOrPut(owner) { SettingsValueCache() }
