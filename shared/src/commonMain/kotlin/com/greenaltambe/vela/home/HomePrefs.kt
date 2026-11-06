package com.greenaltambe.vela.home

import com.greenaltambe.vela.core.settings.SettingLayer
import com.greenaltambe.vela.core.settings.SettingsStore
import kotlin.time.Instant

/**
 * What Home remembers about this viewer, per account: the videos and channels sent away ("Not interested", "Hide this channel from Home") and when
 * Home was last opened (for the "new" marker on the subscription shelf). Stored as plain settings rows, so backup and restore carry them.
 */
class HomePrefs(private val store: SettingsStore, private val accountId: String) {
    private fun key(name: String) = "home.$name.$accountId"
    private fun read(name: String): List<String> = store.get(key(name), SettingLayer.Global)?.split(',')?.filter { it.isNotBlank() }.orEmpty()
    private fun write(name: String, values: List<String>) = store.put(key(name), SettingLayer.Global, values.distinct().takeLast(MAX).joinToString(","))

    fun dismissed() = HomeDismissed(read("hiddenVideos").toSet(), read("hiddenChannels").toSet())

    fun dismissVideo(id: String) = write("hiddenVideos", read("hiddenVideos") + id)
    fun restoreVideo(id: String) = write("hiddenVideos", read("hiddenVideos") - id)
    fun hideChannel(id: String) = write("hiddenChannels", read("hiddenChannels") + id)
    fun restoreChannel(id: String) = write("hiddenChannels", read("hiddenChannels") - id)
    fun clearDismissed() { write("hiddenVideos", emptyList()); write("hiddenChannels", emptyList()) }

    /** The time Home was opened before this visit; null the first time. */
    fun lastOpened(): Instant? = store.get(key("lastOpened"), SettingLayer.Global)?.toLongOrNull()?.let { Instant.fromEpochMilliseconds(it) }
    fun markOpened(now: Instant) = store.put(key("lastOpened"), SettingLayer.Global, now.toEpochMilliseconds().toString())

    private companion object { const val MAX = 1500 }
}
