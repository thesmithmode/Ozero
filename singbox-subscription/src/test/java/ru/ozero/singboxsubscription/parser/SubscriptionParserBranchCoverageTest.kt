package ru.ozero.singboxsubscription.parser

import org.junit.jupiter.api.Test
import ru.ozero.singboxfmt.ShadowsocksBean
import ru.ozero.singboxfmt.TrojanBean
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxfmt.VMessBean
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test
    fun `raw parser falls back to clash yaml when links and json are absent`() {
        val yaml = """
            proxies:
              - name: Clash SS
                type: ss
                server: ss.example.com
                port: 8388
                method: chacha20-ietf-poly1305
                password: secret
        """.trimIndent()

        val result = RawShareLinksParser.parse(yaml)

        assertEquals(1, result.size)
        val ss = result.single() as ShadowsocksBean
        assertEquals("Clash SS", ss.name)
        assertEquals("ss.example.com", ss.serverAddress)
        assertEquals(8388, ss.serverPort)
        assertEquals("chacha20-ietf-poly1305", ss.method)
    }

    @Test
    fun `clash parser covers nested helper fallbacks and unsupported type filtering`() {
        val yaml = """
            proxies:
              - name: Unknown
                type: direct
                server: ignored.example.com
                port: 443
              - name: Uppercase Vless
                type: VLESS
                server: vless-default.example.com
                port: 443
                uuid: 12345678-1234-1234-1234-123456789abc
                tls: false
                skip-cert-verify: {}
                allowInsecure: {}
                http-opts:
                  path: /http
                  host:
                    - http-host.example.com
                    - fallback-host.example.com
                plugin-opts:
                  mode: stream-up
                  host: nested
              - name: VMess No Security
                type: vmess
                server: vmess-default.example.com
                port: "443"
                uuid: 12345678-1234-1234-1234-123456789abc
              - name: SS Method Map
                type: shadowsocks
                server: ss-map.example.com
                port: 8388
                cipher:
                  method: aes-128-gcm
                  mode: cfb
                password: secret
        """.trimIndent()

        val result = ClashYamlParser.parse(yaml)

        assertEquals(3, result.size)
        val vless = result[0] as VLESSBean
        assertEquals("http", vless.type)
        assertEquals("/http", vless.path)
        assertEquals("http-host.example.com,fallback-host.example.com", vless.host)
        assertEquals("none", vless.security)
        assertFalse(vless.allowInsecure)
        val vmess = result[1] as VMessBean
        assertEquals("auto", vmess.encryption)
        assertEquals("none", vmess.security)
        val ss = result[2] as ShadowsocksBean
        assertEquals("method=aes-128-gcm,mode=cfb", ss.method)
    }

    @Test
    fun `clash parser handles malformed root structures`() {
        assertTrue(ClashYamlParser.parse("proxies: [").isEmpty())
        assertTrue(ClashYamlParser.parse("proxies: plain").isEmpty())
        assertTrue(ClashYamlParser.parse("name: scalar").isEmpty())
    }

    @Test
    fun `clash parser covers transport fallback and bool/int edge variants`() {
        val yaml = """
            proxies:
              - name: WS fallback from ws opts
                type: vless
                server: ws-fallback.example.com
                port: 443
                uuid: 12345678-1234-1234-1234-123456789abc
                tls: true
                ws-opts:
                  path: /ws
                  headers:
                    Host: ws-host.example.com
              - name: VMess malformed alter-id and no utls
                type: vmess
                server: vmess-h2.example.com
                port: 443
                uuid: 12345678-1234-1234-1234-123456789abc
                security: none
                alter-id: bad
                h2-opts:
                  path: /h2
                  host:
                    - h2-a.example.com
                    - h2-b.example.com
                skip-cert-verify: no
        """.trimIndent()

        val result = ClashYamlParser.parse(yaml)

        assertEquals(2, result.size)
        val vless = result[0] as VLESSBean
        assertEquals("/ws", vless.path)
        assertEquals("ws-host.example.com", vless.host)
        assertEquals("tls", vless.security)
        assertFalse(vless.allowInsecure)
        val vmess = result[1] as VMessBean
        assertEquals(0, vmess.alterId)
        assertEquals("/h2", vmess.path)
        assertEquals("h2-a.example.com,h2-b.example.com", vmess.host)
        assertEquals("none", vmess.security)
    }
}
