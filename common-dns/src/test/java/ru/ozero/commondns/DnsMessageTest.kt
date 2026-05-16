package ru.ozero.commondns

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DnsMessageTest {

    @Test
    fun aQueryHasCorrectQtype() {
        val q = DnsMessage.buildAQuery("example.com")
        assertEquals(0, q[q.size - 4].toInt())
        assertEquals(1, q[q.size - 3].toInt())
        assertEquals(0, q[q.size - 2].toInt())
        assertEquals(1, q[q.size - 1].toInt())
    }

    @Test
    fun aaaaQueryHasCorrectQtype() {
        val q = DnsMessage.buildAAAAQuery("example.com")
        assertEquals(0, q[q.size - 4].toInt())
        assertEquals(28, q[q.size - 3].toInt())
        assertEquals(0, q[q.size - 2].toInt())
        assertEquals(1, q[q.size - 1].toInt())
    }

    @Test
    fun aaaaQueryEncodesHostnameCorrectly() {
        val q = DnsMessage.buildAAAAQuery("a.b")
        assertEquals(17 + 4, q.size)
    }

    @Test
    fun parseAAAAAnswersExtractsIpv6() {
        val body = byteArrayOf(
            0, 0, 0x81.toByte(), 0x80.toByte(), 0, 1, 0, 1, 0, 0, 0, 0,
            1, 'a'.code.toByte(), 1, 'b'.code.toByte(), 0,
            0, 28, 0, 1,
            0xC0.toByte(), 0x0C, 0, 28, 0, 1, 0, 0, 0, 60, 0, 16,
            0x20, 0x01, 0x0d, 0xb8.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1,
        )
        val ips = DnsMessage.parseAAAAAnswers(body)
        assertEquals(1, ips.size)
        assertEquals("2001:db8:0:0:0:0:0:1", ips[0])
    }

    @Test
    fun parseAAnswersReturnsEmptyForMalformedTooShort() {
        assertTrue(DnsMessage.parseAAnswers(ByteArray(5)).isEmpty())
        assertTrue(DnsMessage.parseAAAAAnswers(ByteArray(5)).isEmpty())
    }

    @Test
    fun parseAAnswersIgnoresAAAARecords() {
        val body = byteArrayOf(
            0, 0, 0x81.toByte(), 0x80.toByte(), 0, 1, 0, 1, 0, 0, 0, 0,
            1, 'a'.code.toByte(), 0,
            0, 28, 0, 1,
            0xC0.toByte(), 0x0C, 0, 28, 0, 1, 0, 0, 0, 60, 0, 16,
            0x20, 0x01, 0x0d, 0xb8.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1,
        )
        assertTrue(DnsMessage.parseAAnswers(body).isEmpty())
    }

    @Test
    fun `buildAQuery бросает IllegalArgumentException на label > 63 байт`() {
        val tooLong = "a".repeat(64) + ".com"
        val e = kotlin.runCatching { DnsMessage.buildAQuery(tooLong) }.exceptionOrNull()
        assertTrue(e is IllegalArgumentException, "ожидалась IllegalArgumentException, был: $e")
        assertTrue(e!!.message?.contains("label") == true, "msg: ${e.message}")
    }

    @Test
    fun `buildAQuery accept ровно 63 байта label — RFC 1035 boundary`() {
        val ok = "a".repeat(63) + ".com"
        DnsMessage.buildAQuery(ok)
    }

    @Test
    fun `buildAQuery бросает на total name > 255 байт`() {
        val labels = generateSequence { "a".repeat(50) }.take(6).joinToString(".")
        val e = kotlin.runCatching { DnsMessage.buildAQuery(labels) }.exceptionOrNull()
        assertTrue(e is IllegalArgumentException, "ожидалась IllegalArgumentException, был: $e")
        assertTrue(e!!.message?.contains("имя") == true, "msg: ${e.message}")
    }

    @Test
    fun `buildAQuery бросает на пустой hostname`() {
        val e = kotlin.runCatching { DnsMessage.buildAQuery("") }.exceptionOrNull()
        assertTrue(e is IllegalArgumentException)
    }

}
