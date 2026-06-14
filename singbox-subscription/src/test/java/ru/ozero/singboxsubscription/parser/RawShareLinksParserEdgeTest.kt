package ru.ozero.singboxsubscription.parser

import org.junit.jupiter.api.Test
import ru.ozero.singboxfmt.ShadowsocksBean
import ru.ozero.singboxfmt.TrojanBean
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxfmt.VMessBean
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RawShareLinksParserEdgeTest {

    @Test
    fun `should parse sing-box outbound validation edge cases`() {
        val json = """
            {
              "outbounds": [
                { "type": "trojan", "server": "", "server_port": 443, "password": "x" },
                { "type": "vmess", "server": "no-port.example.com", "uuid": "id" },
                { "type": "vless", "server": "bad-port.example.com", "server_port": 0, "uuid": "id" },
                {
                  "type": "vmess",
                  "tag": "Plain VMess",
                  "server": "plain-vmess.example.com",
                  "server_port": 80,
                  "uuid": "12345678-1234-1234-1234-123456789abc"
                },
                {
                  "type": "trojan",
                  "tag": "TLS No UTLS",
                  "server": "tls-trojan.example.com",
                  "server_port": 443,
                  "password": "secret",
                  "tls": { "enabled": true, "server_name": "front.example.com" }
                }
              ]
            }
        """.trimIndent()

        val result = RawShareLinksParser.parse(json)

        assertEquals(2, result.size)
        val vmess = result[0] as VMessBean
        assertEquals("tcp", vmess.type)
        assertEquals("none", vmess.security)
        val trojan = result[1] as TrojanBean
        assertEquals("tls", trojan.security)
        assertEquals("", trojan.utlsFingerprint)
    }

    @Test
    fun `should parse clash explicit security missing tls and scalar helpers`() {
        val yaml = """
            proxies:
              - name: Explicit Security
                type: vless
                server: explicit.example.com
                port: 443
                uuid: 12345678-1234-1234-1234-123456789abc
                security: xtls
                path: /direct
                host: direct.example.com
                service-name: direct-svc
              - name: TLS Disabled
                type: vmess
                server: disabled.example.com
                port: 80
                uuid: 12345678-1234-1234-1234-123456789abc
                tls: false
              - name: Map String
                type: ss
                server: ss-map.example.com
                port: 8388
                cipher:
                  method: aes-128-gcm
                password: pwd
              - name: String Bool False
                type: trojan
                server: false.example.com
                port: 443
                password: pwd
                skip-cert-verify: no
                allowInsecure: 0
        """.trimIndent()

        val result = RawShareLinksParser.parse(yaml)

        assertEquals(4, result.size)
        val explicit = result[0] as VLESSBean
        assertEquals("xtls", explicit.security)
        assertEquals("/direct", explicit.path)
        assertEquals("direct.example.com", explicit.host)
        assertEquals("direct-svc", explicit.grpcServiceName)
        val disabled = result[1] as VMessBean
        assertEquals("none", disabled.security)
        val ss = result[2] as ShadowsocksBean
        assertEquals("aes-128-gcm", ss.method)
        val trojan = result[3] as TrojanBean
        assertEquals("tls", trojan.security)
        assertFalse(trojan.allowInsecure)
    }

    @Test
    fun `should skip clash non list proxies and unknown proxy type`() {
        assertTrue(RawShareLinksParser.parse("proxies:\n  name: not-list").isEmpty())
        assertTrue(
            RawShareLinksParser.parse(
                """
                proxies:
                  - name: Unknown
                    type: direct
                    server: direct.example.com
                    port: 1
                """.trimIndent(),
            ).isEmpty(),
        )
    }

    @Test
    fun `should skip malformed links and invalid sing-box json`() {
        val text = """
            # comments and malformed tokens should not crash parser
            just-a-comment
            vless://
            vmess://not-a-valid-vmess
        """.trimIndent()

        assertTrue(RawShareLinksParser.parse(text).isEmpty())

        val malformedJson = """
            {"outbounds":
            "not-array"
        """.trimIndent()

        assertTrue(RawShareLinksParser.parse(malformedJson).isEmpty())
    }

    @Test
    fun `should ignore empty tokens from multiple spaces in share link lines`() {
        val text = """
            vless://12345678-1234-1234-1234-123456789abc@vless.example.com:443?type=tcp  trojan://secret@trojan.example.com:443
            # comment to ignore
        """.trimIndent()

        val result = RawShareLinksParser.parse(text)

        assertEquals(2, result.size)
        assertEquals("vless.example.com", result[0].serverAddress)
        assertEquals("trojan.example.com", result[1].serverAddress)
    }

    @Test
    fun `should parse valid sing-box outbounds and skip unknown type`() {
        val json = """
            {
              "outbounds": [
                "not-an-object",
                { "type": "direct", "tag": "skip", "server": "skip.example.com" },
                {
                  "type": "vless",
                  "tag": "keep-vless",
                  "server": "vless.example.com",
                  "server_port": 443,
                  "uuid": "12345678-1234-1234-1234-123456789abc"
                },
                {
                  "type": "vmess",
                  "tag": "keep-vmess",
                  "server": "vmess.example.com",
                  "server_port": 443,
                  "uuid": "12345678-1234-1234-1234-123456789abc",
                  "transport": {
                    "type": "xhttp",
                    "path": "/x",
                    "headers": { "Host": "front.example.com" },
                    "max_early_data": 7,
                    "early_data_header_name": "Early"
                  },
                  "tls": {
                    "enabled": true,
                    "server_name": "vmess-sni.example.com",
                    "alpn": ["h2", "", "http/1.1"],
                    "insecure": true,
                    "utls": { "fingerprint": "firefox" }
                  }
                },
                {
                  "type": "vless",
                  "tag": "reality-no-utls",
                  "server": "fallback.example.com",
                  "server_port": 443,
                  "uuid": "12345678-1234-1234-1234-123456789abc",
                  "tls": {
                    "enabled": true,
                    "reality": {
                      "enabled": true,
                      "public_key": "public-key",
                      "short_id": "short"
                    }
                  }
                },
                {
                  "type": "vless",
                  "tag": "reality-disabled",
                  "server": "disabled.example.com",
                  "server_port": 443,
                  "uuid": "12345678-1234-1234-1234-123456789abc",
                  "tls": {
                    "enabled": true,
                    "reality": {
                      "enabled": false,
                      "public_key": "unused-key",
                      "short_id": "unused"
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        val result = RawShareLinksParser.parse(json)

        assertEquals(4, result.size)
        assertEquals("VLESSBean", result[0]::class.simpleName)
        assertEquals("VMessBean", result[1]::class.simpleName)
        assertEquals("VLESSBean", result[2]::class.simpleName)
        assertEquals("VLESSBean", result[3]::class.simpleName)

        val vmess = result[1] as VMessBean
        assertEquals("splithttp", vmess.type)
        assertEquals("/x", vmess.path)
        assertEquals("front.example.com", vmess.host)
        assertEquals(7, vmess.maxEarlyData)
        assertEquals("Early", vmess.earlyDataHeaderName)
        assertEquals("tls", vmess.security)
        assertEquals("vmess-sni.example.com", vmess.sni)
        assertEquals("h2,http/1.1", vmess.alpn)
        assertTrue(vmess.allowInsecure)
        assertEquals("firefox", vmess.utlsFingerprint)

        val vlessNoUtls = result[2] as VLESSBean
        assertEquals("reality", vlessNoUtls.security)
        assertEquals("public-key", vlessNoUtls.realityPublicKey)
        assertEquals("short", vlessNoUtls.realityShortId)
        assertEquals("chrome", vlessNoUtls.realityFingerprint)

        val vlessRealityDisabled = result[3] as VLESSBean
        assertEquals("tls", vlessRealityDisabled.security)
        assertEquals("chrome", vlessRealityDisabled.realityFingerprint)
    }

    @Test
    fun `should parse sing-box grpc transport and shadowsocks plugin fields`() {
        val json = """
            {
              "outbounds": [
                {
                  "type": "trojan",
                  "tag": "grpc-trojan",
                  "server": "trojan.example.com",
                  "server_port": 443,
                  "password": "secret",
                  "transport": {
                    "type": "grpc",
                    "service_name": "svc"
                  }
                },
                {
                  "type": "shadowsocks",
                  "tag": "ss",
                  "server": "ss.example.com",
                  "server_port": 8388,
                  "method": "aes-128-gcm",
                  "password": "pwd",
                  "plugin": "v2ray-plugin",
                  "plugin_opts": "tls;host=front.example.com"
                }
              ]
            }
        """.trimIndent()

        val result = RawShareLinksParser.parse(json)

        assertEquals(2, result.size)
        val trojan = result[0] as TrojanBean
        assertEquals("grpc", trojan.type)
        assertEquals("svc", trojan.grpcServiceName)
        val ss = result[1] as ShadowsocksBean
        assertEquals("v2ray-plugin", ss.plugin)
        assertEquals("tls;host=front.example.com", ss.pluginOpts)
    }
}
