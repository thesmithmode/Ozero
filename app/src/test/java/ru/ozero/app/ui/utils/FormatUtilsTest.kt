package ru.ozero.app.ui.utils

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FormatUtilsTest {

    @Test
    fun `formatBytes returns zero megabytes for zero and negative values`() {
        assertEquals("0 МБ", formatBytes(0))
        assertEquals("0 МБ", formatBytes(-1))
    }

    @Test
    fun `formatBytes formats values below one megabyte as kilobytes`() {
        assertEquals("1 КБ", formatBytes(1024))
        assertEquals("512 КБ", formatBytes(512 * 1024))
    }

    @Test
    fun `formatBytes formats megabytes with one decimal place`() {
        assertEquals("1.0 МБ", formatBytes(1024 * 1024))
        assertEquals("1.5 МБ", formatBytes(1536 * 1024))
    }

    @Test
    fun `formatBytes formats gigabytes with two decimal places`() {
        assertEquals("1.00 ГБ", formatBytes(1024L * 1024L * 1024L))
        assertEquals("1.50 ГБ", formatBytes(1536L * 1024L * 1024L))
    }
}
