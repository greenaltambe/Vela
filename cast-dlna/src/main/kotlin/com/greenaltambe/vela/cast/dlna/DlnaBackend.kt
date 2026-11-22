package com.greenaltambe.vela.cast.dlna

import com.greenaltambe.vela.cast.CastBackend
import com.greenaltambe.vela.cast.CastTarget
import com.greenaltambe.vela.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * DLNA/UPnP casting. While discovery runs it searches every [intervalMs]; a device is listed until it has missed [forgetAfter] searches in a row.
 * [onSearchWindow] wraps each search so Android can hold its multicast lock for exactly that long.
 */
class DlnaCastBackend(
    private val scope: CoroutineScope,
    private val discovery: DlnaDiscovery = DlnaDiscovery(),
    private val http: UpnpHttp = JdkUpnpHttp,
    private val intervalMs: Long = 6000,
    private val forgetAfter: Int = 3,
    private val onSearchWindow: (search: () -> Unit) -> Unit = { it() },
) : CastBackend {
    private val _targets = MutableStateFlow<List<CastTarget>>(emptyList())
    override val targets: StateFlow<List<CastTarget>> = _targets

    private val known = LinkedHashMap<String, DlnaRenderer>()
    private val misses = HashMap<String, Int>()
    private var job: Job? = null

    override fun startDiscovery() {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                var found = emptyList<DlnaRenderer>()
                runCatching { onSearchWindow { found = discovery.discover() } }
                merge(found)
                delay(intervalMs)
            }
        }
    }

    @Synchronized private fun merge(found: List<DlnaRenderer>) {
        val seen = found.map { it.udn }.toSet()
        found.forEach { known[it.udn] = it; misses[it.udn] = 0 }
        known.keys.toList().filter { it !in seen }.forEach { id -> val m = (misses[id] ?: 0) + 1; misses[id] = m; if (m >= forgetAfter) { known.remove(id); misses.remove(id) } }
        _targets.value = known.values.map { CastTarget(it.udn, it.name) }
    }

    override fun stopDiscovery() { job?.cancel(); job = null }

    @Synchronized override fun connect(target: CastTarget): PlayerController {
        val renderer = known[target.id] ?: throw IllegalStateException("${target.name} is no longer on the network.")
        return DlnaController(AvTransport(renderer.avTransportControlUrl, http), scope)
    }
}
