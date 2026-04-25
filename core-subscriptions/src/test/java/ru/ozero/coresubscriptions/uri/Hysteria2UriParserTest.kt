package ru.ozero.coresubscriptions.uri

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class Hysteria2UriParserTest {
    private val parser = Hysteria2UriParser()

    @Test
    fun parsesFullUri() {
        val uri =
            "hysteria2://secretpass@example.com:443" +
                "?sni=example.com&insecure=1&obfs=salamander&obfs-password=obfspass" +
                "#RU-Entry"

        val result = parser.parse(uri)
        assertIs<UriParseResult.Ok<Hysteria2Server>>(result)
        val s = result.server
        assertEquals("secretpass", s.password)
        assertEquals("example.com", s.host)
        assertEquals(443, s.port)
        assertEquals("example.com", s.sni)
        assertEquals(true, s.insecure)
        assertEquals("salamander", s.obfs)
        assertEquals("obfspass", s.obfsPassword)
        assertEquals("RU-Entry", s.remark)
    }

    @Test
    fun parsesMinimalUri() {
        val uri = "hysteria2://pass@host:443"
        val result = parser.parse(uri)
        assertIs<UriParseResult.Ok<Hysteria2Server>>(result)
        assertEquals("pass", result.server.password)
        assertEquals(false, result.server.insecure)
        assertNull(result.server.obfs)
    }

    @Test
    fun acceptsHy2SchemeAlias() {
        val result = parser.parse("hy2://pass@host:443")
        assertIs<UriParseResult.Ok<Hysteria2Server>>(result)
    }

    @Test
    fun rejectsWrongScheme() {
        assertIs<UriParseResult.Error>(parser.parse("vless://uuid@host:443"))
    }

    @Test
    fun rejectsMissingPassword() {
        assertIs<UriParseResult.Error>(parser.parse("hysteria2://@host:443"))
    }

    @Test
    fun rejectsMissingPort() {
        assertIs<UriParseResult.Error>(parser.parse("hysteria2://pass@host"))
    }

    @Test
    fun insecureFalseWhenAbsent() {
        val result = parser.parse("hysteria2://pass@host:443?sni=x")
        assertIs<UriParseResult.Ok<Hysteria2Server>>(result)
        assertEquals(false, result.server.insecure)
    }

    // ---- E4: pinSHA256, multi-SNI, mport, bandwidth -----------------

    @Test
    fun parsesPinSha256() {
        val r = parser.parse("hysteria2://p@h:443?pinSHA256=AB:CD:EF")
        assertIs<UriParseResult.Ok<Hysteria2Server>>(r)
        assertEquals("AB:CD:EF", r.server.pinSHA256)
    }

    @Test
    fun pinSha256NullWhenAbsent() {
        val r = parser.parse("hysteria2://p@h:443")
        assertIs<UriParseResult.Ok<Hysteria2Server>>(r)
        assertNull(r.server.pinSHA256)
    }

    @Test
    fun parsesMultiSni() {
        val r = parser.parse("hysteria2://p@h:443?sni=primary.com,alt1.com,alt2.com")
        assertIs<UriParseResult.Ok<Hysteria2Server>>(r)
        assertEquals("primary.com", r.server.sni)
        assertEquals(listOf("alt1.com", "alt2.com"), r.server.sniAlternatives)
    }

    @Test
    fun singleSniProducesEmptyAlternatives() {
        val r = parser.parse("hysteria2://p@h:443?sni=only.com")
        assertIs<UriParseResult.Ok<Hysteria2Server>>(r)
        assertEquals("only.com", r.server.sni)
        assertEquals(emptyList(), r.server.sniAlternatives)
    }

    @Test
    fun parsesMportRange() {
        val r = parser.parse("hysteria2://p@h:443?mport=20000-50000")
        assertIs<UriParseResult.Ok<Hysteria2Server>>(r)
        assertEquals(20000, r.server.portRangeStart)
        assertEquals(50000, r.server.portRangeEnd)
    }

    @Test
    fun mportInvertedIgnored() {
        val r = parser.parse("hysteria2://p@h:443?mport=50000-20000")
        assertIs<UriParseResult.Ok<Hysteria2Server>>(r)
        assertNull(r.server.portRangeStart)
        assertNull(r.server.portRangeEnd)
    }

    @Test
    fun mportMalformedIgnored() {
        val r = parser.parse("hysteria2://p@h:443?mport=abc")
        assertIs<UriParseResult.Ok<Hysteria2Server>>(r)
        assertNull(r.server.portRangeStart)
        assertNull(r.server.portRangeEnd)
    }

    @Test
    fun parsesBandwidthUpDown() {
        val r = parser.parse("hysteria2://p@h:443?up=100mbps&down=500mbps")
        assertIs<UriParseResult.Ok<Hysteria2Server>>(r)
        assertEquals("100mbps", r.server.bandwidthUp)
        assertEquals("500mbps", r.server.bandwidthDown)
    }
}
