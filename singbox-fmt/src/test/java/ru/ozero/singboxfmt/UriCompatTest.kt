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
        assertEquals("broken%%%", malformed.host)
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

    @Test
    fun `fallback parser keeps authority and fragment when URI parser fails`() {
        val uri = UriCompat.parse("vless://uuid@example.com:443?type=xhttp&extra={a,b}#Name%20With%20Space")

        assertEquals("uuid", uri.userInfo)
        assertEquals("example.com", uri.host)
        assertEquals(443, uri.port)
        assertEquals("Name%20With%20Space", uri.fragment)
        assertEquals("xhttp", uri.getQueryParameter("type"))
        assertEquals("{a,b}", uri.getQueryParameter("extra"))
    }

    @Test
    fun `fallback parser handles ipv6 authority and query keys without values`() {
        val uri = UriCompat.parse("vless://user@[2001:db8::1]:443?flag&bad={x}#Name")

        assertEquals("user", uri.userInfo)
        assertEquals("2001:db8::1", uri.host)
        assertEquals(443, uri.port)
        assertEquals("Name", uri.fragment)
        assertEquals("", uri.getQueryParameter("flag"))
        assertEquals("{x}", uri.getQueryParameter("bad"))
    }

    @Test
    fun `fallback parser tolerates invalid ports and bare authorities`() {
        val ipv6 = UriCompat.parse("vless://user@[2001:db8::1]:abc?flag#N")
        val bare = UriCompat.parse("vless://example.com?x=1")

        assertEquals("user", ipv6.userInfo)
        assertEquals("2001:db8::1", ipv6.host)
        assertEquals(-1, ipv6.port)
        assertEquals("N", ipv6.fragment)
        assertEquals("", ipv6.getQueryParameter("flag"))
        assertEquals("example.com", bare.host)
        assertEquals(-1, bare.port)
        assertEquals("1", bare.getQueryParameter("x"))
    }

    @Test
    fun `fallback parser handles absent scheme blank authority and empty query`() {
        val noScheme = UriCompat.parse("plain-host?x=1")
        val blankAuthority = UriCompat.parse("vless://?x=1")
        val emptyQuery = UriCompat.parse("vless://user@example.com?")
        val noFragment = UriCompat.parse("vless://user@example.com:8443?host=front")
        val badIpv6 = UriCompat.parse("vless://user@[2001:db8::1?x=1")
        val invalidPort = UriCompat.parse("vless://user@example.com:abc?x=1")
        val bareAuth = UriCompat.parse("vless://user@example.com#frag")

        assertNull(noScheme.host)
        assertEquals("1", noScheme.getQueryParameter("x"))
        assertNull(blankAuthority.host)
        assertEquals("1", blankAuthority.getQueryParameter("x"))
        assertEquals("example.com", emptyQuery.host)
        assertNull(emptyQuery.getQueryParameter("x"))
        assertEquals("front", noFragment.getQueryParameter("host"))
        assertNull(noFragment.fragment)
        assertEquals("[2001:db8::1", badIpv6.host)
        assertEquals(-1, invalidPort.port)
        assertEquals("frag", bareAuth.fragment)
    }

    @Test
    fun `fallback parser decodes percent encoded user info`() {
        val uri = UriCompat.parse("trojan://pa%40ss@host:443?extra={a,b}")

        assertEquals("pa@ss", uri.userInfo)
        assertEquals("host", uri.host)
        assertEquals(443, uri.port)
        assertEquals("{a,b}", uri.getQueryParameter("extra"))
    }
}
