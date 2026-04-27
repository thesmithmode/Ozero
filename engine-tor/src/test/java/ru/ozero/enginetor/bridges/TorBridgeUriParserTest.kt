package ru.ozero.enginetor.bridges

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TorBridgeUriParserTest {
    private val parser = TorBridgeUriParser()

    @Test
    fun parsesObfs4WithCertAndIat() {
        val r = parser.parse(
            "bridge://obfs4@192.0.2.1:443?fingerprint=AAAA&cert=BBBB&iat-mode=0#RU-Bridge",
        ).getOrThrow()
        assertEquals("obfs4", r.transport)
        assertEquals("192.0.2.1:443", r.address)
        assertEquals("AAAA", r.fingerprint)
        assertEquals("BBBB", r.args["cert"])
        assertEquals("0", r.args["iat-mode"])
        assertEquals("RU-Bridge", r.remark)
    }

    @Test
    fun rendersTorrcLineForObfs4() {
        val r = parser.parse(
            "bridge://obfs4@1.1.1.1:443?fingerprint=FP&cert=CERT&iat-mode=0",
        ).getOrThrow()
        assertEquals("Bridge obfs4 1.1.1.1:443 FP cert=CERT iat-mode=0", r.toTorrcLine())
    }

    @Test
    fun parsesSnowflakeWithUrlAndFront() {
        val r = parser.parse(
            "bridge://snowflake@snowflake.torproject.org:80" +
                "?fingerprint=2B280B23E1107BB62ABFC40DDCC8824814F80A72" +
                "&url=https%3A%2F%2Fsnowflake-broker.torproject.net.global.prod.fastly.net%2F" +
                "&front=cdn.example.com",
        ).getOrThrow()
        assertEquals("snowflake", r.transport)
        assertTrue(r.args["url"]!!.startsWith("https://"))
        assertEquals("cdn.example.com", r.args["front"])
    }

    @Test
    fun parsesWebtunnel() {
        val r = parser.parse(
            "bridge://webtunnel@example.com:443?fingerprint=FP&url=https://example.com/path",
        ).getOrThrow()
        assertEquals("webtunnel", r.transport)
    }

    @Test
    fun parsesMeekLite() {
        val r = parser.parse(
            "bridge://meek_lite@meek.azureedge.net:443?fingerprint=FP&url=https://example.com&front=ajax.aspnetcdn.com",
        ).getOrThrow()
        assertEquals("meek_lite", r.transport)
    }

    @Test
    fun rejectsUnknownTransport() {
        assertTrue(parser.parse("bridge://wrong@h:1?fingerprint=FP").isFailure)
    }

    @Test
    fun rejectsMissingFingerprint() {
        assertTrue(parser.parse("bridge://obfs4@h:443").isFailure)
    }

    @Test
    fun rejectsMissingTransport() {
        assertTrue(parser.parse("bridge://h:443?fingerprint=FP").isFailure)
    }

    @Test
    fun rejectsWrongScheme() {
        assertTrue(parser.parse("https://h:443").isFailure)
    }

    @Test
    fun argsSortedDeterministically() {
        val a = parser.parse("bridge://obfs4@h:443?fingerprint=F&z=1&a=2&m=3").getOrThrow()
                val keys = a.args.keys.toList()
        assertEquals(listOf("a", "m", "z"), keys)
    }
}
