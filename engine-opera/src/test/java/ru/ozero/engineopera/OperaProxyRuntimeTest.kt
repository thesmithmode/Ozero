package ru.ozero.engineopera

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.ProbeResult
import ru.ozero.enginescore.StartResult
import java.io.IOException

class OperaProxyRuntimeTest {
    private class FakeProcess : OperaProxyProcess {
        override var alive = true
        override fun stop() {
            alive = false
        }
    }

    @Test
    fun `start waits for routed probe and stop cleans up`() = runTest {
        val process = FakeProcess()
        var calls = 0
        val runtime = OperaProxyRuntime(
            launcher = OperaProxyLauncher { _, _ -> process },
            routedProbe = {
                calls++
                if (calls == 1) throw IOException()
                42L
            },
            allocatePort = { 12345 },
        )
        assertEquals(StartResult.Success(12345), runtime.start(OperaProxyConfig()))
        assertEquals(2, calls)
        assertEquals(ProbeResult.Success(42L), runtime.probe())
        runtime.stop()
        runtime.stop()
        assertFalse(process.alive)
        assertTrue(runtime.probe() is ProbeResult.Failure)
    }

    @Test
    fun `exited process cannot report readiness`() = runTest {
        val process = FakeProcess().apply { alive = false }
        val runtime = OperaProxyRuntime(
            launcher = OperaProxyLauncher { _, _ -> process },
            routedProbe = { error("must not probe dead process") },
            allocatePort = { 12345 },
        )
        assertTrue(runtime.start(OperaProxyConfig()) is StartResult.Failure)
        assertTrue(runtime.probe() is ProbeResult.Failure)
    }

    @Test
    fun `unreachable route times out and kills process`() = runTest {
        val process = FakeProcess()
        val runtime = OperaProxyRuntime(
            launcher = OperaProxyLauncher { _, _ -> process },
            routedProbe = { throw IOException() },
            allocatePort = { 12345 },
            timeoutMs = 100,
            retryDelayMs = 10,
        )
        assertEquals(
            StartResult.Failure("opera readiness timeout or process stopped"),
            runtime.start(OperaProxyConfig()),
        )
        assertFalse(process.alive)
    }

    @Test
    fun `cancellation kills starting process`() = runTest {
        val process = FakeProcess()
        val entered = CompletableDeferred<Unit>()
        val runtime = OperaProxyRuntime(
            launcher = OperaProxyLauncher { _, _ -> process },
            routedProbe = {
                entered.complete(Unit)
                awaitCancellation()
            },
            allocatePort = { 12345 },
        )
        val job = async { runtime.start(OperaProxyConfig()) }
        entered.await()
        job.cancel()
        assertTrue(runCatching { job.await() }.exceptionOrNull() is CancellationException)
        assertFalse(process.alive)
        assertTrue(job.isCancelled)
    }

    @Test
    fun `stop does not wait for an in-flight startup probe`() = runTest {
        val process = FakeProcess()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val runtime = OperaProxyRuntime(
            launcher = OperaProxyLauncher { _, _ -> process },
            routedProbe = {
                entered.complete(Unit)
                release.await()
                20L
            },
            allocatePort = { 12345 },
        )
        val starting = async { runtime.start(OperaProxyConfig()) }
        entered.await()
        runtime.stop()
        assertFalse(process.alive)
        release.complete(Unit)
        assertTrue(starting.await() is StartResult.Failure)
    }

    @Test
    fun `superseded startup cannot stop the replacement process`() = runTest {
        val old = FakeProcess()
        val replacement = FakeProcess()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var launches = 0
        var nextPort = 12344
        val runtime = OperaProxyRuntime(
            launcher = OperaProxyLauncher { _, _ ->
                if (launches++ == 0) old else replacement
            },
            routedProbe = { port ->
                if (port == 12345) {
                    entered.complete(Unit)
                    release.await()
                }
                20L
            },
            allocatePort = { ++nextPort },
        )
        val starting = async { runtime.start(OperaProxyConfig()) }
        entered.await()
        assertEquals(StartResult.Success(12346), runtime.start(OperaProxyConfig()))
        release.complete(Unit)
        assertTrue(starting.await() is StartResult.Failure)
        assertFalse(old.alive)
        assertTrue(replacement.alive)
        runtime.stop()
    }

    @Test
    fun `stopping during health check invalidates its successful response`() = runTest {
        val process = FakeProcess()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val runtime = OperaProxyRuntime(
            launcher = OperaProxyLauncher { _, _ -> process },
            routedProbe = {
                if (++calls > 1) {
                    entered.complete(Unit)
                    release.await()
                }
                20L
            },
            allocatePort = { 12345 },
        )
        assertEquals(StartResult.Success(12345), runtime.start(OperaProxyConfig()))
        val probing = async { runtime.probe() }
        entered.await()
        runtime.stop()
        release.complete(Unit)
        assertTrue(probing.await() is ProbeResult.Failure)
    }

    @Test
    fun `launch failure is contained`() = runTest {
        val runtime = OperaProxyRuntime(
            launcher = OperaProxyLauncher { _, _ -> throw IOException("private detail") },
            allocatePort = { 12345 },
        )
        assertEquals(StartResult.Failure("opera startup failed"), runtime.start(OperaProxyConfig()))
    }
}
