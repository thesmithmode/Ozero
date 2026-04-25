package ru.ozero.coresubscriptions.uri

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NaiveUriParserTest {
    private val parser = NaiveUriParser()

    @Test
    fun parsesHttpsUri() {
        val r = parser.parse("naive+https://user:pass@example.com:443#RU-Entry")
        assertIs<UriParseResult.Ok<NaiveServer>>(r)
        val s = r.server
        assertEquals("https", s.scheme)
        assertEquals("user", s.username)
        assertEquals("pass", s.password)
        assertEquals("example.com", s.host)
        assertEquals(443, s.port)
        assertEquals("RU-Entry", s.remark)
        assertTrue(!s.isQuic)
    }

    @Test
    fun parsesQuicUri() {
        val r = parser.parse("naive+quic://u:p@h:443") as UriParseResult.Ok
        assertEquals("quic", r.server.scheme)
        assertTrue(r.server.isQuic)
    }

    @Test
    fun proxyUrlComposed() {
        val r = parser.parse("naive+https://u:p@h:443") as UriParseResult.Ok
        assertEquals("https://u:p@h:443", r.server.proxyUrl)
    }

    @Test
    fun rejectsWrongScheme() {
        assertIs<UriParseResult.Error>(parser.parse("https://u:p@h:443"))
    }

    @Test
    fun rejectsMissingSubScheme() {
        assertIs<UriParseResult.Error>(parser.parse("naive+u:p@h:443"))
    }

    @Test
    fun rejectsUnknownSubScheme() {
        assertIs<UriParseResult.Error>(parser.parse("naive+ftp://u:p@h:443"))
    }

    @Test
    fun rejectsMissingUserPass() {
        assertIs<UriParseResult.Error>(parser.parse("naive+https://h:443"))
    }

    @Test
    fun rejectsBlankUser() {
        assertIs<UriParseResult.Error>(parser.parse("naive+https://:p@h:443"))
    }

    @Test
    fun rejectsBlankPass() {
        assertIs<UriParseResult.Error>(parser.parse("naive+https://u:@h:443"))
    }

    @Test
    fun rejectsMissingPort() {
        assertIs<UriParseResult.Error>(parser.parse("naive+https://u:p@h"))
    }

    @Test
    fun decodesUrlEncodedCredentials() {
        val r = parser.parse("naive+https://u%40s:p%26w@h:443") as UriParseResult.Ok
        assertEquals("u@s", r.server.username)
        assertEquals("p&w", r.server.password)
    }
}
