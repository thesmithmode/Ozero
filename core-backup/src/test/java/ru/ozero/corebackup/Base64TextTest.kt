package ru.ozero.corebackup

import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Base64TextTest {

    @Test
    fun `encode handles empty one two and three byte groups`() {
        assertEquals("", Base64Text.encode(byteArrayOf()))
        assertEquals("Zg==", Base64Text.encode("f".toByteArray()))
        assertEquals("Zm8=", Base64Text.encode("fo".toByteArray()))
        assertEquals("Zm9v", Base64Text.encode("foo".toByteArray()))
    }

    @Test
    fun `decode ignores whitespace and handles padding`() {
        assertContentEquals("f".toByteArray(), Base64Text.decode(" Zg==\n"))
        assertContentEquals("fo".toByteArray(), Base64Text.decode("Zm8="))
        assertContentEquals("foo".toByteArray(), Base64Text.decode("Zm9v"))
    }

    @Test
    fun `decode rejects bad length and bad characters`() {
        assertFailsWith<IllegalArgumentException> { Base64Text.decode("Z") }
        assertFailsWith<IllegalArgumentException> { Base64Text.decode("Zm9@") }
        assertFailsWith<IllegalArgumentException> { Base64Text.decode("Zm9\u0100") }
    }
}
