package com.greenaltambe.vela.ui.look

import com.greenaltambe.vela.core.settings.*
import java.io.File
import kotlin.test.*

class LookBuilderTest {
    private fun build(s: LookSnapshot = LookSnapshot(), systemDark: Boolean = true) = LookBuilder.build(s, systemDark)

    @Test fun defaultsGiveTheDocumentedShapesSpacingAndMotion() {
        val l = build()
        assertEquals(18f, l.radii.largeDp); assertEquals(12f, l.radii.mediumDp); assertEquals(6f, l.radii.smallDp); assertEquals(999f, l.radii.fullDp)
        assertEquals(1f, l.typography.scale); assertEquals(TitleWeight.LIGHT, l.typography.titleWeight)
        assertTrue(l.effects.blur); assertFalse(l.motion.reduced); assertEquals(listOf(90, 160, 240, 360), l.motion.durationsMs)
    }

    @Test fun oneCornersSettingDrivesEveryRadius() {
        val l = build(LookSnapshot(corners = 0f)); assertEquals(0f, l.radii.largeDp); assertEquals(0f, l.radii.mediumDp)
        val r = build(LookSnapshot(corners = 16f)); assertEquals(24f, r.radii.largeDp); assertEquals(16f, r.radii.mediumDp); assertEquals(8f, r.radii.smallDp)
    }

    @Test fun performanceModeForcesGlassOffAndMotionReducedWhateverTheOtherSettingsSay() {
        val l = build(LookSnapshot(glass = true, reduceMotion = false, performance = true))
        assertFalse(l.effects.blur); assertTrue(l.motion.reduced)
        assertTrue(build(LookSnapshot(reduceMotion = true)).motion.reduced)
    }

    @Test fun themeFollowsTheSystemUnlessChosenAndPureBlackIsAlwaysDarkAndBlack() {
        assertEquals(Looks.Dark.colors.background, build(systemDark = true).colors.background)
        assertEquals(Looks.Light.colors.background, build(systemDark = false).colors.background)
        assertEquals(Looks.Light.colors.background, build(LookSnapshot(theme = ThemeMode.LIGHT), systemDark = true).colors.background)
        for (theme in ThemeMode.entries) for (dark in listOf(true, false)) for (palette in Palette.entries) {
            val accent = palette
            val l = build(LookSnapshot(theme = theme, pureBlack = true, palette = palette), dark)
            assertEquals(0xFF000000, l.colors.background, "pure black stays pure black (theme=$theme, systemDark=$dark, accent=$accent)")
        }
    }

    @Test fun thePaletteDrivesTheGradientAndTheAccent() {
        for (p in Palette.entries) {
            val l = build(LookSnapshot(palette = p, theme = ThemeMode.DARK))
            assertEquals(Palettes.stops(p), l.accent.wake); assertEquals(3, l.accent.wake.size)
        }
        assertNotEquals(build(LookSnapshot(palette = Palette.VELA)).accent.wake, build(LookSnapshot(palette = Palette.EMBER)).accent.wake)
    }

    @Test fun shapesIconsAndTextComeThroughAsTokens() {
        val l = build(LookSnapshot(iconStyle = IconStyle.LINE, iconWeight = IconWeight.BOLD, iconRounded = false, textScalePercent = 130f, font = FontChoice.SERIF, density = DensityChoice.ROOMY))
        assertEquals(IconTokens(IconStyle.LINE, IconWeight.BOLD, false), l.icons)
        assertEquals(1.3f, l.typography.scale); assertEquals("serif", l.typography.fontFamily); assertEquals(Density.SPACIOUS, l.density)
    }

    @Test fun everyAppearanceSettingHasALabelADefaultAndKeywordsAndLooksOnlyTouchLookKeys() {
        for (s in LookSettings.all) { assertTrue(s.label.isNotBlank() && s.keywords.isNotEmpty(), s.key); assertTrue(LookKeys.isLookKey(s.key), "${s.key} must start with look.") }
        assertTrue(LookSettings.all.all { it in Settings.all }, "registered in the schema (settings UI, search and backup read it)")
        assertFalse(LookKeys.isLookKey("account.tierOrder")); assertFalse(LookKeys.isLookKey("downloads.folder")); assertFalse(LookKeys.isLookKey("modules.sponsorblock.enabled"))
    }

    /** Lint-style guard: styling comes from tokens. Raw colours, corner radii and font sizes outside the theme package fail the build. */
    @Test fun noScreenSpellsARawColourRadiusOrFontSize() {
        val root = File("src/commonMain/kotlin/com/greenaltambe/vela/ui")
        assertTrue(root.isDirectory, "run from the :shared module (${File(".").absolutePath})")
        val allowed = setOf("look", "debug")                     // the token definitions themselves, and the debug overlay
        val allowedFiles = setOf("VelaTheme.kt")
        val banned = listOf(Regex("""Color\(0x"""), Regex("""RoundedCornerShape\(\s*\d"""), Regex("""CircleShape"""), Regex("""fontSize\s*=\s*\d"""), Regex("""\d\.sp\b"""), Regex("""Color\.(White|Black|Red|Green|Blue|Gray|Yellow)\b"""))
        val offenders = root.walkTopDown().filter { it.extension == "kt" && it.name !in allowedFiles && it.relativeTo(root).invariantSeparatorsPath.substringBefore('/') !in allowed }
            .flatMap { f -> f.readLines().withIndex().filter { (_, line) -> banned.any { it.containsMatchIn(line) } }.map { "${f.relativeTo(root)}:${it.index + 1}: ${it.value.trim().take(90)}" } }.toList()
        assertTrue(offenders.isEmpty(), "raw styling outside the theme:\n" + offenders.joinToString("\n"))
    }
}
