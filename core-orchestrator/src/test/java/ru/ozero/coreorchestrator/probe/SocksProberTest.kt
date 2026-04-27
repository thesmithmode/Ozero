package ru.ozero.coreorchestrator.probe

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.coreapi.ProbeResult
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SocksProberTest {
    private val targets = listOf(
        ProbeTarget("youtube.com", "https://youtube.com/generate_204", "HEAD"),
        ProbeTarget("discord.com", "https://discord.com/", "HEAD"),
        ProbeTarget("github.com", "https://api.github.com/", "HEAD"),
    )

    @Test
    fun returnsFirstSuccessfulTarget() = runTest {
        val seen = mutableListOf<String>()
        val connector = SocksProber.HttpConnector { target, _ ->
            seen += target.host
            42L
        }
        val prober = SocksProber(targets = targets, connector = connector)
        val result = prober.probe(socksPort = 10808)

        assertTrue(result is ProbeResult.Success)
        assertEquals(42L, (result as ProbeResult.Success).latencyMs)
                assertEquals(listOf("youtube.com"), seen)
    }

    @Test
    fun fallsBackOnFailureToNextTarget() = runTest {
        val seen = mutableListOf<String>()
        val connector = SocksProber.HttpConnector { target, _ ->
            seen += target.host
            if (target.host == "youtube.com") error("HTTP 503")
            123L
        }
        val prober = SocksProber(targets = targets, connector = connector)
        val result = prober.probe(socksPort = 10808)

        assertTrue(result is ProbeResult.Success)
        assertEquals(listOf("youtube.com", "discord.com"), seen)
    }

    @Test
    fun failsWhenAllTargetsFail() = runTest {
        val connector = SocksProber.HttpConnector { _, _ -> error("network down") }
        val prober = SocksProber(targets = targets, connector = connector)
        val result = prober.probe(socksPort = 10808)

        assertTrue(result is ProbeResult.Failure)
        assertTrue((result as ProbeResult.Failure).reason.contains("github.com"), "reason: ${result.reason}")
    }

    @Test
    fun timeoutMarksAsTimeoutAndContinues() = runTest {
        val seen = mutableListOf<String>()
        val connector = SocksProber.HttpConnector { target, _ ->
            seen += target.host
            if (target.host == "youtube.com") {
                delay(10_000) 
                0L
            } else {
                50L
            }
        }
        val prober = SocksProber(targets = targets, timeoutMs = 1_000L, connector = connector)
        val result = prober.probe(socksPort = 10808)

        assertTrue(result is ProbeResult.Success)
        assertEquals(50L, (result as ProbeResult.Success).latencyMs)
        assertTrue("discord.com" in seen)
    }

    @Test
    fun rejectsInvalidPort() = runTest {
        val prober = SocksProber(connector = { _, _ -> 0L })
        runCatching { prober.probe(socksPort = 0) }.also {
            assertTrue(it.isFailure, "должно бросить на port=0")
        }
        runCatching { prober.probe(socksPort = 65536) }.also {
            assertTrue(it.isFailure)
        }
    }

    @Test
    fun emptyTargetsImmediateFailure() = runTest {
        val prober = SocksProber(targets = emptyList(), connector = { _, _ -> 0L })
        val result = prober.probe(socksPort = 10808)
        assertTrue(result is ProbeResult.Failure)
    }

    @Test
    fun passesPortToConnector() = runTest {
        var observedPort = -1
        val connector = SocksProber.HttpConnector { _, port ->
            observedPort = port
            10L
        }
        val prober = SocksProber(targets = targets.take(1), connector = connector)
        prober.probe(socksPort = 10999)
        assertEquals(10999, observedPort)
    }
}
