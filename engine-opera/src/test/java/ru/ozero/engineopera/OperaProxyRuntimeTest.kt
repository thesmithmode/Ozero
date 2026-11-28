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
        assertEquals(StartResult.Failure("opera process exited before readiness"), runtime.start(OperaProxyConfig()))
        assertTrue(runtime.probe() is ProbeResult.Failure)
    }

    @Test
    fun `native registration or selection failure retries with alternate bootstrap`() = runTest {
        val failed = object : OperaProxyProcess {
            override val alive = false
            override val exitCode = 12
            override fun stop() = Unit
        }
        val replacement = FakeProcess()
        val configs = mutableListOf<OperaProxyConfig>()
        val runtime = OperaProxyRuntime(
            launcher = OperaProxyLauncher { config, _ ->
                configs.add(config)
                if (configs.size == 1) failed else replacement
            },
            routedProbe = { 10L },
            allocatePort = { 12345 },
        )
        try {
            assertEquals(StartResult.Success(12345), runtime.start(OperaProxyConfig()))
            assertEquals(listOf(false, true), configs.map { it.alternateBootstrap })
            assertTrue(replacement.alive)
        } finally {
            runtime.stop()
        }
        assertFalse(replacement.alive)
    }

    @Test
    fun `stalled primary bootstrap retries and stops the first process`() = runTest {
        val primary = FakeProcess()
        val alternate = FakeProcess()
        val configs = mutableListOf<Boolean>()
        var port = 12344
        val runtime = OperaProxyRuntime(
            launcher = OperaProxyLauncher { config, _ ->
                configs.add(config.alternateBootstrap)
                if (config.alternateBootstrap) alternate else primary
            },
            routedProbe = { candidate ->
                if (candidate == 12345) throw IOException()
                20L
            },
            allocatePort = { ++port },
            timeoutMs = 100,
            retryDelayMs = 10,
        )
        try {
            assertEquals(StartResult.Success(12346), runtime.start(OperaProxyConfig()))
            assertEquals(listOf(false, true), configs)
            assertFalse(primary.alive)
            assertTrue(alternate.alive)
        } finally {
            runtime.stop()
        }
        assertFalse(alternate.alive)
    }

    @Test
    fun `unreachable route times out and kills both processes`() = runTest {
        val processes = mutableListOf<FakeProcess>()
        val runtime = OperaProxyRuntime(
            launcher = OperaProxyLauncher { _, _ -> FakeProcess().also { processes.add(it) } },
            routedProbe = { throw IOException() },
            allocatePort = { 12345 },
            timeoutMs = 100,
            retryDelayMs = 10,
        )
        assertEquals(
            StartResult.Failure("opera routed readiness timed out"),
            runtime.start(OperaProxyConfig()),
        )
        assertEquals(2, processes.size)
        assertTrue(processes.none { it.alive })
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
