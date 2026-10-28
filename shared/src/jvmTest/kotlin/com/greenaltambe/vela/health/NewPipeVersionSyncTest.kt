package com.greenaltambe.vela.health

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** The version recorded in code (for the staleness warning) must be the one the build actually uses. */
class NewPipeVersionSyncTest {
    @Test fun codeMatchesTheVersionCatalog() {
        val catalog = generateSequence(File("").absoluteFile) { it.parentFile }.map { File(it, "gradle/libs.versions.toml") }.firstOrNull { it.isFile } ?: return
        val declared = Regex("^newpipe-extractor = \"(v[0-9.]+)\"", RegexOption.MULTILINE).find(catalog.readText())!!.groupValues[1]
        assertEquals(declared, NewPipeVersion.VERSION)
    }
}
