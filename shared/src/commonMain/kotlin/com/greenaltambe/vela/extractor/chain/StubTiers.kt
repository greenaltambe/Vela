package com.greenaltambe.vela.extractor.chain

import com.greenaltambe.vela.extractor.Extractor
import com.greenaltambe.vela.extractor.ExtractorError
import com.greenaltambe.vela.extractor.StubExtractor

/**
 * The four tiers in chain order. Only the ones passed in are real; the rest fail with a clear "not implemented"
 * instead of returning empty data, so a failure of a real tier shows up as a failure rather than as an empty success.
 *
 * [ytDlp] null leaves the yt-dlp tier out entirely (it can't run on this platform or isn't installed).
 */
fun stubTiers(
    anonymous: Extractor = StubExtractor(),
    ytDlp: Extractor? = notImplemented(),
): List<ExtractorTier> = listOfNotNull(
    ExtractorTier(TierId.ANONYMOUS, anonymous),
    ExtractorTier(TierId.AUTHENTICATED, notImplemented()),
    ExtractorTier(TierId.ALT_CLIENT, notImplemented()),
    ytDlp?.let { ExtractorTier(TierId.YT_DLP, it) },
)

private fun notImplemented(): Extractor = StubExtractor(ExtractorError.Unplayable("Tier not implemented yet"))
