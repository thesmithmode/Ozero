package ru.ozero.engineurnetwork.auth

import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

import org.junit.jupiter.api.Test

class Base58JvmBranchCoverageTest {

    @Test
    fun `empty byte array encodes and decodes to empty result`() {
        val encoded = Base58.encode(ByteArray(0))
        val decoded = Base58.decode(encoded)

        assertEquals("", encoded)
        assertEquals(0, decoded.size)
    }

    @Test
    fun `leading zero bytes round trip through leading ones`() {
        val raw = byteArrayOf(0, 0, 0, 1, 2)
        val encoded = Base58.encode(raw)
        val decoded = Base58.decode(encoded)

        assertTrue(encoded.startsWith("111"))
        assertEquals(raw.toList(), decoded.toList())
    }

    @Test
    fun `decode rejects invalid ascii characters with index`() {
        val ex = runCatching { Base58.decode("ABC0") }.exceptionOrNull()

        assertTrue(ex != null)
        assertEquals("invalid base58 character at 3", ex.message)
    }

    @Test
    fun `decode rejects unicode character`() {
        val ex = runCatching { Base58.decode("abc\u0410") }.exceptionOrNull()

        assertTrue(ex != null)
        assertTrue(ex.message?.contains("invalid base58 character") == true)
    }

    @Test
    fun `random byte arrays roundtrip`() {
        val random = Random(17)
        val raw = ByteArray(64) { random.nextInt(-128, 128).toByte() }
        val encoded = Base58.encode(raw)
        val decoded = Base58.decode(encoded)

        assertEquals(raw.size, decoded.size)
        assertEquals(raw.toList(), decoded.toList())
    }

    @Test
    fun `roundtrip with nonzero leading and high bit set`() {
        val raw = byteArrayOf(0xff.toByte(), 0x00.toByte(), 0x7f.toByte(), 0x01.toByte())
        val encoded = Base58.encode(raw)
        val decoded = Base58.decode(encoded)

        assertNotEquals("", encoded)
        assertEquals(raw.toList(), decoded.toList())
    }
}
