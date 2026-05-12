package ru.ozero.enginewarp

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class WarpPreflightTest {

    @Test
    fun `resolveTarget использует peerEndpoint IP когда provider возвращает валидный endpoint`() {
        val preflight = WarpPreflight(peerEndpointProvider = { "203.0.113.5:4500" })
        val (host, port) = preflight.resolveTarget()
        assertEquals("203.0.113.5", host)
        assertEquals(443, port)
    }

    @Test
    fun `resolveTarget использует 1_1_1_1 fallback когда provider null`() {
        val preflight = WarpPreflight(peerEndpointProvider = { null })
        val (host, port) = preflight.resolveTarget()
        assertEquals("1.1.1.1", host)
        assertEquals(443, port)
    }

    @Test
    fun `resolveTarget использует fallback когда endpoint пустая строка`() {
        val preflight = WarpPreflight(peerEndpointProvider = { "" })
        val (host, _) = preflight.resolveTarget()
        assertEquals("1.1.1.1", host)
    }

    @Test
    fun `resolveTarget использует fallback когда endpoint только пробелы`() {
        val preflight = WarpPreflight(peerEndpointProvider = { "   " })
        val (host, _) = preflight.resolveTarget()
        assertEquals("1.1.1.1", host)
    }

    @Test
    fun `resolveTarget использует fallback когда host - DNS имя`() {
        val preflight = WarpPreflight(peerEndpointProvider = { "engage.cloudflareclient.com:4500" })
        val (host, _) = preflight.resolveTarget()
        assertEquals("1.1.1.1", host, "DNS-имя ломает probe на DNS-блоке — должен сработать fallback")
    }

    @Test
    fun `resolveTarget использует fallback когда endpoint без порта`() {
        val preflight = WarpPreflight(peerEndpointProvider = { "203.0.113.5" })
        val (host, _) = preflight.resolveTarget()
        assertEquals("1.1.1.1", host)
    }

    @Test
    fun `resolveTarget использует fallback для IPv6 адреса`() {
        val preflight = WarpPreflight(peerEndpointProvider = { "[2606:4700:d0::a29f:c001]:4500" })
        val (host, _) = preflight.resolveTarget()
        assertEquals("1.1.1.1", host, "IPv6 fallback — текущий isPlainIp поддерживает только IPv4")
    }
}
