package ru.ozero.singboxfmt

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UriCompatTest {

    @Test
    fun `parse exposes host port user info fragment and decoded query parameters`() {
        val uri = UriCompat.parse(
            "vless://user:pass@example.com:8443?host=front%2Eexample.com&path=%2Fws&empty#Server%201",
        )

        assertEquals("example.com", uri.host)
        assertEquals(8443, uri.port)
        assertEquals("user:pass", uri.userInfo)
        assertEquals("Server 1", uri.fragment)
        assertEquals("front.example.com", uri.getQueryParameter("host"))
        assertEquals("/ws", uri.getQueryParameter("path"))
        assertEquals("", uri.getQueryParameter("empty"))
    }

    @Test
    fun `parse handles absent port query fragment and malformed input`() {
        val noPort = UriCompat.parse("vless://id@example.com")
        assertEquals("example.com", noPort.host)
        assertEquals(-1, noPort.port)
        assertNull(noPort.fragment)
        assertNull(noPort.getQueryParameter("missing"))

        val malformed = UriCompat.parse("://broken%%%")
        assertNull(malformed.host)
        assertEquals(-1, malformed.port)
        assertNull(malformed.userInfo)
        assertNull(malformed.fragment)
        assertNull(malformed.getQueryParameter("x"))
    }

    @Test
    fun `query parser keeps last value and raw text when percent decoding fails`() {
        val uri = UriCompat.parse("vless://id@example.com?a=1&a=2&bad=%GG")

        assertEquals("2", uri.getQueryParameter("a"))
        assertEquals("%GG", uri.getQueryParameter("bad"))
    }
}
