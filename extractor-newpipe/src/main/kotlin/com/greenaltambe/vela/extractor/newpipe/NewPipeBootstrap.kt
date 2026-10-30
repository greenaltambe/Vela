package com.greenaltambe.vela.extractor.newpipe

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

/** NewPipe keeps its downloader in a static, so it is initialised exactly once per process; the first caller's wins. */
internal object NewPipeBootstrap {
    private var initialised = false

    @Synchronized
    fun ensureInitialised(downloader: () -> Downloader) {
        if (initialised) return
        NewPipe.init(downloader(), Localization("en", "US"), ContentCountry("US"))
        initialised = true
    }

    /** Sets the content country (trending, recommendations) when it differs from the current one; the language stays English. */
    @Synchronized
    fun applyRegion(code: String) {
        val cc = code.uppercase().takeIf { it.length == 2 } ?: "US"
        if (NewPipe.getPreferredContentCountry().countryCode == cc) return
        NewPipe.setupLocalization(Localization("en", cc), ContentCountry(cc))
    }
}
