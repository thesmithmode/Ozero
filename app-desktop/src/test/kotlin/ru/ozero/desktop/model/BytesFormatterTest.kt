package ru.ozero.desktop.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BytesFormatterTest {

    @Test
    fun `humanReadable returns 0 B for zero`() {
        assertEquals("0 B", BytesFormatter.humanReadable(0L))
    }

    @Test
    fun `humanReadable formats bytes`() {
        assertEquals("512 B", BytesFormatter.humanReadable(512L))
    }

    @Test
    fun `humanReadable formats kilobytes`() {
        assertEquals("1.0 KB", BytesFormatter.humanReadable(1024L))
    }

    @Test
    fun `humanReadable formats megabytes`() {
        assertEquals("10.0 MB", BytesFormatter.humanReadable(10 * 1024 * 1024L))
    }

    @Test
    fun `humanReadablePerSec returns 0 for zero`() {
        assertEquals("0 B/s", BytesFormatter.humanReadablePerSec(0.0))
    }

    @Test
    fun `humanReadablePerSec formats small values`() {
        assertEquals("512 B/s", BytesFormatter.humanReadablePerSec(512.0))
    }

    @Test
    fun `humanReadablePerSec formats large values`() {
        assertEquals("1.0 MB/s", BytesFormatter.humanReadablePerSec(1024.0 * 1024.0))
    }

    @Test
    fun `durationHms returns zero for no time`() {
        assertEquals("00:00", BytesFormatter.durationHms(0L))
    }

    @Test
    fun `durationHms formats minutes and seconds`() {
        assertEquals("05:30", BytesFormatter.durationHms(330_000L))
    }

    @Test
    fun `durationHms formats hours`() {
        assertEquals("1:30:00", BytesFormatter.durationHms(5_400_000L))
    }
}
