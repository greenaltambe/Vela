package com.greenaltambe.vela.core.settings

/**
 * The shelves Home can show above its main list, in the order they come. The viewer chooses which and in what order (Customize > Home); a
 * shelf that has nothing to show, or whose source fails, is simply not there, whatever the choice.
 */
enum class HomeShelf(val label: String, val hint: String) {
    CONTINUE("Continue watching", "Videos you started and did not finish"),
    SUBSCRIPTIONS("New from your subscriptions", "The newest uploads of channels you subscribe to"),
    BECAUSE("Because you watched", "Related to what you watched lately, with no account"),
    CHANNELS("From channels you watch a lot", "Channels you often watch but do not subscribe to"),
    INTERESTS("Your interests", "Searches for the topics that come up most in your history"),
    SHORTS("Shorts", "Short videos from your subscriptions"),
}

object HomeShelves {
    val default: List<HomeShelf> = HomeShelf.entries.toList()

    /** The enabled shelves in order; unknown names are dropped and an empty or damaged value means the default. */
    fun parse(raw: String): List<HomeShelf> {
        if (raw.isBlank()) return default
        if (raw.trim() == NONE) return emptyList()
        val shelves = raw.split(',').mapNotNull { n -> HomeShelf.entries.firstOrNull { it.name.equals(n.trim(), ignoreCase = true) } }.distinct()
        return shelves.ifEmpty { default }
    }

    fun encode(shelves: List<HomeShelf>): String = if (shelves.isEmpty()) NONE else shelves.joinToString(",") { it.name }

    /** Every shelf, enabled ones first in their order, then the rest: what the Customize page lists. */
    fun ordered(enabled: List<HomeShelf>): List<HomeShelf> = enabled + HomeShelf.entries.filter { it !in enabled }

    private const val NONE = "-"
}

/** How often a shelf breaks up the list: after every [step] videos. */
enum class HomeDensity(val label: String, val step: Int) { FEW("Few", 8), NORMAL("Normal", 5), MANY("Many", 3) }
