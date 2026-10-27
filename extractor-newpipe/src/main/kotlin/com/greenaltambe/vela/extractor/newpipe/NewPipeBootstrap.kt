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
}
