package com.greenaltambe.vela.core.http

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Instant

/** One line for Diagnostics: what a feature module did and whether it is currently working. */
class ModuleEvent(val moduleId: String, val ok: Boolean, val message: String, val at: Instant)

/**
 * Modules never block playback or the feed: when the network or an API fails they go quietly inactive, and this is
 * where that shows. Keeps the latest events (bounded) and the last known state per module.
 */
class ModuleLog(private val capacity: Int = 200) {
    private val _events = MutableStateFlow<List<ModuleEvent>>(emptyList())
    val events: StateFlow<List<ModuleEvent>> = _events

    fun record(event: ModuleEvent) = _events.update { (it + event).takeLast(capacity) }

    /** The newest event of each module, i.e. whether it is working right now. */
    fun status(): Map<String, ModuleEvent> = _events.value.associateBy { it.moduleId }
}
