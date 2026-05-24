package ru.ozero.singboxsubscription.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SubscriptionInfoParserTest {

    @Test
    fun `should return null for null header`() {
        assertNull(SubscriptionInfoParser.parse(null))
    }

    @Test
    fun `should return null for blank header`() {
        assertNull(SubscriptionInfoParser.parse(""))
        assertNull(SubscriptionInfoParser.parse("   "))
    }

    @Test
    fun `should return null for header with no recognized fields`() {
        assertNull(SubscriptionInfoParser.parse("some-random-text-without-equals"))
    }

    @Test
    fun `should parse full subscription-userinfo header`() {
        val header = "upload=1073741824; download=2147483648; total=107374182400; expire=1735689600"

        val info = SubscriptionInfoParser.parse(header)

        assertNotNull(info)
        assertEquals(1073741824L, info!!.uploadBytes)
        assertEquals(2147483648L, info.downloadBytes)
        assertEquals(107374182400L, info.totalBytes)
        assertEquals(1735689600L, info.expiryTimestamp)
    }

    @Test
    fun `should parse partial header with missing fields defaulting to zero`() {
        val header = "upload=512; download=1024"

        val info = SubscriptionInfoParser.parse(header)

        assertNotNull(info)
        assertEquals(512L, info!!.uploadBytes)
        assertEquals(1024L, info.downloadBytes)
        assertEquals(0L, info.totalBytes)
        assertEquals(0L, info.expiryTimestamp)
    }

    @Test
    fun `should parse header with only upload field`() {
        val info = SubscriptionInfoParser.parse("upload=999")

        assertNotNull(info)
        assertEquals(999L, info!!.uploadBytes)
        assertEquals(0L, info.downloadBytes)
    }

    @Test
    fun `should parse header with only expire field`() {
        val info = SubscriptionInfoParser.parse("expire=1800000000")

        assertNotNull(info)
        assertEquals(1800000000L, info!!.expiryTimestamp)
    }

    @Test
    fun `should handle zero values in header`() {
        val info = SubscriptionInfoParser.parse("upload=0; download=0; total=0; expire=0")

        assertNotNull(info)
        assertEquals(0L, info!!.uploadBytes)
        assertEquals(0L, info.downloadBytes)
    }

    @Test
    fun `should handle header without semicolons`() {
        val info = SubscriptionInfoParser.parse("upload=100 download=200")

        assertNotNull(info)
        assertEquals(100L, info!!.uploadBytes)
        assertEquals(200L, info.downloadBytes)
    }

    @Test
    fun `should handle very large byte values`() {
        val total = Long.MAX_VALUE
        val info = SubscriptionInfoParser.parse("total=$total")

        assertNotNull(info)
        assertEquals(total, info!!.totalBytes)
    }
}
