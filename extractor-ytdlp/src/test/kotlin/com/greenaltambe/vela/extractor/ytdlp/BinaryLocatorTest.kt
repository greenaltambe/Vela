package com.greenaltambe.vela.extractor.ytdlp

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class BinaryLocatorTest {
    private fun locator(path: String, installed: Set<String>, home: String = "/home/u") =
        BinaryLocator(pathEnv = { path }, home = home, isExecutable = { it in installed }, listDirs = { emptyList() })

    @Test fun pathBeatsCommonLocations() {
        val l = locator("/usr/bin:/bin", setOf("/usr/bin/node", "/home/u/.local/bin/node"))
        assertEquals(FoundBinary("node", "/usr/bin/node", "PATH"), l.locate("node"))
    }

    /** A launcher's PATH has no ~/.local/bin; the pip-installed yt-dlp must still be found there. */
    @Test fun shortLauncherPathStillFindsHomeLocalBin() {
        val found = locator("/usr/bin:/bin", setOf("/home/u/.local/bin/yt-dlp")).locate("yt-dlp")
        assertEquals("/home/u/.local/bin/yt-dlp", found.path)
        assertEquals("common location", found.source)
    }

    @Test fun configuredPathIsUsedOrReportedBroken() {
        val l = locator("/usr/bin", setOf("/opt/yt/yt-dlp", "/usr/bin/yt-dlp"))
        assertEquals("setting", l.locate("yt-dlp", "/opt/yt/yt-dlp").source)
        val broken = l.locate("yt-dlp", "/opt/missing/yt-dlp")
        assertNull(broken.path)
        assertTrue("/opt/missing/yt-dlp" in broken.source, "does not silently fall back: ${broken.source}")
    }

    @Test fun missingSaysWhereItLooked() {
        val f = locator("/usr/bin", emptySet()).locate("node")
        assertFalse(f.found)
        assertTrue("not found" in f.description)
    }

    @Test fun locateAllListsEveryCopyInOrderWithoutDuplicates() {
        val l = locator("/usr/bin:/usr/bin", setOf("/usr/bin/yt-dlp", "/home/u/.local/bin/yt-dlp"))
        assertEquals(listOf("/usr/bin/yt-dlp", "/home/u/.local/bin/yt-dlp"), l.locateAll("yt-dlp").map { it.path })
    }

    @Test fun newestYtDlpWinsWhateverThePathOrder() = runTest {
        val versions = mapOf("/usr/bin/yt-dlp" to "2026.08.19", "/home/u/.local/bin/yt-dlp" to "2026.10.26")
        val runner = FakeRunner { ProcessOutput(0, versions.getValue(it.first()) + "\n", "") }
        val cands = listOf("/usr/bin/yt-dlp", "/home/u/.local/bin/yt-dlp").map { FoundBinary("yt-dlp", it, "PATH") }
        assertEquals("/home/u/.local/bin/yt-dlp", chooseYtDlp(runner, cands).first?.binary?.path)
        assertEquals("/home/u/.local/bin/yt-dlp", chooseYtDlp(runner, cands.reversed()).first?.binary?.path)
    }

    @Test fun brokenCandidateIsSkippedAndRevisionOrdersAfterBase() = runTest {
        val runner = FakeRunner { c -> if (c.first() == "/a") ProcessOutput(1, "", "boom") else ProcessOutput(0, if (c.first() == "/b") "2026.10.26" else "2026.10.26.232815", "") }
        val best = chooseYtDlp(runner, listOf("/a", "/b", "/c").map { FoundBinary("yt-dlp", it, "PATH") })
        assertEquals("/c", best.first?.binary?.path)
        assertNull(best.second.first { it.binary.path == "/a" }.version)
        assertNull(chooseYtDlp(FakeRunner.failing(1, "x"), listOf(FoundBinary("yt-dlp", "/a", "PATH"))).first)
    }
}
