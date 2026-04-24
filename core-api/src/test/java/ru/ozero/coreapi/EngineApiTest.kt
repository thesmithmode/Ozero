package ru.ozero.coreapi

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EngineApiTest {
    @Test
    fun engineIdContainsAllValues() {
        val entries = EngineId.entries
        assertEquals(5, entries.size)
        assertTrue(entries.contains(EngineId.BYEDPI))
        assertTrue(entries.contains(EngineId.XRAY))
        assertTrue(entries.contains(EngineId.AMNEZIA))
        assertTrue(entries.contains(EngineId.TOR))
        assertTrue(entries.contains(EngineId.NAIVE))
    }

    @Test
    fun engineCapabilitiesEqualityAndCopy() {
        val original = EngineCapabilities(
            supportsTcp = true,
            supportsUdp = false,
            supportsDoH = true,
            localOnly = false,
            requiresServer = true
        )
        val copy = original.copy()
        assertEquals(original, copy)

        val modified = original.copy(supportsTcp = false)
        assertFalse(modified.supportsTcp)
        assertFalse(modified.supportsUdp)
    }

    @Test
    fun byeDpiDefaultArgs() {
        val config = EngineConfig.ByeDpi()
        assertEquals("-Ku -a1 -An -o1 -At,r,s -d1", config.args)
        assertEquals(1080, config.socksPort)
    }

    @Test
    fun byeDpiCustomArgs() {
        val customArgs = "-custom"
        val config = EngineConfig.ByeDpi(args = customArgs, socksPort = 2080)
        assertEquals(customArgs, config.args)
        assertEquals(2080, config.socksPort)
    }

    @Test
    fun startResultSuccess() {
        val result = StartResult.Success(socksPort = 1080)
        assertEquals(1080, result.socksPort)
        assertTrue(result is StartResult.Success)
    }

    @Test
    fun startResultFailure() {
        val cause = Exception("Test error")
        val result = StartResult.Failure(reason = "Connection failed", cause = cause)
        assertEquals("Connection failed", result.reason)
        assertNotNull(result.cause)
        assertEquals("Test error", result.cause.message)
    }

    @Test
    fun startResultFailureWithoutCause() {
        val result = StartResult.Failure(reason = "Timeout")
        assertEquals("Timeout", result.reason)
        assertNull(result.cause)
    }

    @Test
    fun probeResultSuccess() {
        val result = ProbeResult.Success(latencyMs = 150L)
        assertEquals(150L, result.latencyMs)
        assertTrue(result is ProbeResult.Success)
    }

    @Test
    fun probeResultFailure() {
        val result = ProbeResult.Failure(reason = "Network unreachable")
        assertEquals("Network unreachable", result.reason)
        assertTrue(result is ProbeResult.Failure)
    }

    @Test
    fun engineStatsCopyAndEquality() {
        val original = EngineStats(
            bytesIn = 1000L,
            bytesOut = 2000L,
            connectedSince = 1704067200000L,
            activeConnections = 5
        )
        val copy = original.copy()
        assertEquals(original, copy)

        val modified = original.copy(activeConnections = 10)
        assertEquals(10, modified.activeConnections)
        assertEquals(1000L, modified.bytesIn)
    }

    @Test
    fun engineStatsDefaultValues() {
        val stats = EngineStats()
        assertEquals(0L, stats.bytesIn)
        assertEquals(0L, stats.bytesOut)
        assertEquals(0L, stats.connectedSince)
        assertEquals(0, stats.activeConnections)
    }

    @Test
    fun xrayConfigDefaults() {
        val config = EngineConfig.Xray(configJson = "{}")
        assertEquals("{}", config.configJson)
        assertEquals(10808, config.socksPort)
    }

    @Test
    fun torConfigDefaults() {
        val config = EngineConfig.Tor()
        assertEquals(emptyList(), config.bridges)
        assertEquals(9050, config.socksPort)
    }

    @Test
    fun naiveConfigRequired() {
        val config = EngineConfig.Naive(proxyUrl = "http://proxy.example.com")
        assertEquals("http://proxy.example.com", config.proxyUrl)
        assertEquals(1080, config.socksPort)
    }
}
