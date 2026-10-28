package com.greenaltambe.vela.extractor.ytdlp

import com.greenaltambe.vela.extractor.ExtractorResult
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertIs

/** Real download of the official release asset from GitHub, checksum against the published SHA2-256SUMS, real canary. Nothing outside a temp dir is touched. */
class LiveUpdaterTest {
    @Test fun installsTheRealReleaseAndItServesAVideo() = runBlocking<Unit> {
        val dir = Files.createTempDirectory("vela-ytdlp-live").toFile()
        val runner = SystemProcessRunner()
        var canaryMs = 0L
        val u = YtDlpUpdater(dir, runner = runner, healthCheck = { bin ->
            val args = listOfNotNull(SystemPathLookup.find("node")?.let { "--js-runtimes" to "node:$it" }).flatMap { it.toList() }
            var ok = false
            canaryMs = measureTimeMillis { ok = YtDlpExtractor(runner, { bin }, { args }).streams("dQw4w9WgXcQ") is ExtractorResult.Success }
            ok
        })
        var result: UpdateResult? = null
        val ms = measureTimeMillis { result = u.update("2026.10.26") }
        println("UPDATER result=${result!!::class.simpleName} total=${ms}ms canary=${canaryMs}ms size=${u.managed.length()} status=${u.status.value}")
        assertIs<UpdateResult.Updated>(result)
    }
}
