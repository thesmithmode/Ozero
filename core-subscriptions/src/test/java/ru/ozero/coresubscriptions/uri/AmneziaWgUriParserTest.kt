package ru.ozero.coresubscriptions.uri

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AmneziaWgUriParserTest {
    private val parser = AmneziaWgUriParser()

    @Test
    fun parsesFullUri() {
        val uri = "awg://PRIVKEY@vpn.example.com:51820" +
            "?publicKey=PUBKEY&presharedKey=PSK" +
            "&allowedIPs=0.0.0.0/0,::/0&address=10.0.0.2/32" +
            "&dns=1.1.1.1,8.8.8.8&mtu=1280&keepalive=25" +
            "&jc=4&jmin=40&jmax=70&s1=0&s2=0" +
            "&h1=0xAABBCCDD&h2=0x11223344&h3=99&h4=100" +
            "#RU-Entry"
        val r = parser.parse(uri)
        assertIs<UriParseResult.Ok<AmneziaWgServer>>(r)
        val s = r.server
        assertEquals("PRIVKEY", s.privateKey)
        assertEquals("PUBKEY", s.publicKey)
        assertEquals("PSK", s.presharedKey)
        assertEquals("vpn.example.com", s.host)
        assertEquals(51820, s.port)
        assertEquals(listOf("0.0.0.0/0", "::/0"), s.allowedIps)
        assertEquals(listOf("10.0.0.2/32"), s.addresses)
        assertEquals(listOf("1.1.1.1", "8.8.8.8"), s.dns)
        assertEquals(1280, s.mtu)
        assertEquals(25, s.persistentKeepalive)
        assertEquals(4, s.jc)
        assertEquals(40, s.jmin)
        assertEquals(70, s.jmax)
        assertEquals(0, s.s1)
        assertEquals(0, s.s2)
        assertEquals(0xAABBCCDDL, s.h1)
        assertEquals(0x11223344L, s.h2)
        assertEquals(99L, s.h3)
        assertEquals(100L, s.h4)
        assertEquals("RU-Entry", s.remark)
    }

    @Test
    fun parsesMinimalUri() {
        val r = parser.parse("awg://PRIV@host:51820?publicKey=PUB")
        assertIs<UriParseResult.Ok<AmneziaWgServer>>(r)
        val s = r.server
        assertEquals(listOf("0.0.0.0/0", "::/0"), s.allowedIps)
        assertEquals(emptyList(), s.addresses)
        assertEquals(emptyList(), s.dns)
        assertEquals(AmneziaWgServer.DEFAULT_MTU, s.mtu)
        assertEquals(0, s.jc)
        assertEquals(0L, s.h1)
        assertNull(s.presharedKey)
    }

    @Test
    fun rejectsWrongScheme() {
        assertIs<UriParseResult.Error>(parser.parse("vless://x@host:443"))
    }

    @Test
    fun rejectsMissingPrivateKey() {
        assertIs<UriParseResult.Error>(parser.parse("awg://@host:51820?publicKey=PUB"))
    }

    @Test
    fun rejectsMissingPublicKey() {
        assertIs<UriParseResult.Error>(parser.parse("awg://PRIV@host:51820"))
    }

    @Test
    fun rejectsMissingPort() {
        assertIs<UriParseResult.Error>(parser.parse("awg://PRIV@host?publicKey=PUB"))
    }

    @Test
    fun hexAndDecimalH1Equivalent() {
        val a = parser.parse("awg://P@h:1?publicKey=P&h1=0x10") as UriParseResult.Ok
        val b = parser.parse("awg://P@h:1?publicKey=P&h1=16") as UriParseResult.Ok
        assertEquals(a.server.h1, b.server.h1)
    }

    @Test
    fun malformedNumberFallsBackToDefault() {
        val r = parser.parse("awg://P@h:1?publicKey=P&jc=abc&h1=zzz") as UriParseResult.Ok
        assertEquals(0, r.server.jc)
        assertEquals(0L, r.server.h1)
    }
}
