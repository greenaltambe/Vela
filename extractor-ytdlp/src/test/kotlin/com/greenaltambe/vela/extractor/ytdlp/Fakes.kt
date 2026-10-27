package com.greenaltambe.vela.extractor.ytdlp

import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

object Fixtures {
    fun text(name: String): String =
        checkNotNull(Fixtures::class.java.getResource("/fixtures/$name")) { "missing fixture $name" }.readText()
}

/** Answers every command via [responder] and remembers what it was asked. */
class FakeRunner(private val responder: (List<String>) -> ProcessOutput) : ProcessRunner {
    val commands = mutableListOf<List<String>>()

    override suspend fun run(command: List<String>, timeout: Duration, maxStdoutBytes: Int): ProcessOutput {
        commands += command
        return responder(command)
    }

    companion object {
        fun ok(stdout: String) = FakeRunner { ProcessOutput(0, stdout, "") }
        fun failing(exit: Int, stderr: String) = FakeRunner { ProcessOutput(exit, "", stderr) }
    }
}

class FakeClock(var current: Instant = Instant.fromEpochSeconds(1_000_000)) : Clock {
    override fun now() = current
}

/**
 * A process that exits by itself with [exitCode] once its output is read, or, when [hang] is set, never does until
 * [destroyForcibly] is called (which then ends it with 137, like a SIGKILL).
 */
class FakeProcess(
    stdout: ByteArray = ByteArray(0),
    stderr: ByteArray = ByteArray(0),
    private val exitCode: Int = 0,
    private val hang: Boolean = false,
) : RunningProcess {
    val destroyed = AtomicBoolean(false)
    val waiting = CountDownLatch(1)
    private val finished = CountDownLatch(if (hang) 1 else 0)

    private fun stream(bytes: ByteArray) = object : InputStream() {
        private var i = 0
        override fun read(): Int {
            if (i < bytes.size) return bytes[i++].toInt() and 0xff
            if (hang) finished.await() // like a pipe whose writer is still alive, until the process is killed
            return -1
        }
    }

    override val stdout: InputStream = stream(stdout)
    override val stderr: InputStream = stream(stderr)

    override fun waitFor(): Int {
        waiting.countDown()
        finished.await()
        return if (destroyed.get()) 137 else exitCode
    }

    override fun isAlive() = finished.count > 0
    override fun destroyForcibly() { destroyed.set(true); finished.countDown() }

    fun awaitWaiting() = check(waiting.await(5, TimeUnit.SECONDS)) { "runner never waited on the process" }
}
