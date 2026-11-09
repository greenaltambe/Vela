package com.greenaltambe.vela.feed

import com.greenaltambe.vela.core.settings.SettingLayer
import com.greenaltambe.vela.core.settings.SettingsStore
import com.greenaltambe.vela.extractor.VideoSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A channel the viewer blocked, with the name it had then (for the list; the id is what is matched). */
data class BlockedChannel(val id: String, val name: String)

/**
 * What is blocked, as a plain value the feed filter reads: channel ids, and words matched against the title (plain substring, ignoring case).
 * Blank ids and words are ignored, so a half-filled entry can never block everything.
 */
class BlockRules(channelIds: Set<String>, keywords: List<String>) {
    private val ids = channelIds.filter { it.isNotBlank() }.toSet()
    private val words = keywords.map { it.trim() }.filter { it.isNotEmpty() }
    val isEmpty: Boolean get() = ids.isEmpty() && words.isEmpty()

    fun blocks(v: VideoSummary): Boolean = (v.channelId.isNotBlank() && v.channelId in ids) || words.any { v.title.contains(it, ignoreCase = true) }
    fun blocksChannel(channelId: String): Boolean = channelId.isNotBlank() && channelId in ids

    companion object { val None = BlockRules(emptySet(), emptyList()) }
}

/** Drops blocked videos. Runs in every list that goes through the feed pipeline (Home, Search, Explore, Subscriptions, Related). */
class BlocklistStep(private val rules: BlockRules) : FeedStep {
    override fun apply(items: List<VideoSummary>, context: FeedContext): List<VideoSummary> =
        if (rules.isEmpty) items else items.filterNot(rules::blocks)
}

/**
 * The viewer's blocklist: channels and title words, kept as two plain settings rows (so Backup and restore carries them) and shared by all accounts.
 * [revision] changes with every edit so lists on screen refilter.
 */
class Blocklist(private val store: SettingsStore) {
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> get() = _revision

    fun channels(): List<BlockedChannel> = lines(CHANNELS).mapNotNull { l ->
        val id = l.substringBefore('\t').trim()
        if (id.isEmpty()) null else BlockedChannel(id, l.substringAfter('\t', "").trim().ifEmpty { id })
    }
    fun keywords(): List<String> = lines(KEYWORDS)

    fun isBlocked(channelId: String) = channels().any { it.id == channelId }

    fun blockChannel(id: String, name: String): Boolean {
        if (id.isBlank() || isBlocked(id)) return false
        writeChannels(channels() + BlockedChannel(id, clean(name))); return true
    }
    fun unblockChannel(id: String) = writeChannels(channels().filter { it.id != id })

    /** False for a blank word or one already there (ignoring case). */
    fun addKeyword(word: String): Boolean {
        val w = clean(word)
        if (w.isEmpty() || keywords().any { it.equals(w, ignoreCase = true) }) return false
        writeKeywords(keywords() + w); return true
    }
    fun removeKeyword(word: String) = writeKeywords(keywords().filterNot { it.equals(word, ignoreCase = true) })

    /** Puts a list back as it was (the Undo of a removal). */
    fun restore(channels: List<BlockedChannel>, keywords: List<String>) { writeChannels(channels); writeKeywords(keywords) }

    fun rules(): BlockRules = BlockRules(channels().map { it.id }.toSet(), keywords())

    /** The list as text to share: one entry per line, `channel<TAB>id<TAB>name` or `word<TAB>text`. */
    fun export(): String = buildString {
        appendLine(HEADER)
        channels().forEach { appendLine("channel\t${it.id}\t${it.name}") }
        keywords().forEach { appendLine("word\t$it") }
    }

    /** Adds what [text] holds (an [export], or just words, one per line); returns how many entries were new. Unreadable lines are skipped. */
    fun import(text: String): Int {
        var added = 0
        text.lineSequence().map { it.trimEnd() }.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
            val parts = line.split('\t')
            when (parts[0]) {
                "channel" -> if (parts.size >= 2 && blockChannel(parts[1], parts.getOrElse(2) { parts[1] })) added++
                "word" -> if (parts.size >= 2 && addKeyword(parts[1])) added++
                else -> if (parts.size == 1 && addKeyword(line)) added++
            }
        }
        return added
    }

    private fun lines(key: String) = store.get(key, SettingLayer.Global)?.lines()?.map { it.trim('\r') }?.filter { it.isNotBlank() }.orEmpty()
    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim()
    private fun writeChannels(list: List<BlockedChannel>) { store.put(CHANNELS, SettingLayer.Global, list.joinToString("\n") { "${it.id}\t${clean(it.name)}" }); _revision.value++ }
    private fun writeKeywords(list: List<String>) { store.put(KEYWORDS, SettingLayer.Global, list.joinToString("\n")); _revision.value++ }

    private companion object {
        const val CHANNELS = "blocklist.channels"
        const val KEYWORDS = "blocklist.keywords"
        const val HEADER = "# Vela blocklist"
    }
}
