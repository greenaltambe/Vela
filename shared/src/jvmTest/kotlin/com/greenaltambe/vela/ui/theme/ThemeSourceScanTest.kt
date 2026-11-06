package com.greenaltambe.vela.ui.theme

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ThemeSourceScanTest {
    /** Lint-style guard: styling comes from the theme. Raw colours, corner radii and font sizes outside the theme package fail the build. */
    @Test fun noScreenSpellsARawColourRadiusOrFontSize() {
        val root = File("src/commonMain/kotlin/com/greenaltambe/vela/ui")
        assertTrue(root.isDirectory, "run from the :shared module (${File(".").absolutePath})")
        // The theme itself, the debug overlay, and ui/look (the icon vectors, motion helpers and the logo, which the next phases shrink).
        val allowed = setOf("theme", "look", "debug")
        val banned = listOf(Regex("""Color\(0x"""), Regex("""RoundedCornerShape\(\s*\d"""), Regex("""fontSize\s*=\s*\d"""), Regex("""\d\.sp\b"""), Regex("""Color\.(White|Black|Red|Green|Blue|Gray|Yellow)\b"""))
        val offenders = root.walkTopDown().filter { it.extension == "kt" && it.relativeTo(root).invariantSeparatorsPath.substringBefore('/') !in allowed }
            .flatMap { f -> f.readLines().withIndex().filter { (_, line) -> banned.any { it.containsMatchIn(line) } }.map { "${f.relativeTo(root)}:${it.index + 1}: ${it.value.trim().take(90)}" } }.toList()
        assertTrue(offenders.isEmpty(), "raw styling outside the theme:\n" + offenders.joinToString("\n"))
    }
}
