package ru.ozero.engineurnetwork.auth

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Base58BranchCoverageTest {

    @Test
    fun encodeAllZeroBytesPreservesEveryLeadingZeroAsOne() {
        assertEquals("11111", Base58.encode(ByteArray(5)))
    }

    @Test
    fun decodeLeadingOnesRestoresLeadingZeroBytes() {
        val decoded = Base58.decode("1112")
        assertEquals(4, decoded.size)
        assertEquals(0, decoded[0])
        assertEquals(0, decoded[1])
        assertEquals(0, decoded[2])
        assertEquals(1, decoded[3])
    }

    @Test
    fun decodeNonAsciiCharacterFailsBeforeDivmod() {
        val ex = runCatching { Base58.decode("abc\u0401") }.exceptionOrNull()
        assertTrue(ex != null)
        assertTrue(ex.message?.contains("invalid base58 character") == true)
    }
}
