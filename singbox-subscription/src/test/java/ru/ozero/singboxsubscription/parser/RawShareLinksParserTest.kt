package ru.ozero.singboxsubscription.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.ozero.singboxfmt.TrojanBean
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
    fun `should parse space-separated links on the same line`() {
        val vless1 = "vless://aaaaaaaa-1111-1111-1111-aaaaaaaaaaaa@s1.example.com:443?type=tcp#S1"
        val vless2 = "vless://bbbbbbbb-2222-2222-2222-bbbbbbbbbbbb@s2.example.com:443?type=tcp#S2"
        val vless3 = "vless://cccccccc-3333-3333-3333-cccccccccccc@s3.example.com:443?type=tcp#S3"
        val text = "$vless1 $vless2\n$vless3"

        val result = RawShareLinksParser.parse(text)
        assertEquals(3, result.size)
    }

    @Test
    fun `should extract server address from parsed vless bean`() {
        val result = RawShareLinksParser.parse(sampleVless)
        val bean = result.first() as VLESSBean
        assertEquals("proxy.example.com", bean.serverAddress)
    }

    @Test
    fun `should parse vless reality outbound from sing-box json`() {
        val json = """
            {
              "outbounds": [
                {
                  "type": "vless",
                  "tag": "Sample Reality",
                  "server": "proxy.example.com",
                  "server_port": 443,
                  "uuid": "12345678-1234-1234-1234-123456789abc",
                  "flow": "xtls-rprx-vision",
                  "packet_encoding": "xudp",
                  "tls": {
                    "enabled": true,
                    "server_name": "front.example.com",
                    "utls": {
                      "enabled": true,
                      "fingerprint": "chrome"
                    },
                    "reality": {
                      "enabled": true,
                      "public_key": "sample-public-key",
                      "short_id": "abcd"
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        val result = RawShareLinksParser.parse(json)

        assertEquals(1, result.size)
        val bean = result.first() as VLESSBean
        assertEquals("Sample Reality", bean.name)
        assertEquals("proxy.example.com", bean.serverAddress)
        assertEquals(443, bean.serverPort)
        assertEquals("xtls-rprx-vision", bean.flow)
        assertEquals("xudp", bean.packetEncoding)
        assertEquals("reality", bean.security)
        assertEquals("front.example.com", bean.sni)
        assertEquals("sample-public-key", bean.realityPublicKey)
        assertEquals("abcd", bean.realityShortId)
    }

    @Test
    fun `should infer reality from clash reality opts`() {
        val yaml = """
            proxies:
              - name: Reality Clash
                type: vless
                server: proxy.example.com
                port: 443
                uuid: 12345678-1234-1234-1234-123456789abc
                tls: true
                servername: front.example.com
                reality-opts:
                  public-key: sample-public-key
                  short-id: abcd
        """.trimIndent()

        val result = RawShareLinksParser.parse(yaml)

        assertEquals(1, result.size)
        val bean = result.first() as VLESSBean
        assertEquals("reality", bean.security)
        assertEquals("sample-public-key", bean.realityPublicKey)
        assertEquals("abcd", bean.realityShortId)
    }

    @Test
    fun `should default clash trojan to tls`() {
        val yaml = """
            proxies:
              - name: Trojan Clash
                type: trojan
                server: trojan.example.com
                port: 443
                password: sample-password
                sni: front.example.com
                skip-cert-verify: true
        """.trimIndent()

        val result = RawShareLinksParser.parse(yaml)

        assertEquals(1, result.size)
        val bean = result.first() as TrojanBean
        assertEquals("tls", bean.security)
        assertEquals("front.example.com", bean.sni)
        assertTrue(bean.allowInsecure)
    }
}
