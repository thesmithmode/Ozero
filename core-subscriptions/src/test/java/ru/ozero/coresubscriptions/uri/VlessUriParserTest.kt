package ru.ozero.coresubscriptions.uri

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class VlessUriParserTest {
    private val parser = VlessUriParser()

    @Test
    fun parsesValidRealityUri() {
        val uri =
            "vless://11111111-2222-3333-4444-555555555555@example.com:443" +
                "?encryption=none&security=reality&fp=chrome&pbk=PUBKEY&sid=SHORT&sni=google.com&type=xhttp&flow=" +
                "#MyServer"

        val result = parser.parse(uri)
        assertIs<UriParseResult.Ok<VlessServer>>(result)
        val s = result.server
        assertEquals("11111111-2222-3333-4444-555555555555", s.uuid)
        assertEquals("example.com", s.host)
        assertEquals(443, s.port)
        assertEquals("reality", s.security)
        assertEquals("chrome", s.fingerprint)
        assertEquals("PUBKEY", s.publicKey)
        assertEquals("SHORT", s.shortId)
        assertEquals("google.com", s.sni)
        assertEquals("xhttp", s.transport)
        assertEquals("MyServer", s.remark)
    }

    @Test
    fun parsesUriWithoutFragment() {
        val uri = "vless://UUID@host.io:8443?encryption=none&security=none&type=tcp"
        val result = parser.parse(uri)
        assertIs<UriParseResult.Ok<VlessServer>>(result)
        assertNull(result.server.remark)
    }

    @Test
    fun rejectsNonVlessScheme() {
        val result = parser.parse("ss://host:443")
        assertIs<UriParseResult.Error>(result)
    }

    @Test
    fun rejectsMissingUuid() {
        val result = parser.parse("vless://@host:443?encryption=none")
        assertIs<UriParseResult.Error>(result)
    }

    @Test
    fun rejectsMissingHost() {
        val result = parser.parse("vless://UUID@:443?encryption=none")
        assertIs<UriParseResult.Error>(result)
    }

    @Test
    fun rejectsMissingPort() {
        val result = parser.parse("vless://UUID@host?encryption=none")
        assertIs<UriParseResult.Error>(result)
    }

    @Test
    fun rejectsMalformedUri() {
        assertIs<UriParseResult.Error>(parser.parse("not a uri"))
        assertIs<UriParseResult.Error>(parser.parse(""))
    }

    @Test
    fun decodesUrlEncodedRemark() {
        val uri = "vless://UUID@host.io:443?encryption=none#My%20Server%20RU"
        val result = parser.parse(uri)
        assertIs<UriParseResult.Ok<VlessServer>>(result)
        assertEquals("My Server RU", result.server.remark)
    }
}
