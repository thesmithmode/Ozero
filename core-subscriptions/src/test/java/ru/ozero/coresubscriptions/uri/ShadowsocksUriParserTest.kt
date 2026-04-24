package ru.ozero.coresubscriptions.uri

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ShadowsocksUriParserTest {
    private val parser = ShadowsocksUriParser()

    @Test
    fun parsesPlainUri() {
        val uri = "ss://aes-256-gcm:mypassword@example.com:8388#My%20SS"
        val result = parser.parse(uri)
        assertIs<UriParseResult.Ok<ShadowsocksServer>>(result)
        val s = result.server
        assertEquals("aes-256-gcm", s.method)
        assertEquals("mypassword", s.password)
        assertEquals("example.com", s.host)
        assertEquals(8388, s.port)
        assertEquals("My SS", s.remark)
    }

    @Test
    fun parsesBase64Uri() {
        // base64("aes-256-gcm:secretpass") = "YWVzLTI1Ni1nY206c2VjcmV0cGFzcw=="
        val uri = "ss://YWVzLTI1Ni1nY206c2VjcmV0cGFzcw==@host.io:8388#Remark"
        val result = parser.parse(uri)
        assertIs<UriParseResult.Ok<ShadowsocksServer>>(result)
        assertEquals("aes-256-gcm", result.server.method)
        assertEquals("secretpass", result.server.password)
        assertEquals("host.io", result.server.host)
        assertEquals(8388, result.server.port)
    }

    @Test
    fun rejectsWrongScheme() {
        assertIs<UriParseResult.Error>(parser.parse("vless://uuid@host:443"))
    }

    @Test
    fun rejectsMalformedUserInfo() {
        val result = parser.parse("ss://nopassword@host:8388")
        assertIs<UriParseResult.Error>(result)
    }

    @Test
    fun rejectsMissingPort() {
        assertIs<UriParseResult.Error>(parser.parse("ss://aes:pass@host"))
    }
}
