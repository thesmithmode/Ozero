package ru.ozero.coresubscriptions.uri

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TrojanUriParserTest {
    private val parser = TrojanUriParser()

    @Test
    fun parsesFullUri() {
        val uri = "trojan://password@example.com:443?sni=example.com&peer=cdn.example.com#Remark"
        val result = parser.parse(uri)
        assertIs<UriParseResult.Ok<TrojanServer>>(result)
        val s = result.server
        assertEquals("password", s.password)
        assertEquals("example.com", s.host)
        assertEquals(443, s.port)
        assertEquals("example.com", s.sni)
        assertEquals("cdn.example.com", s.peer)
        assertEquals("Remark", s.remark)
    }

    @Test
    fun parsesMinimalUri() {
        val uri = "trojan://pass@host:443"
        val result = parser.parse(uri)
        assertIs<UriParseResult.Ok<TrojanServer>>(result)
        assertEquals("pass", result.server.password)
    }

    @Test
    fun rejectsWrongScheme() {
        assertIs<UriParseResult.Error>(parser.parse("vless://uuid@host:443"))
    }

    @Test
    fun rejectsMissingPassword() {
        assertIs<UriParseResult.Error>(parser.parse("trojan://@host:443"))
    }

    @Test
    fun rejectsMissingPort() {
        assertIs<UriParseResult.Error>(parser.parse("trojan://pass@host"))
    }
}
