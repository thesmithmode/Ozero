package ru.ozero.singboxsubscription.parser

import org.junit.jupiter.api.Test
import java.util.Base64
import ru.ozero.singboxfmt.ShadowsocksBean
import ru.ozero.singboxfmt.TrojanBean
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxfmt.VMessBean
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Suppress("LargeClass")
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
    fun `should preserve unescaped spaces in share link names`() {
        val vless = "vless://aaaaaaaa-1111-1111-1111-aaaaaaaaaaaa@s1.example.com:443" +
            "?type=tcp&security=none#Aetris Netherlands 01"
        val trojan = "trojan://secret@trojan.example.com:443?security=tls#Trojan City Node"
        val text = "$vless\n$trojan"

        val result = RawShareLinksParser.parse(text)

        assertEquals(2, result.size)
        assertEquals("Aetris Netherlands 01", result[0].name)
        assertEquals("Trojan City Node", result[1].name)
    }

    @Test
    fun `should split adjacent share links without cutting fragment words`() {
        val vless1 = "vless://aaaaaaaa-1111-1111-1111-aaaaaaaaaaaa@s1.example.com:443" +
            "?type=tcp&security=none#First Node"
        val vless2 = "vless://bbbbbbbb-2222-2222-2222-bbbbbbbbbbbb@s2.example.com:443" +
            "?type=tcp&security=none#Second Node"

        val result = RawShareLinksParser.parse("$vless1 $vless2")

        assertEquals(2, result.size)
        assertEquals("First Node", result[0].name)
        assertEquals("Second Node", result[1].name)
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
    fun `should parse vless xhttp link with raw braces query fallback`() {
        val uri = "vless://12345678-1234-1234-1234-123456789abc@xhttp.example.com:443" +
            "?type=xhttp&security=reality&host=front.example.com&path=/x&mode=stream-up" +
            "&pbk=sample-public-key&sid=abcd&extra={bad-but-common}#XHTTP"

        val result = RawShareLinksParser.parse(uri)

        assertEquals(1, result.size)
        val bean = result.first() as VLESSBean
        assertEquals("xhttp.example.com", bean.serverAddress)
        assertEquals(443, bean.serverPort)
        assertEquals("splithttp", bean.type)
        assertEquals("front.example.com", bean.host)
        assertEquals("/x", bean.path)
        assertEquals("reality", bean.security)
        assertEquals("sample-public-key", bean.realityPublicKey)
        assertEquals("abcd", bean.realityShortId)
    }

    @Test
    fun `should parse fallback authority before path when raw query contains braces`() {
        val uri = "vless://12345678-1234-1234-1234-123456789abc@example.com:443/ws" +
            "?type=ws&path=/ws&extra={bad-but-common}#WS"

        val result = RawShareLinksParser.parse(uri)

        assertEquals(1, result.size)
        val bean = result.first() as VLESSBean
        assertEquals("example.com", bean.serverAddress)
        assertEquals(443, bean.serverPort)
        assertEquals("ws", bean.type)
        assertEquals("/ws", bean.path)
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

    @Test
    fun `should parse vmess trojan and shadowsocks share links`() {
        val vmessJson = """
            {"ps":"VM","add":"vm.example.com","port":"443","id":"uuid","aid":"0","net":"tcp","tls":"tls"}
        """.trimIndent()
        val vmess = "vmess://" + Base64.getUrlEncoder().withoutPadding().encodeToString(vmessJson.toByteArray())
        val trojan = "trojan://secret@trojan.example.com:443?type=ws&host=front.example.com&path=/ws#TR"
        val ssUser = Base64.getUrlEncoder().withoutPadding().encodeToString("aes-128-gcm:pwd".toByteArray())
        val ss = "ss://$ssUser@ss.example.com:8388?plugin=v2ray-plugin#SS"

        val result = RawShareLinksParser.parse("$vmess $trojan\n$ss")

        assertEquals(3, result.size)
        assertEquals("vm.example.com", (result[0] as VMessBean).serverAddress)
        assertEquals("trojan.example.com", (result[1] as TrojanBean).serverAddress)
        assertEquals("ss.example.com", (result[2] as ShadowsocksBean).serverAddress)
    }

    @Test
    fun `should ignore non object sing-box outbounds and disabled tls`() {
        val json = """
            {
              "outbounds": [
                "bad",
                {
                  "type": "vless",
                  "tag": "No TLS",
                  "server": "plain.example.com",
                  "server_port": 80,
                  "uuid": "12345678-1234-1234-1234-123456789abc",
                  "transport": { "type": "xhttp", "host": "front.example.com", "path": "/x" },
                  "tls": { "enabled": false, "server_name": "ignored.example.com" }
                }
              ]
            }
        """.trimIndent()

        val result = RawShareLinksParser.parse(json)

        assertEquals(1, result.size)
        val bean = result.first() as VLESSBean
        assertEquals("No TLS", bean.name)
        assertEquals("splithttp", bean.type)
        assertEquals("none", bean.security)
        assertEquals("", bean.sni)
    }

    @Test
    fun `should parse clash ss and vmess nested transport variants`() {
        val yaml = """
            proxies:
              - name: SS Clash
                type: ss
                server: ss.example.com
                port: 8388
                cipher: aes-128-gcm
                password: pwd
                plugin: v2ray-plugin
                plugin-opts: mode=websocket
              - name: VMess HTTP
                type: vmess
                server: vm.example.com
                port: 443
                uuid: 12345678-1234-1234-1234-123456789abc
                security: zero
                network: h2
                h2-opts:
                  path: /h2
                  host: [a.example.com, b.example.com]
        """.trimIndent()

        val result = RawShareLinksParser.parse(yaml)

        assertEquals(2, result.size)
        val ss = result[0] as ShadowsocksBean
        assertEquals("aes-128-gcm", ss.method)
        assertEquals("v2ray-plugin", ss.plugin)
        val vmess = result[1] as VMessBean
        assertEquals("http", vmess.type)
        assertEquals("/h2", vmess.path)
        assertEquals("a.example.com,b.example.com", vmess.host)
        assertEquals("zero", vmess.encryption)
    }

    @Test
    fun `should skip malformed clash yaml and invalid proxy entries`() {
        assertTrue(RawShareLinksParser.parse("proxies: [").isEmpty())
        assertTrue(RawShareLinksParser.parse("proxies:\n  - direct").isEmpty())
        assertTrue(
            RawShareLinksParser.parse(
                """
                proxies:
                  - name: Missing port
                    type: vless
                    server: no-port.example.com
                    uuid: 12345678-1234-1234-1234-123456789abc
                """.trimIndent(),
            ).isEmpty(),
        )
    }

    @Test
    fun `should parse clash transport aliases and boolean variants`() {
        val yaml = """
            proxies:
              - name: WS Clash
                type: vless
                server: ws.example.com
                port: "443"
                uuid: 12345678-1234-1234-1234-123456789abc
                network: ws
                tls: 1
                skip-cert-verify: yes
                ws-opts:
                  path: /socket
                  headers:
                    Host: front.example.com
              - name: HTTP Upgrade
                type: vmess
                server: hu.example.com
                port: 8443
                uuid: 12345678-1234-1234-1234-123456789abc
                alter-id: "2"
                network: httpupgrade
                httpupgrade-opts:
                  path: /upgrade
                  host: upgrade.example.com
              - name: gRPC Clash
                type: trojan
                server: grpc.example.com
                port: 443
                password: secret
                network: grpc
                grpc-opts:
                  grpc-service-name: svc
                allowInsecure: true
        """.trimIndent()

        val result = RawShareLinksParser.parse(yaml)

        assertEquals(3, result.size)
        val ws = result[0] as VLESSBean
        assertEquals("ws", ws.type)
        assertEquals("/socket", ws.path)
        assertEquals("front.example.com", ws.host)
        assertEquals("tls", ws.security)
        assertTrue(ws.allowInsecure)
        val httpUpgrade = result[1] as VMessBean
        assertEquals("httpupgrade", httpUpgrade.type)
        assertEquals("/upgrade", httpUpgrade.path)
        assertEquals("upgrade.example.com", httpUpgrade.host)
        assertEquals(2, httpUpgrade.alterId)
        val grpc = result[2] as TrojanBean
        assertEquals("grpc", grpc.type)
        assertEquals("svc", grpc.grpcServiceName)
        assertTrue(grpc.allowInsecure)
    }

    @Test
    fun `should parse clash inline reality keys and scalar alpn`() {
        val yaml = """
            proxies:
              - name: Inline Reality
                type: vless
                server: reality-inline.example.com
                port: 443
                uuid: 12345678-1234-1234-1234-123456789abc
                network: xhttp
                reality: true
                public-key: inline-public-key
                short-id: ef01
                alpn: h3
                fp: safari
        """.trimIndent()

        val result = RawShareLinksParser.parse(yaml)

        assertEquals(1, result.size)
        val bean = result.first() as VLESSBean
        assertEquals("splithttp", bean.type)
        assertEquals("reality", bean.security)
        assertEquals("inline-public-key", bean.realityPublicKey)
        assertEquals("ef01", bean.realityShortId)
        assertEquals("h3", bean.alpn)
        assertEquals("safari", bean.realityFingerprint)
    }

    @Test
    fun `should parse sing-box tls without reality and header fallbacks`() {
        val json = """
            {
              "outbounds": [
                {
                  "type": "vless",
                  "tag": "TLS WS",
                  "server": "tls-ws.example.com",
                  "server_port": 443,
                  "uuid": "12345678-1234-1234-1234-123456789abc",
                  "transport": {
                    "type": "ws",
                    "host": "transport.example.com",
                    "headers": { "Host": "" },
                    "max_early_data": "bad"
                  },
                  "tls": {
                    "enabled": true,
                    "server_name": "sni.example.com",
                    "alpn": ["", "h2"],
                    "insecure": false,
                    "reality": { "enabled": false },
                    "utls": { "fingerprint": "edge" }
                  }
                }
              ]
            }
        """.trimIndent()

        val result = RawShareLinksParser.parse(json)

        assertEquals(1, result.size)
        val bean = result.first() as VLESSBean
        assertEquals("tls", bean.security)
        assertEquals("transport.example.com", bean.host)
        assertEquals(0, bean.maxEarlyData)
        assertEquals("sni.example.com", bean.sni)
        assertEquals("h2", bean.alpn)
        assertEquals("edge", bean.utlsFingerprint)
    }

    @Test
    fun `should return empty for json without outbounds or malformed root`() {
        assertTrue(RawShareLinksParser.parse("""{"route":{}}""").isEmpty())
        assertTrue(RawShareLinksParser.parse("""["not", "object"]""").isEmpty())
    }

    @Test
    fun `should parse sing-box shadowsocks plugin fields and fallback transport type`() {
        val json = """
            {
              "outbounds": [
                {
                  "type": "shadowsocks",
                  "tag": "SS Plugin",
                  "server": "ss-plugin.example.com",
                  "server_port": 8388,
                  "method": "aes-128-gcm",
                  "password": "secret",
                  "plugin": "v2ray-plugin",
                  "plugin_opts": "mode=websocket"
                },
                {
                  "type": "vmess",
                  "tag": "Default Transport",
                  "server": "default.example.com",
                  "server_port": 443,
                  "uuid": "12345678-1234-1234-1234-123456789abc",
                  "transport": {
                    "path": "/fallback"
                  }
                }
              ]
            }
        """.trimIndent()

        val result = RawShareLinksParser.parse(json)

        assertEquals(2, result.size)
        val ss = result[0] as ShadowsocksBean
        assertEquals("SS Plugin", ss.name)
        assertEquals("v2ray-plugin", ss.plugin)
        assertEquals("mode=websocket", ss.pluginOpts)
        val vmess = result[1] as VMessBean
        assertEquals("/fallback", vmess.path)
        assertEquals("tcp", vmess.type)
    }

    @Test
    fun `should return empty for sing-box outbounds with blank server values`() {
        val json = """
            {
              "outbounds": [
                {
                  "type": "vless",
                  "tag": "Blank server",
                  "server": "",
                  "server_port": 443,
                  "uuid": "12345678-1234-1234-1234-123456789abc"
                },
                {
                  "type": "vmess",
                  "tag": "Blank port",
                  "server": "example.com",
                  "server_port": 0,
                  "uuid": "12345678-1234-1234-1234-123456789abc"
                }
              ]
            }
        """.trimIndent()

        assertTrue(RawShareLinksParser.parse(json).isEmpty())
    }
}
