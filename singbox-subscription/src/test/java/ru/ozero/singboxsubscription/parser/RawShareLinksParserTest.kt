package ru.ozero.singboxsubscription.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.ozero.singboxfmt.VLESSBean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RawShareLinksParserTest {

    private val sampleVless =
        "vless://12345678-1234-1234-1234-123456789abc@proxy.example.com:443" +
            "?type=tcp&security=none#Test+Server"

    @Test
    fun `should return empty list for empty input`() {
        val result = RawShareLinksParser.parse("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `should return empty list for blank lines only`() {
        val result = RawShareLinksParser.parse("   \n\n   \n")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `should skip comment lines starting with hash`() {
        val text = "# This is a comment\n$sampleVless"
        val result = RawShareLinksParser.parse(text)
        assertEquals(1, result.size)
    }

    @Test
    fun `should parse single valid vless uri`() {
        val result = RawShareLinksParser.parse(sampleVless)
        assertEquals(1, result.size)
    }

    @Test
    fun `should parse multiple valid vless uris`() {
        val vless1 = "vless://aaaaaaaa-1111-1111-1111-aaaaaaaaaaaa@server1.example.com:443" +
            "?type=tcp&security=none#Server1"
        val vless2 = "vless://bbbbbbbb-2222-2222-2222-bbbbbbbbbbbb@server2.example.com:8443" +
            "?type=tcp&security=none#Server2"
        val text = "$vless1\n$vless2"

        val result = RawShareLinksParser.parse(text)
        assertEquals(2, result.size)
    }

    @Test
    fun `should skip unknown scheme lines silently`() {
        val text = "shadowsocks://unknown@host:1080\n$sampleVless"
        val result = RawShareLinksParser.parse(text)
        assertEquals(1, result.size)
    }

    @Test
    fun `should skip malformed vless uri without throwing`() {
        val text = "vless://not-a-valid-uri-#@@@\n$sampleVless"
        val result = RawShareLinksParser.parse(text)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `should trim whitespace from each line`() {
        val text = "  $sampleVless  "
        val result = RawShareLinksParser.parse(text)
        assertEquals(1, result.size)
    }

    @Test
    fun `should handle mixed valid and invalid lines`() {
        val text = buildString {
            appendLine("# comment")
            appendLine("")
            appendLine("vless://aaaaaaaa-1111-1111-1111-aaaaaaaaaaaa@s1.example.com:443?type=tcp#S1")
            appendLine("not-a-link")
            appendLine("vless://bbbbbbbb-2222-2222-2222-bbbbbbbbbbbb@s2.example.com:443?type=tcp#S2")
        }

        val result = RawShareLinksParser.parse(text)
        assertEquals(2, result.size)
    }

    @Test
    fun `should extract server address from parsed vless bean`() {
        val result = RawShareLinksParser.parse(sampleVless)
        val bean = result.first() as VLESSBean
        assertEquals("proxy.example.com", bean.serverAddress)
    }
}
