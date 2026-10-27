package com.greenaltambe.vela.core.settings

/** How a setting is edited. Derived from the schema alone: nothing is declared per setting. */
enum class SettingControl { TOGGLE, CHOICE, SLIDER, TEXT }

fun Setting<*>.control(): SettingControl = when {
    default is Boolean -> SettingControl.TOGGLE
    choices.isNotEmpty() -> SettingControl.CHOICE
    range != null -> SettingControl.SLIDER
    else -> SettingControl.TEXT
}

@Suppress("UNCHECKED_CAST")
private fun Setting<*>.untyped() = this as Setting<Any?>

/** The current value (global layer, falling back to the default) in the codec's string form. */
fun SettingsResolver.rawValue(setting: Setting<*>): String = setting.untyped().let { it.codec.encode(resolve(it)) }

fun SettingsResolver.isOverridden(setting: Setting<*>, layer: SettingLayer = SettingLayer.Global): Boolean = hasValue(setting, layer)

/**
 * Sets the global value from its string form. False, and nothing written, if [raw] does not decode, is not one of
 * the setting's choices, or falls outside its range.
 */
fun SettingsResolver.setRaw(setting: Setting<*>, raw: String): Boolean {
    val s = setting.untyped()
    val value = s.codec.decode(raw) ?: return false
    if (s.choices.isNotEmpty() && s.choices.none { it.raw == raw }) return false
    s.range?.let { r -> val n = raw.toDoubleOrNull() ?: return false; if (n < r.min || n > r.max) return false }
    set(s, value)
    return true
}

fun SettingsResolver.resetToDefault(setting: Setting<*>) = clear(setting, SettingLayer.Global)

/** Settings grouped by section in display order; with a non-blank [query], only those [Settings.search] finds. */
fun settingsBySection(query: String = "", all: List<Setting<*>> = Settings.all): List<Pair<SettingSection, List<Setting<*>>>> {
    val shown = if (query.isBlank()) all else Settings.search(query, all)
    return SettingSection.entries.mapNotNull { section -> shown.filter { it.section == section }.takeIf { it.isNotEmpty() }?.let { section to it } }
}
