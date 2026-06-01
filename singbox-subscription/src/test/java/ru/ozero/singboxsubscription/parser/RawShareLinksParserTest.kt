package ru.ozero.singboxsubscription.parser

import org.junit.jupiter.api.Test
import ru.ozero.singboxfmt.TrojanBean
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxfmt.VMessBean
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun `should infer reality security from clash reality opts`() {
        val yaml = """
            proxies:
              - name: Clash Reality
                type: vless
                server: reality.example.com
                port: 443
                uuid: 12345678-1234-1234-1234-123456789abc
                tls: true
                servername: front.example.com
                flow: xtls-rprx-vision
                reality-opts:
                  public-key: sample-public-key
                  short-id: abcd
                client-fingerprint: chrome
        """.trimIndent()

        val result = RawShareLinksParser.parse(yaml)

        assertEquals(1, result.size)
        val bean = result.first() as VLESSBean
        assertEquals("reality", bean.security)
        assertEquals("front.example.com", bean.sni)
        assertEquals("sample-public-key", bean.realityPublicKey)
        assertEquals("abcd", bean.realityShortId)
        assertEquals("xtls-rprx-vision", bean.flow)
    }

    @Test
    fun `should infer tls security for clash trojan without tls flag`() {
        val yaml = """
            proxies:
              - name: Clash Trojan
                type: trojan
                server: trojan.example.com
                port: 443
                password: secret
                sni: trojan-sni.example.com
        """.trimIndent()

        val result = RawShareLinksParser.parse(yaml)

        assertEquals(1, result.size)
        val bean = result.first() as TrojanBean
        assertEquals("tls", bean.security)
        assertEquals("trojan-sni.example.com", bean.sni)
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
    fun `should parse sing-box websocket early data fields without numeric type leak`() {
        val json = """
            {
              "outbounds": [
                {
                  "type": "vless",
                  "tag": "WS Early",
                  "server": "proxy.example.com",
                  "server_port": 443,
                  "uuid": "12345678-1234-1234-1234-123456789abc",
                  "transport": {
                    "type": "ws",
                    "path": "/ws",
                    "max_early_data": 2048,
                    "early_data_header_name": 1
                  }
                }
              ]
            }
        """.trimIndent()

        val result = RawShareLinksParser.parse(json)

        assertEquals(1, result.size)
        val bean = result.first() as VLESSBean
        assertEquals(2048, bean.maxEarlyData)
        assertEquals("1", bean.earlyDataHeaderName)
    }

    @Test
    fun `should parse vless uri ed as max early data and eh as header name`() {
        val uri = "vless://12345678-1234-1234-1234-123456789abc@proxy.example.com:443" +
            "?type=ws&security=tls&host=front.example.com&ed=2048&eh=Sec-WebSocket-Protocol" +
            "&allowInsecure=1#WS"

        val result = RawShareLinksParser.parse(uri)

        assertEquals(1, result.size)
        val bean = result.first() as VLESSBean
        assertEquals(2048, bean.maxEarlyData)
        assertEquals("Sec-WebSocket-Protocol", bean.earlyDataHeaderName)
        assertTrue(bean.allowInsecure)
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

    @Test
    fun `should parse sing-box vmess websocket tls outbound`() {
        val json = """
            {
              "outbounds": [
                {
                  "type": "vmess",
                  "tag": "VMess WS",
                  "server": "vmess.example.com",
                  "server_port": 8443,
                  "uuid": "12345678-1234-1234-1234-123456789abc",
                  "alter_id": 4,
                  "security": "auto",
                  "packet_encoding": "packetaddr",
                  "transport": {
                    "type": "ws",
                    "path": "/ws",
                    "headers": {
                      "Host": "front.example.com"
                    }
                  },
                  "tls": {
                    "enabled": true,
                    "server_name": "tls.example.com",
                    "alpn": ["h2", "http/1.1"],
                    "insecure": true,
                    "utls": {
                      "fingerprint": "firefox"
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        val result = RawShareLinksParser.parse(json)

        assertEquals(1, result.size)
        val bean = result.first() as VMessBean
        assertEquals("VMess WS", bean.name)
        assertEquals("vmess.example.com", bean.serverAddress)
        assertEquals(8443, bean.serverPort)
        assertEquals(4, bean.alterId)
        assertEquals("auto", bean.encryption)
        assertEquals("packetaddr", bean.packetEncoding)
        assertEquals("ws", bean.type)
        assertEquals("/ws", bean.path)
        assertEquals("front.example.com", bean.host)
        assertEquals("tls", bean.security)
        assertEquals("tls.example.com", bean.sni)
        assertEquals("h2,http/1.1", bean.alpn)
        assertEquals("firefox", bean.utlsFingerprint)
        assertTrue(bean.allowInsecure)
    }

    @Test
    fun `should parse sing-box trojan grpc outbound`() {
        val json = """
            {
              "outbounds": [
                {
                  "type": "trojan",
                  "tag": "Trojan gRPC",
                  "server": "trojan.example.com",
                  "server_port": 443,
                  "password": "secret",
                  "transport": {
                    "type": "grpc",
                    "service_name": "ozero"
                  }
                }
              ]
            }
        """.trimIndent()

        val result = RawShareLinksParser.parse(json)

        assertEquals(1, result.size)
        val bean = result.first() as TrojanBean
        assertEquals("Trojan gRPC", bean.name)
        assertEquals("trojan.example.com", bean.serverAddress)
        assertEquals(443, bean.serverPort)
        assertEquals("secret", bean.password)
        assertEquals("grpc", bean.type)
        assertEquals("ozero", bean.grpcServiceName)
    }

    @Test
    fun `should skip invalid sing-box outbounds and unknown types`() {
        val json = """
            {
              "outbounds": [
                { "type": "direct", "tag": "direct" },
                { "type": "vless", "tag": "missing-server", "server_port": 443 },
                {
                  "type": "shadowsocks",
                  "server": "ss.example.com",
                  "server_port": 8388,
                  "method": "2022-blake3-aes-128-gcm",
                  "password": "pw"
                }
              ]
            }
        """.trimIndent()

        val result = RawShareLinksParser.parse(json)

        assertEquals(1, result.size)
        assertEquals("ss.example.com", result.first().serverAddress)
    }
}
