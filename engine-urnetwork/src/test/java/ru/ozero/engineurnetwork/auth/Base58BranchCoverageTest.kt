package ru.ozero.engineurnetwork.auth

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Base58BranchCoverageTest {

    @Test
    fun `empty input encodes and decodes to empty output`() {
        assertEquals("", Base58.encode(ByteArray(0)))
        assertEquals(emptyList(), Base58.decode("").toList())
    }

    @Test
    fun `decode preserves multiple leading zero bytes`() {
        val decoded = Base58.decode("1112")

        assertEquals(listOf<Byte>(0, 0, 0, 1), decoded.toList())
    }

    @Test
    fun `all zero payload encodes as one per leading zero and decodes back`() {
        val raw = ByteArray(12)

        val encoded = Base58.encode(raw)
        val decoded = Base58.decode(encoded)

        assertEquals("111111111111", encoded)
        assertEquals(raw.toList(), decoded.toList())
    }

    @Test
    fun `decode rejects invalid unicode and ambiguous alphabet characters`() {
        listOf("0", "O", "I", "l", "é", "11O11").forEach { value ->
            val ex = runCatching { Base58.decode(value) }.exceptionOrNull()
            assertTrue(ex != null, "$value must be invalid base58")
        }
    }

    @Test
    fun `all single byte values roundtrip`() {
        for (value in 0..255) {
            val raw = byteArrayOf(value.toByte())
            assertEquals(raw.toList(), Base58.decode(Base58.encode(raw)).toList(), "byte $value")
        }
    }

    @Test
    fun `payload ending with zero bytes roundtrips without treating suffix as leading zeros`() {
        val raw = byteArrayOf(7, 0, 0, 0)

        val encoded = Base58.encode(raw)
        val decoded = Base58.decode(encoded)

        assertTrue(!encoded.startsWith("1"))
        assertEquals(raw.toList(), decoded.toList())
    }

    @Test
    fun `decode rejects invalid character at exact index after valid prefix`() {
        val ex = runCatching { Base58.decode("111x0") }.exceptionOrNull()

        assertTrue(ex?.message?.contains("4") == true, "invalid index must be reported: ${ex?.message}")
    }

    @Test
    fun `long payload with leading zeros roundtrips without trimming payload zeros`() {
        val raw = ByteArray(96) { index ->
            when (index) {
                0, 1, 2 -> 0
                else -> (index * 37).toByte()
            }
        }

        val encoded = Base58.encode(raw)
        val decoded = Base58.decode(encoded)

        assertTrue(encoded.startsWith("111"), "leading zero bytes must stay encoded as 1: $encoded")
        assertEquals(raw.toList(), decoded.toList())
    }
}
