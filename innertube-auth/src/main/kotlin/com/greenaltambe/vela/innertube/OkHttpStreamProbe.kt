package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.health.StreamProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Anonymous GET of the first KiB of a stream URL: the canary's "does it actually play" check. No cookies, ever. */
class OkHttpStreamProbe(private val transport: AuthTransport = OkHttpAuthTransport()) : StreamProbe {
    override suspend fun status(url: String, headers: Map<String, String>): Int = withContext(Dispatchers.IO) {
        transport.execute("GET", url, headers.filterKeys { !it.equals("Cookie", true) && !it.equals("Authorization", true) } + ("Range" to "bytes=0-1023"), null).code
    }
}
