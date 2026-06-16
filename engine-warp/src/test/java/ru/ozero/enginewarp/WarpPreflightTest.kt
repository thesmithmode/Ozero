package ru.ozero.enginewarp

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EnginePreflight
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WarpPreflightTest {

    @Test
    fun `probe всегда Ok независимо от endpoint`() = runTest {
        assertIs<EnginePreflight.Result.Ok>(
            WarpPreflight(peerEndpointProvider = { "203.0.113.5:4500" })
                .probe(protector = { true }),
        )
    }

    @Test
    fun `probe Ok когда provider null`() = runTest {
        assertIs<EnginePreflight.Result.Ok>(
            WarpPreflight(peerEndpointProvider = { null })
                .probe(protector = { true }),
        )
    }

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
    fun `resolveTarget извлекает IPv6 хост из bracketed endpoint`() {
        val preflight = WarpPreflight(peerEndpointProvider = { "[2606:4700:d0::a29f:c001]:4500" })
        val (host, port) = preflight.resolveTarget()
        assertEquals("2606:4700:d0::a29f:c001", host)
        assertEquals(443, port)
    }

    @Test
    fun `resolveTarget использует fallback когда IPv6 без скобок`() {
        val preflight = WarpPreflight(peerEndpointProvider = { "2606:4700:d0::a29f:c001" })
        val (host, _) = preflight.resolveTarget()
        assertEquals("1.1.1.1", host)
    }

    @Test
    fun `resolveTarget использует fallback когда скобки пустые`() {
        val preflight = WarpPreflight(peerEndpointProvider = { "[]:4500" })
        val (host, _) = preflight.resolveTarget()
        assertEquals("1.1.1.1", host)
    }

    @Test
    fun `resolveTarget uses fallback for bracketed non IPv6 host`() {
        val preflight = WarpPreflight(peerEndpointProvider = { "[engage.cloudflareclient.com]:4500" })

        val (host, port) = preflight.resolveTarget()

        assertEquals("1.1.1.1", host)
        assertEquals(443, port)
    }

    @Test
    fun `resolveTarget uses fallback for bracketed endpoint without closing bracket`() {
        val preflight = WarpPreflight(peerEndpointProvider = { "[2606:4700:d0::a29f:c001:4500" })

        val (host, _) = preflight.resolveTarget()

        assertEquals("1.1.1.1", host)
    }

    @Test
    fun `resolveTarget uses fallback for IPv4 endpoint with empty host`() {
        val preflight = WarpPreflight(peerEndpointProvider = { ":4500" })

        val (host, _) = preflight.resolveTarget()

        assertEquals("1.1.1.1", host)
    }

    @Test
    fun `resolveTarget uses fallback for IPv4 host with invalid character`() {
        val preflight = WarpPreflight(peerEndpointProvider = { "203.0.113.a:4500" })

        val (host, _) = preflight.resolveTarget()

        assertEquals("1.1.1.1", host)
    }

    @Test
    fun `resolveTarget uses fallback for bracketed IPv6 with invalid character`() {
        val preflight = WarpPreflight(peerEndpointProvider = { "[2606:4700::zzzz]:4500" })

        val (host, _) = preflight.resolveTarget()

        assertEquals("1.1.1.1", host)
    }

    @Test
    fun `resolveTarget accepts uppercase IPv6 hex characters`() {
        val preflight = WarpPreflight(peerEndpointProvider = { "[2606:4700:D0::A29F:C001]:4500" })

        val (host, port) = preflight.resolveTarget()

        assertEquals("2606:4700:D0::A29F:C001", host)
        assertEquals(443, port)
    }

    @Test
    fun `resolveTarget trims provider endpoint before parsing`() {
        val preflight = WarpPreflight(peerEndpointProvider = { "  203.0.113.9:4500  " })

        val (host, port) = preflight.resolveTarget()

        assertEquals("203.0.113.9", host)
        assertEquals(443, port)
    }
}
