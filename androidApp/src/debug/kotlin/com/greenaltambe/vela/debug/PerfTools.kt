package com.greenaltambe.vela.debug

import android.os.Looper
import java.io.File

/**
 * Debug-only poor man's profiler: samples the MAIN thread's stack every few milliseconds and aggregates where the time goes, as
 * the first frame of our own code ("com.greenaltambe") on each stack and the leaf frame. The cause of a lag is read from this,
 * not guessed. Started and stopped from the self-test (`--es run sample --ei seconds N`); the report is written to files/sample.txt.
 */
object MainThreadSampler {
    fun run(seconds: Int, out: File, intervalMs: Long = 4) {
        val main = Looper.getMainLooper().thread
        Thread {
            val own = HashMap<String, Int>(); val owner = HashMap<String, Int>(); val chain = HashMap<String, Int>(); val leaf = HashMap<String, Int>(); val compose = HashMap<String, Int>()
            var samples = 0; var busy = 0; val probe = System.getProperty("vela.probe") ?: "LayoutCoordinatesKt"; val probeStacks = HashMap<String, Int>(); val share = linkedMapOf("accessibility/semantics" to 0, "compose measure+layout" to 0, "compose composition" to 0, "draw/render sync" to 0, "coil images" to 0, "our code" to 0)
            val end = System.currentTimeMillis() + seconds * 1000L
            while (System.currentTimeMillis() < end) {
                val st = main.stackTrace
                samples++
                // An idle main thread sits in MessageQueue.nativePollOnce; anything else is work.
                val idle = st.isNotEmpty() && st[0].methodName.contains("nativePollOnce")
                if (!idle && st.isNotEmpty()) {
                    busy++
                    fun has(vararg k: String) = st.any { f -> k.any { f.className.contains(it) || f.methodName.contains(it) } }
                    if (has("AccessibilityDelegate", "Semantics")) share.merge("accessibility/semantics", 1, Int::plus)
                    if (has("MeasureAndLayout", "measureAndLayout", "LayoutNode.measure", "remeasure")) share.merge("compose measure+layout", 1, Int::plus)
                    if (has("Recomposer", "ComposerImpl", "composeContent", "recompose")) share.merge("compose composition", 1, Int::plus)
                    if (has("nSyncAndDrawFrame", "HardwareRenderer", "draw(")) share.merge("draw/render sync", 1, Int::plus)
                    if (has("coil3")) share.merge("coil images", 1, Int::plus)
                    if (st.any { it.className.startsWith("com.greenaltambe") }) share.merge("our code", 1, Int::plus)
                    if (st.any { it.className.contains(probe) }) probeStacks.merge(st.drop(st.indexOfFirst { it.className.contains(probe) }).take(9).joinToString(" < ") { it.className.substringAfterLast('.').take(40) + "." + it.methodName.take(30) }, 1, Int::plus)
                    leaf.merge("${st[0].className.substringAfterLast('.')}.${st[0].methodName}", 1, Int::plus)
                    st.firstOrNull { it.className.startsWith("com.greenaltambe") }?.let { own.merge("${it.className.removePrefix("com.greenaltambe.vela.")}.${it.methodName}:${it.lineNumber}", 1, Int::plus) }
                    // WHO asked for it: the first frame in UI/feed/module code, with the DB/net/core frame it ended in.
                    st.firstOrNull { f -> listOf("ui.", "feed.", "modules.", "playback.", "player.", "downloads.", "auth.", "library.").any { f.className.startsWith("com.greenaltambe.vela.$it") } }?.let { o ->
                        owner.merge("${o.className.removePrefix("com.greenaltambe.vela.")}.${o.methodName}:${o.lineNumber}", 1, Int::plus)
                        val low = st.firstOrNull { it.className.startsWith("com.greenaltambe") && (it.className.contains(".data.") || it.className.contains(".core.") || it.className.contains(".net.")) }
                        chain.merge("${o.className.substringAfterLast('.')}.${o.methodName} -> ${low?.className?.substringAfterLast('.') ?: "-"}.${low?.methodName ?: "-"}", 1, Int::plus)
                    }
                    st.firstOrNull { it.className.startsWith("androidx.compose.foundation.lazy") || it.className.startsWith("coil3") || it.className.startsWith("androidx.compose.ui.layout") || it.className.contains("SubcomposeLayout") }
                        ?.let { compose.merge(it.className.substringAfterLast('.').take(60), 1, Int::plus) }
                }
                Thread.sleep(intervalMs)
            }
            fun top(m: Map<String, Int>, n: Int) = m.entries.sortedByDescending { it.value }.take(n).joinToString("\n") { "  ${it.value * intervalMs} ms  ${it.key}" }
            out.writeText("samples=$samples busy=$busy (~${busy * intervalMs} ms of ${seconds * 1000} ms)\nOUR CODE (first com.greenaltambe frame):\n${top(own, 25)}\nOWNER (first ui/feed/modules frame):\n${top(owner, 20)}\nOWNER -> LOW LEVEL:\n${top(chain, 20)}\nLEAF:\n${top(leaf, 12)}\nSHARE OF BUSY SAMPLES (a sample can count in several):\n${share.entries.joinToString("\n") { "  ${it.value * 100 / busy.coerceAtLeast(1)}%  ${it.key}" }}\nPROBE ($probe) callers:\n${top(probeStacks, 4)}\nFRAMEWORK AREAS:\n${top(compose, 8)}\n")
        }.apply { isDaemon = true; start() }
    }
}
