package com.greenaltambe.vela.extractor.ytdlp

import kotlinx.coroutines.*
import org.junit.Assume.assumeFalse
import java.util.concurrent.TimeUnit
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SystemProcessRunnerTest {
    private fun runnerFor(process: FakeProcess, maxStderr: Int = 1024) =
        SystemProcessRunner(start = { process }, maxStderrBytes = maxStderr)

    @Test fun collectsOutputAndExitCode() = runBlocking {
        val p = FakeProcess("out".toByteArray(), "err".toByteArray(), exitCode = 3)
        val result = runnerFor(p).run(listOf("x"), 5.seconds, 1024)
        assertEquals(3, result.exitCode)
        assertEquals("out", result.stdout)
        assertEquals("err", result.stderr)
    }

    @Test fun cancellationKillsTheProcess() = runBlocking {
        val p = FakeProcess(hang = true)
        val job = launch(Dispatchers.Default) { runnerFor(p).run(listOf("x"), 30.seconds, 1024) }
        p.awaitWaiting()
        assertFalse(p.destroyed.get())

        job.cancelAndJoin()
        assertTrue(p.destroyed.get(), "the process must be destroyed when the caller is cancelled")
    }

    @Test fun timeoutKillsTheProcessAndReportsIt() = runBlocking {
        val p = FakeProcess(hang = true)
        val e = assertFailsWith<ProcessTimeoutException> { runnerFor(p).run(listOf("x"), 150.milliseconds, 1024) }
        assertEquals(150.milliseconds, e.timeout)
        assertTrue(p.destroyed.get())
    }

    @Test fun stdoutOverTheCapKillsTheProcessAndFails() = runBlocking {
        val p = FakeProcess(stdout = ByteArray(5000) { 'a'.code.toByte() }, hang = true)
        val e = assertFailsWith<OutputTooLargeException> { runnerFor(p).run(listOf("x"), 10.seconds, 1000) }
        assertEquals(1000, e.limitBytes)
        assertTrue(p.destroyed.get())
    }

    @Test fun stdoutExactlyAtTheCapIsFine() = runBlocking {
        val p = FakeProcess(stdout = ByteArray(1000) { 'a'.code.toByte() })
        assertEquals(1000, runnerFor(p).run(listOf("x"), 5.seconds, 1000).stdout.length)
    }

    @Test fun stderrIsTruncatedNotFatal() = runBlocking {
        val p = FakeProcess(stderr = ByteArray(10_000) { 'e'.code.toByte() }, exitCode = 1)
        val result = runnerFor(p, maxStderr = 100).run(listOf("x"), 5.seconds, 1000)
        assertEquals(100, result.stderr.length)
        assertEquals(1, result.exitCode)
    }

    @Test fun startFailureIsReported() = runBlocking {
        val runner = SystemProcessRunner(start = { throw ProcessStartException("nope") })
        assertFailsWith<ProcessStartException> { runner.run(listOf("x"), 5.seconds, 10) }
        Unit
    }

    // ---- real processes (unix tools) ----

    private fun requireUnix() = assumeFalse("needs unix tools", System.getProperty("os.name").lowercase().contains("win"))

    @Test fun realProcessOutputAndExitCode() = runBlocking {
        requireUnix()
        val r = SystemProcessRunner().run(listOf("sh", "-c", "printf out; printf err >&2; exit 3"), 10.seconds, 1024)
        assertEquals(3, r.exitCode)
        assertEquals("out", r.stdout)
        assertEquals("err", r.stderr)
    }

    @Test fun missingBinaryIsAStartFailure() = runBlocking {
        assertFailsWith<ProcessStartException> { SystemProcessRunner().run(listOf("/nonexistent/vela-no-such-binary"), 5.seconds, 10) }
        Unit
    }

    @Test fun cancelledRealProcessIsReallyKilled() = runBlocking {
        requireUnix()
        val marker = "30.${System.nanoTime() % 100000}" // unique argument so only our sleep matches
        fun ours() = ProcessHandle.allProcesses().filter { h -> h.info().arguments().map { it.contains(marker) }.orElse(false) }.toList()

        val job = launch(Dispatchers.Default) { SystemProcessRunner().run(listOf("sleep", marker), 60.seconds, 1024) }
        withTimeout(5.seconds) { while (ours().isEmpty()) delay(20) }
        job.cancelAndJoin()
        withTimeout(5.seconds) { ours().forEach { it.onExit().get(5, TimeUnit.SECONDS) } }
        assertTrue(ours().isEmpty(), "sleep must be gone after cancellation")
    }
}
