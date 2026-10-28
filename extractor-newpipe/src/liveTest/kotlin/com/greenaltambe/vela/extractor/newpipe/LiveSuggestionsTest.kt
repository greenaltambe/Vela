package com.greenaltambe.vela.extractor.newpipe

import com.greenaltambe.vela.extractor.ExtractorResult
import com.greenaltambe.vela.extractor.chain.PacedExtractor
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class LiveSuggestionsTest {
    @Test fun suggestionsComeBackThroughThePacingWrapperToo() = runBlocking<Unit> {
        val r = PacedExtractor(NewPipeExtractor()).suggestions("lofi")
        println("SUGGEST $r")
        assertTrue(r is ExtractorResult.Success && r.value.isNotEmpty(), "got $r")
    }
}
