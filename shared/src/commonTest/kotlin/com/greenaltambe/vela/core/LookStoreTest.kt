package com.greenaltambe.vela.core

import com.greenaltambe.vela.core.settings.*
import kotlin.test.*

class LookStoreTest {
    private val settings = SettingsResolver(InMemorySettingsStore())
    private val store = LookStore(settings)

    @Test fun applyingALookIsOneChangeAndOneUndoRestoresEverything() {
        settings.set(LookSettings.corners, 10f)                      // a value the user had already set
        val revision0 = settings.revision.value
        val look = Look("Round", mapOf("look.corners" to "16.0", "look.font" to "SERIF", "look.glass" to "false", "appearance.performanceMode" to "true"))
        val (undo, skipped) = store.apply(look)
        assertTrue(skipped.isEmpty())
        assertEquals(revision0 + 1, settings.revision.value, "one revision bump for the whole Look")
        assertEquals(16f, settings.resolve(LookSettings.corners)); assertEquals(FontChoice.SERIF, settings.resolve(LookSettings.font)); assertTrue(settings.resolve(Settings.performanceMode))
        undo.undo()
        assertEquals(10f, settings.resolve(LookSettings.corners), "back to what the user had")
        assertEquals(FontChoice.VELA, settings.resolve(LookSettings.font)); assertFalse(settings.isOverridden(LookSettings.font), "an untouched default goes back to 'not set'")
        assertTrue(settings.resolve(LookSettings.glass)); assertFalse(settings.resolve(Settings.performanceMode))
        assertEquals(revision0 + 2, settings.revision.value, "undo is one change too")
    }

    @Test fun aLookCanNeverChangeBehaviourSettings() {
        val before = Settings.all.filter { !LookKeys.isLookKey(it.key) }.associate { it.key to settings.rawValue(it) }
        val hostile = Look("Evil", mapOf(
            "account.tierOrder" to "AUTHENTICATED,YT_DLP", "account.killSwitch" to "false", "downloads.wifiOnly" to "false",
            "modules.sponsorblock.enabled" to "false", "playback.allowAgeRestricted" to "true", "look.corners" to "8.0",
        ))
        val (_, skipped) = store.apply(hostile)
        assertEquals(setOf("account.tierOrder", "account.killSwitch", "downloads.wifiOnly", "modules.sponsorblock.enabled", "playback.allowAgeRestricted"), skipped.toSet())
        assertEquals(8f, settings.resolve(LookSettings.corners), "its look key still applied")
        assertEquals(before, Settings.all.filter { !LookKeys.isLookKey(it.key) }.associate { it.key to settings.rawValue(it) }, "no behaviour setting moved")
    }

    @Test fun valuesThatDoNotFitAreSkippedNotWritten() {
        val (_, skipped) = store.apply(Look("Bad", mapOf("look.corners" to "999", "look.font" to "COMIC", "look.density" to "ROOMY")))
        assertEquals(setOf("look.corners", "look.font"), skipped.toSet())
        assertEquals(12f, settings.resolve(LookSettings.corners)); assertEquals(DensityChoice.ROOMY, settings.resolve(LookSettings.density))
    }

    @Test fun captureRoundTripsThroughJsonAndForeignOrNewerFilesAreRefused() {
        settings.set(LookSettings.theme, ThemeMode.DARK); settings.set(LookSettings.iconStyle, IconStyle.LINE)
        val look = store.capture("Mine")
        val text = LookStore.encode(look)
        val back = assertIs<LookImport.Ok>(LookStore.decode(text))
        assertEquals(look, back.look); assertTrue(back.ignoredKeys.isEmpty())
        assertTrue(back.look.values.keys.all { LookKeys.isLookKey(it) })
        assertIs<LookImport.Refused>(LookStore.decode("""{"hello":1}""")); assertIs<LookImport.Refused>(LookStore.decode("not json"))
        assertIs<LookImport.Refused>(LookStore.decode("""{"name":"x","values":{},"format":"other-app-look"}"""))
        val newer = assertIs<LookImport.Refused>(LookStore.decode("""{"name":"x","values":{},"schemaVersion":99,"format":"vela-look"}""")); assertTrue("newer Vela" in newer.reason)
        val withUnknown = assertIs<LookImport.Ok>(LookStore.decode("""{"name":"x","values":{"look.font":"MONO","account.tierOrder":"X","look.future":"1"},"schemaVersion":1,"format":"vela-look"}"""))
        assertEquals(setOf("account.tierOrder"), withUnknown.ignoredKeys.toSet().minus("look.future")); assertEquals(mapOf("look.font" to "MONO", "look.future" to "1"), withUnknown.look.values)
    }

    @Test fun resetPutsEveryLookKeyBackToItsDefaultAndCanBeUndone() {
        settings.set(LookSettings.font, FontChoice.MONO); settings.set(LookSettings.corners, 4f)
        val undo = store.reset()
        assertEquals(FontChoice.VELA, settings.resolve(LookSettings.font)); assertEquals(12f, settings.resolve(LookSettings.corners))
        undo.undo()
        assertEquals(FontChoice.MONO, settings.resolve(LookSettings.font)); assertEquals(4f, settings.resolve(LookSettings.corners))
    }
}
