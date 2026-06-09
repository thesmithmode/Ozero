package ru.ozero.engineurnetwork.auth

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Base58BranchCoverageTest {

    @Test
    fun `decode preserves multiple leading zero bytes`() {
        val decoded = Base58.decode("1112")

        assertEquals(listOf<Byte>(0, 0, 0, 1), decoded.toList())
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
