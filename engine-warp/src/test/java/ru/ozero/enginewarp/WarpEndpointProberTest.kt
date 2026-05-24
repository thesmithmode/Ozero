package ru.ozero.enginewarp

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WarpEndpointProberTest {

    @Test
    fun `probe returns result for every endpoint`() = runTest {
        val prober = WarpEndpointProber(connectTimeoutMs = 100)
        val endpoints = listOf("1.1.1.1:443", "8.8.8.8:443")
        val results = prober.probe(endpoints)
        assertEquals(endpoints.size, results.size)
    }

    @Test
    fun `probe unreachable endpoint returns MAX_VALUE`() = runTest {
        val prober = WarpEndpointProber(connectTimeoutMs = 50)
        val results = prober.probe(listOf("255.255.255.1:9999"))
        assertEquals(Long.MAX_VALUE, results.first().rttMs)
    }

    @Test
    fun `probe sorts by rtt ascending`() = runTest {
        val prober = WarpEndpointProber(connectTimeoutMs = 100)
        val results = prober.probe(listOf("1.1.1.1:443", "255.255.255.1:9999", "8.8.8.8:443"))
        val rtts = results.map { it.rttMs }
        assertEquals(rtts.sorted(), rtts)
    }

    @Test
    fun `probe preserves all endpoints even all unreachable`() = runTest {
        val prober = WarpEndpointProber(connectTimeoutMs = 50)
        val endpoints = listOf("255.255.255.1:9999", "255.255.255.2:9999")
        val results = prober.probe(endpoints)
        assertEquals(2, results.size)
    }

    @Test
    fun `probe empty list returns empty`() = runTest {
        val prober = WarpEndpointProber(connectTimeoutMs = 50)
        val results = prober.probe(emptyList())
        assertTrue(results.isEmpty())
    }
}
