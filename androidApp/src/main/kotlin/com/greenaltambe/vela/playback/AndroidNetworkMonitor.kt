package com.greenaltambe.vela.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.greenaltambe.vela.core.network.NetworkMonitor
import com.greenaltambe.vela.core.network.NetworkType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Pure so it can be tested: a network with internet that is not flagged metered is unmetered. */
internal fun classifyNetwork(hasInternet: Boolean, notMetered: Boolean): NetworkType = when {
    !hasInternet -> NetworkType.OFFLINE
    notMetered -> NetworkType.UNMETERED
    else -> NetworkType.METERED
}

/**
 * Tracks the default network through [ConnectivityManager]. "Metered" is the system's own flag, so a Wi-Fi network the
 * user marked as metered, or a hotspot, counts as metered, as it should.
 */
class AndroidNetworkMonitor(context: Context) : NetworkMonitor {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val _type = MutableStateFlow(current())
    override val type: StateFlow<NetworkType> = _type

    init {
        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                _type.value = classify(capabilities)
            }

            override fun onLost(network: Network) {
                _type.value = NetworkType.OFFLINE
            }
        })
    }

    private fun current(): NetworkType {
        val caps = manager.getNetworkCapabilities(manager.activeNetwork ?: return NetworkType.OFFLINE) ?: return NetworkType.OFFLINE
        return classify(caps)
    }

    private fun classify(caps: NetworkCapabilities) = classifyNetwork(
        hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
        notMetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
    )
}
