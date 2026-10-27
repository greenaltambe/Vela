package com.greenaltambe.vela.core.settings

interface SettingCodec<T> {
    fun encode(value: T): String
    fun decode(raw: String): T?
}

object SettingCodecs {
    val boolean = object : SettingCodec<Boolean> {
        override fun encode(value: Boolean) = value.toString()
        override fun decode(raw: String) = raw.toBooleanStrictOrNull()
    }

    val float = object : SettingCodec<Float> {
        override fun encode(value: Float) = value.toString()
        override fun decode(raw: String) = raw.toFloatOrNull()
    }

    fun <E : Enum<E>> enum(entries: List<E>) = object : SettingCodec<E> {
        override fun encode(value: E) = value.name
        override fun decode(raw: String) = entries.firstOrNull { it.name == raw }
    }
}

/** Groups settings on the settings screen; declared in display order. */
enum class SettingSection(val title: String) {
    PLAYBACK("Playback"),
    FEED("Feed"),
    MODULES("Modules"),
}

/** One selectable value of an enumerated setting; [raw] is what the codec stores. */
class SettingChoice(val raw: String, val label: String)

/** Allowed numeric span for a slider-style setting. */
class SettingRange(val min: Double, val max: Double, val step: Double? = null) {
    init { require(min < max) { "Empty range $min..$max" } }
}

/** One option, declared once. UI, backup/restore, validation and search all read from this. */
class Setting<T>(
    val key: String,
    val default: T,
    val scope: SettingScope,
    val label: String,
    val keywords: List<String>,
    val codec: SettingCodec<T>,
    val section: SettingSection,
    /** Options for enumerated settings; empty when the value isn't a pick-one. */
    val choices: List<SettingChoice> = emptyList(),
    /** Bounds for numeric settings; null when unbounded or not numeric. */
    val range: SettingRange? = null,
)
