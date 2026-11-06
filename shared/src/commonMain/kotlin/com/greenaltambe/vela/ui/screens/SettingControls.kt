package com.greenaltambe.vela.ui.screens

import com.greenaltambe.vela.core.settings.Setting
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.core.settings.isOverridden
import com.greenaltambe.vela.core.settings.rawValue
import com.greenaltambe.vela.core.settings.resetToDefault
import com.greenaltambe.vela.core.settings.setRaw
import com.greenaltambe.vela.core.settings.title
import com.greenaltambe.vela.ui.look.UndoToasts

/** Writes [raw] to [setting] and raises the undo toast; false (nothing written) if the value does not fit. */
fun SettingsResolver.setWithUndo(setting: Setting<*>, raw: String, toasts: UndoToasts, wordFor: (String) -> String = { it }): Boolean {
    val before = if (isOverridden(setting)) rawValue(setting) else null
    if (rawValue(setting) == raw) return true
    if (!setRaw(setting, raw)) return false
    toasts.show("${setting.title} → ${wordFor(raw)}") { if (before == null) resetToDefault(setting) else setRaw(setting, before) }
    return true
}
