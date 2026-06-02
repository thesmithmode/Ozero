package ru.ozero.singboxsubscription.parser

import org.junit.jupiter.api.Test
import ru.ozero.singboxfmt.ShadowsocksBean
import ru.ozero.singboxfmt.TrojanBean
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxfmt.VMessBean
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Suppress("LongMethod")
class SubscriptionParserBranchCoverageTest {

    @Test
    fun `clash parser returns empty for missing key scalar root and malformed yaml`() {
        assertTrue(ClashYamlParser.parse("proxy-groups: []").isEmpty())
        assertTrue(ClashYamlParser.parse("proxies: [").isEmpty())
        assertTrue(ClashYamlParser.parse("proxies: plain").isEmpty())
    }

    @Test
    fun `clash parser reads nested transport reality tls and list fields`() {
        val yaml = """
            proxies:
              - name: Nested VLESS
                type: vless
                server: vless.example.com
                port: "443"
                uuid: 12345678-1234-1234-1234-123456789abc
                network: h2
                h2-opts:
                  path: /h2
                  host:
                    - front.example.com
                    - edge.example.com
                reality-opts:
                  public-key: pk
                  short-id: sid
                client-fingerprint: firefox
                alpn:
                  - h2
                  - http/1.1
              - name: Nested WS
                type: vmess
                server: vmess.example.com
                port: 8443
                uuid: 12345678-1234-1234-1234-123456789abc
                alter-id: "4"
                tls: yes
                skip-cert-verify: 1
                ws-opts:
                  path: /ws
                  headers:
                    Host: ws-front.example.com
              - name: Nested GRPC
                type: trojan
                server: trojan.example.com
                port: 443
                password: secret
                net: grpc
                grpc-opts:
                  grpc-service-name: svc
              - name: HTTP Upgrade
                type: shadowsocks
                server: ss.example.com
                port: 8388
                cipher: aes-256-gcm
                password: secret
                plugin-opts: mode=websocket
        """.trimIndent()

        val result = ClashYamlParser.parse(yaml)

        assertEquals(4, result.size)
        val vless = result[0] as VLESSBean
        assertEquals("http", vless.type)
        assertEquals("/h2", vless.path)
        assertEquals("front.example.com,edge.example.com", vless.host)
        assertEquals("reality", vless.security)
        assertEquals("pk", vless.realityPublicKey)
        assertEquals("sid", vless.realityShortId)
        assertEquals("firefox", vless.realityFingerprint)
        assertEquals("h2,http/1.1", vless.alpn)
        val vmess = result[1] as VMessBean
        assertEquals(4, vmess.alterId)
        assertEquals("tls", vmess.security)
        assertEquals("/ws", vmess.path)
        assertEquals("ws-front.example.com", vmess.host)
        assertTrue(vmess.allowInsecure)
        val trojan = result[2] as TrojanBean
        assertEquals("grpc", trojan.type)
        assertEquals("svc", trojan.grpcServiceName)
        val ss = result[3] as ShadowsocksBean
        assertEquals("mode=websocket", ss.pluginOpts)
    }

    @Test
    fun `raw parser ignores unsupported tokens and parses space separated links`() {
        val text = """
            # header
            ignored-token vless://id@vless.example.com:443?type=tcp
            trojan://secret@trojan.example.com:443
        """.trimIndent()

        val result = RawShareLinksParser.parse(text)

        assertEquals(2, result.size)
        assertEquals("vless.example.com", result[0].serverAddress)
        assertEquals("trojan.example.com", result[1].serverAddress)
    }

