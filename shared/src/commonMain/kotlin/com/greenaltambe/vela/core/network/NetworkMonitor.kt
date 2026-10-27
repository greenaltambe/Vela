package com.greenaltambe.vela.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the current connection costs: [UNMETERED] (Wi-Fi, ethernet), [METERED] (mobile data, hotspots) or none. */
enum class NetworkType { UNMETERED, METERED, OFFLINE }

/** Platform-specific: ConnectivityManager on Android. Changes are reported, never acted on, by this layer. */
interface NetworkMonitor {
    val type: StateFlow<NetworkType>
}

/** A connection that never changes: the desktop for now, and tests (via [set]). */
class FixedNetworkMonitor(initial: NetworkType = NetworkType.UNMETERED) : NetworkMonitor {
    private val _type = MutableStateFlow(initial)
    override val type: StateFlow<NetworkType> = _type
    fun set(type: NetworkType) { _type.value = type }
}
