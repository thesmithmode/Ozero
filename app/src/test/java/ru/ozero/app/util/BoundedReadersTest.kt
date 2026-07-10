package ru.ozero.app.util

import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BoundedReadersTest {

    @Test
    fun `readBytesBounded returns payload within limit`() {
        val bytes = byteArrayOf(1, 2, 3)

        val result = bytes.inputStream().readBytesBounded(maxBytes = 3)

        assertEquals(bytes.toList(), result.toList())
    }

    @Test
    fun `readBytesBounded throws before accepting oversized payload`() {
        val e = assertFailsWith<IOException> {
            byteArrayOf(1, 2, 3, 4).inputStream().readBytesBounded(maxBytes = 3)
        }

        assertEquals("Input is larger than 3 bytes", e.message)
    }

    @Test
    fun `readTextBounded decodes text within limit`() {
        val text = "hello"

        val result = text.byteInputStream().readTextBounded(maxBytes = 5)

        assertEquals(text, result)
    }
}