    @Test
    fun `raw parser reads singbox outbound transports tls variants and skips invalid outbounds`() {
        val json = """
            {
              "outbounds": [
                "not-object",
                { "type": "direct" },
                {
                  "type": "vless",
                  "tag": "vless",
                  "server": "vless.example.com",
                  "server_port": 443,
                  "uuid": "id",
                  "packet_encoding": "xudp",
                  "transport": {
                    "type": "xhttp",
                    "path": "/x",
                    "headers": { "Host": "front.example.com" },
                    "max_early_data": 1024,
                    "early_data_header_name": "Sec-WebSocket-Protocol"
                  },
                  "tls": {
                    "enabled": true,
                    "server_name": "sni.example.com",
                    "alpn": ["h2", ""],
                    "insecure": true,
                    "utls": { "fingerprint": "chrome" },
                    "reality": { "enabled": true, "public_key": "pk", "short_id": "sid" }
                  }
                },
                {
                  "type": "shadowsocks",
                  "tag": "ss",
                  "server": "ss.example.com",
                  "server_port": 8388,
                  "method": "aes-256-gcm",
                  "password": "secret",
                  "plugin": "v2ray-plugin",
                  "plugin_opts": "mode=websocket"
                }
              ]
            }
        """.trimIndent()

        val result = RawShareLinksParser.parse(json)

        assertEquals(2, result.size)
        val vless = result[0] as VLESSBean
        assertEquals("splithttp", vless.type)
        assertEquals("/x", vless.path)
        assertEquals("front.example.com", vless.host)
        assertEquals(1024, vless.maxEarlyData)
        assertEquals("Sec-WebSocket-Protocol", vless.earlyDataHeaderName)
        assertEquals("reality", vless.security)
        assertEquals("sni.example.com", vless.sni)
        assertEquals("h2", vless.alpn)
        assertEquals("pk", vless.realityPublicKey)
        assertEquals("sid", vless.realityShortId)
        val ss = result[1] as ShadowsocksBean
        assertEquals("v2ray-plugin", ss.plugin)
        assertEquals("mode=websocket", ss.pluginOpts)
    }

    @Test
    fun `base64 parser pads url safe input and falls back to empty on undecodable text`() {
        val link = "vless://id@vless.example.com:443?type=tcp"
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(link.toByteArray())

        assertEquals(1, Base64BundleParser.parse(encoded).size)
        assertTrue(Base64BundleParser.parse("####").isEmpty())
    }

    @Test
    fun `clash parser covers top level reality aliases and filters invalid proxies`() {
        val yaml = """
            proxies:
              - name: Reality Alias
                type: vless
                server: vless.example.com
                port: 443
                uuid: id
                network: xhttp
                pbk: pk-top
                sid: sid-top
                fp: safari
                allowInsecure: true
                httpupgrade-opts:
                  path: /upgrade
                  host: upgrade.example.com
              - name: Vmess Defaults
                type: vmess
                server: vmess.example.com
                port: "443"
                uuid: id
                aid: bad
                tls: false
              - name: Missing server
                type: trojan
                port: 443
                password: secret
              - name: Missing port
                type: ss
                server: ss.example.com
                cipher: aes-128-gcm
                password: secret
        """.trimIndent()

        val result = ClashYamlParser.parse(yaml)

        assertEquals(2, result.size)
        val vless = result[0] as VLESSBean
        assertEquals("splithttp", vless.type)
        assertEquals("reality", vless.security)
        assertEquals("pk-top", vless.realityPublicKey)
        assertEquals("sid-top", vless.realityShortId)
        assertEquals("safari", vless.realityFingerprint)
        assertTrue(vless.allowInsecure)
        val vmess = result[1] as VMessBean
        assertEquals(0, vmess.alterId)
        assertEquals("none", vmess.security)
        assertEquals("auto", vmess.encryption)
    }

    @Test
    fun `raw parser covers singbox tls disabled and transport host fallback`() {
        val json = """
            {
              "outbounds": [
                {
                  "type": "vmess",
                  "tag": "vmess",
                  "server": "vmess.example.com",
                  "server_port": 443,
                  "uuid": "id",
                  "security": "zero",
                  "transport": {
                    "type": "ws",
                    "path": "/ws",
                    "host": "direct-host.example.com"
                  },
                  "tls": { "enabled": false, "server_name": "ignored.example.com" }
                },
                {
                  "type": "trojan",
                  "tag": "trojan",
                  "server": "trojan.example.com",
                  "server_port": 443,
                  "password": "secret",
                  "transport": { "type": "grpc", "service_name": "svc" },
                  "tls": { "enabled": true, "alpn": ["", "h2"] }
                }
              ]
            }
        """.trimIndent()

        val result = RawShareLinksParser.parse(json)

        assertEquals(2, result.size)
        val vmess = result[0] as VMessBean
        assertEquals("ws", vmess.type)
        assertEquals("/ws", vmess.path)
        assertEquals("direct-host.example.com", vmess.host)
        assertEquals("none", vmess.security)
        assertEquals("zero", vmess.encryption)
        val trojan = result[1] as TrojanBean
        assertEquals("grpc", trojan.type)
        assertEquals("svc", trojan.grpcServiceName)
        assertEquals("tls", trojan.security)
        assertEquals("h2", trojan.alpn)
    }
}
