package com.greenaltambe.vela.core.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A Look: a named bundle of APPEARANCE settings (keys in [LookKeys]) with a schema version. Applying one writes all its values in
 * one batch and hands back an [LookUndo] that restores exactly what was there. Behaviour (account safety, tier order, downloads,
 * SponsorBlock...) is not a Look key, so no Look - not even a hostile shared file - can change it.
 * The Looks screen arrives in task 24; this is the model it will use.
 */
@Serializable
data class Look(val name: String, val values: Map<String, String>, val schemaVersion: Int = Look.SCHEMA, val format: String = Look.FORMAT) {
    companion object {
        const val FORMAT = "vela-look"
        const val SCHEMA = 1
    }
}

/** What [LookStore.apply] changed, so the toast's Undo can put it back in one step. */
class LookUndo internal constructor(private val settings: SettingsResolver, private val before: Map<Setting<*>, String?>) {
    /** Restores every touched setting to its previous stored value (or to "not set", i.e. the default). One batch. */
    fun undo() = settings.batch {
        for ((setting, raw) in before) if (raw == null) settings.resetToDefault(setting) else settings.setRaw(setting, raw)
    }
    val touched: Set<String> get() = before.keys.map { it.key }.toSet()
}

sealed interface LookImport {
    class Ok(val look: Look, val ignoredKeys: List<String>) : LookImport
    class Refused(val reason: String) : LookImport
}

class LookStore(private val settings: SettingsResolver) {
    private val byKey: Map<String, Setting<*>> = Settings.all.filter { LookKeys.isLookKey(it.key) }.associateBy { it.key }

    /** The current value of every Look key, as a Look called [name]. */
    fun capture(name: String) = Look(name, byKey.values.associate { it.key to settings.rawValue(it) })

    /** Writes [look] in one batch. Keys that are not Look keys, or whose values do not fit the setting, are skipped (and returned). */
    fun apply(look: Look): Pair<LookUndo, List<String>> {
        val before = LinkedHashMap<Setting<*>, String?>(); val skipped = ArrayList<String>()
        settings.batch {
            for ((key, raw) in look.values) {
                val setting = byKey[key]
                if (setting == null) { skipped += key; continue }
                val previous = if (settings.isOverridden(setting)) settings.rawValue(setting) else null
                if (settings.setRaw(setting, raw)) before[setting] = previous else skipped += key
            }
        }
        return LookUndo(settings, before) to skipped
    }

    /** Back to the defaults for every Look key (the "Reset look" action), undoable. */
    fun reset(): LookUndo {
        val before = byKey.values.associateWith { if (settings.isOverridden(it)) settings.rawValue(it) else null }
        settings.batch { byKey.values.forEach { settings.resetToDefault(it) } }
        return LookUndo(settings, before)
    }

    companion object {
        private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

        fun encode(look: Look): String = json.encodeToString(Look.serializer(), look)

        /** Reads a Look file. Refuses anything that is not a Vela look or is from a newer schema; unknown keys are kept out and reported. */
        fun decode(text: String): LookImport {
            val look = try { json.decodeFromString(Look.serializer(), text) } catch (e: Exception) { return LookImport.Refused("not a Vela look file") }
            if (look.format != Look.FORMAT) return LookImport.Refused("not a Vela look file")
            if (look.schemaVersion > Look.SCHEMA) return LookImport.Refused("made by a newer Vela (look format ${look.schemaVersion}); update Vela to use it")
            val (known, unknown) = look.values.keys.partition { LookKeys.isLookKey(it) }
            return LookImport.Ok(look.copy(values = look.values.filterKeys { it in known }), unknown)
        }
    }
}
