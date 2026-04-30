package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class BytesFormatterTest {

    @Test
    fun zeroBytes() {
        assertEquals("0 B", BytesFormatter.humanReadable(0))
    }

    @Test
    fun smallBytes() {
        assertEquals("1 B", BytesFormatter.humanReadable(1))
        assertEquals("512 B", BytesFormatter.humanReadable(512))
        assertEquals("1023 B", BytesFormatter.humanReadable(1023))
    }

    @Test
    fun kilobyteBoundary() {
        assertEquals("1.0 KB", BytesFormatter.humanReadable(1024))
        assertEquals("1.5 KB", BytesFormatter.humanReadable(1536))
        assertEquals("999.0 KB", BytesFormatter.humanReadable(999L * 1024))
    }

    @Test
    fun megabyteBoundary() {
        assertEquals("1.0 MB", BytesFormatter.humanReadable(1024L * 1024))
        assertEquals("12.5 MB", BytesFormatter.humanReadable((12.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun gigabyteBoundary() {
        assertEquals("1.0 GB", BytesFormatter.humanReadable(1024L * 1024 * 1024))
        assertEquals("3.5 GB", BytesFormatter.humanReadable((3.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun terabyteBoundary() {
        val tb = 1024L * 1024 * 1024 * 1024
        assertEquals("1.0 TB", BytesFormatter.humanReadable(tb))
    }

    @Test
    fun negativeBytes() {
        assertEquals("-1.0 KB", BytesFormatter.humanReadable(-1024))
    }
}
