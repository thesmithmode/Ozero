package ru.ozero.coreapi

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class UpstreamTest {

    @Test
    fun none_isObjectSingleton() {
        val a: Upstream = Upstream.None
        val b: Upstream = Upstream.None
        assertTrue(a === b, "Upstream.None должен быть object singleton — chain orchestrator сравнивает через ===")
    }

    @Test
    fun socks5_holdsHostAndPort() {
        val u = Upstream.Socks5(host = "127.0.0.1", port = 1080)
        assertEquals("127.0.0.1", u.host)
        assertEquals(1080, u.port)
    }

    @Test
    fun socks5_dataClassEquality() {
        val a = Upstream.Socks5("127.0.0.1", 1080)
        val b = Upstream.Socks5("127.0.0.1", 1080)
        val c = Upstream.Socks5("127.0.0.1", 1081)
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun socks5_copy() {
        val a = Upstream.Socks5("127.0.0.1", 1080)
        val b = a.copy(port = 2080)
        assertEquals("127.0.0.1", b.host)
        assertEquals(2080, b.port)
    }

    @Test
    fun http_holdsHostAndPort() {
        val u = Upstream.Http(host = "proxy.example", port = 8080)
        assertEquals("proxy.example", u.host)
        assertEquals(8080, u.port)
    }

    @Test
    fun http_dataClassEquality() {
        val a = Upstream.Http("proxy.example", 8080)
        val b = Upstream.Http("proxy.example", 8080)
        assertEquals(a, b)
    }

    @Test
    fun sealedExhaustive() {
        val values: List<Upstream> = listOf(
            Upstream.None,
            Upstream.Socks5("h", 1),
            Upstream.Http("h", 2),
        )
        values.forEach { u ->
            val descr = when (u) {
                Upstream.None -> "none"
                is Upstream.Socks5 -> "socks5/${u.host}:${u.port}"
                is Upstream.Http -> "http/${u.host}:${u.port}"
            }
            assertTrue(descr.isNotEmpty())
        }
    }
}
