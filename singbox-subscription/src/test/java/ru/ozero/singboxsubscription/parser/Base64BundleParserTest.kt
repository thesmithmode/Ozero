package ru.ozero.singboxsubscription.parser

import org.junit.jupiter.api.Test
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Base64BundleParserTest {

    private fun encodeBase64(text: String): String =
        Base64.getEncoder().withoutPadding().encodeToString(text.toByteArray(Charsets.UTF_8))

    private val vless1 =
        "vless://aaaaaaaa-1111-1111-1111-aaaaaaaaaaaa@s1.example.com:443?type=tcp&security=none#S1"
    private val vless2 =
        "vless://bbbbbbbb-2222-2222-2222-bbbbbbbbbbbb@s2.example.com:443?type=tcp&security=none#S2"

    @Test
    fun `should return empty list for empty string`() {
        val result = Base64BundleParser.parse(encodeBase64(""))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `should return empty list for garbage base64`() {
        val result = Base64BundleParser.parse("!!!not-valid-base64!!!")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `should parse single vless link from base64 bundle`() {
        val encoded = encodeBase64(vless1)
        val result = Base64BundleParser.parse(encoded)
        assertEquals(1, result.size)
    }

    @Test
    fun `should parse multiple vless links from base64 bundle`() {
        val encoded = encodeBase64("$vless1\n$vless2")
        val result = Base64BundleParser.parse(encoded)
        assertEquals(2, result.size)
    }

    @Test
    fun `should skip comment lines inside base64 bundle`() {
        val text = "# This is a header comment\n$vless1"
        val encoded = encodeBase64(text)
        val result = Base64BundleParser.parse(encoded)
        assertEquals(1, result.size)
    }

    @Test
    fun `should handle base64 with leading and trailing whitespace`() {
        val encoded = "  ${encodeBase64(vless1)}  "
        val result = Base64BundleParser.parse(encoded)
        assertEquals(1, result.size)
    }

    @Test
    fun `should handle url-safe base64 variant`() {
        val textBytes = "$vless1\n$vless2".toByteArray(Charsets.UTF_8)
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(textBytes)
        val result = Base64BundleParser.parse(encoded)
        assertEquals(2, result.size)
    }
}
