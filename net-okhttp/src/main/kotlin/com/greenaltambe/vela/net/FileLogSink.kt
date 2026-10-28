package com.greenaltambe.vela.net

import com.greenaltambe.vela.auth.Redaction
import com.greenaltambe.vela.extractor.chain.LogLineSink
import java.io.File

/**
 * A bounded rotating log: `<name>` grows to [maxBytes], then becomes `<name>.1` (replacing the previous one) and a new file starts,
 * so the total never exceeds about 2 x [maxBytes]. Every line is redacted again on the way in. Failures to write are swallowed:
 * logging must never break the app.
 */
class FileLogSink(private val file: File, private val maxBytes: Long = 512 * 1024) : LogLineSink {
    private val lock = Any()

    override fun write(line: String) = synchronized(lock) {
        runCatching {
            file.parentFile?.mkdirs()
            if (file.length() >= maxBytes) { val old = File(file.path + ".1"); old.delete(); file.renameTo(old) }
            file.appendText(Redaction.redact(line).replace('\n', ' ') + "\n")
        }
        Unit
    }

    /** The older file then the current one, for Export. */
    fun readAll(): String = synchronized(lock) { listOf(File(file.path + ".1"), file).filter { it.isFile }.joinToString("") { it.readText() } }
}
