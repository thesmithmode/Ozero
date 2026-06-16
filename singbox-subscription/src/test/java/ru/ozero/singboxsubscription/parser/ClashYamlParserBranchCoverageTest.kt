package ru.ozero.singboxsubscription.parser

import org.junit.jupiter.api.Test
import ru.ozero.singboxfmt.ShadowsocksBean
import ru.ozero.singboxfmt.TrojanBean
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxfmt.VMessBean
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClashYamlParserBranchCoverageTest {

    @Test
    fun `parse returns empty for missing proxies malformed yaml and non list proxies`() {
        assertTrue(ClashYamlParser.parse("proxy-groups:\n  - name: direct").isEmpty())
        assertTrue(ClashYamlParser.parse("proxies: [").isEmpty())
        assertTrue(ClashYamlParser.parse("proxies:\n  name: not-a-list").isEmpty())
        assertTrue(ClashYamlParser.parse("proxies:\n  - type: vless\n    server: ''\n    port: 443").isEmpty())
        assertTrue(ClashYamlParser.parse("proxies:\n  - type: vmess\n    server: example.com\n    port: 0").isEmpty())
    }

    @Test
    fun `parse covers nested transport fallbacks and h2 normalization`() {
        val yaml = """
            proxies:
              - name: websocket
                type: vless
                server: ws.example.com
                port: 443
                uuid: uuid
                ws-opts:
                  path: /ws
                  headers:
                    Host: front.example.com
              - name: http-list-host
                type: vmess
                server: h2.example.com
                port: "443"
                uuid: uuid
                h2-opts:
                  path: /h2
                  host:
                    - a.example.com
                    - b.example.com
              - name: grpc-nested
                type: trojan
                server: grpc.example.com
                port: 443
                password: secret
                grpc-opts:
                  grpc-service-name: svc
        """.trimIndent()

        val parsed = ClashYamlParser.parse(yaml)

        assertEquals(3, parsed.size)
        val ws = parsed[0] as VLESSBean
        assertEquals("ws", ws.type)
        assertEquals("/ws", ws.path)
        assertEquals("front.example.com", ws.host)
        val h2 = parsed[1] as VMessBean
        assertEquals("http", h2.type)
        assertEquals("/h2", h2.path)
        assertEquals("a.example.com,b.example.com", h2.host)
        val grpc = parsed[2] as TrojanBean
        assertEquals("grpc", grpc.type)
        assertEquals("svc", grpc.grpcServiceName)
    }

    @Test
    fun `parse covers aliases defaults unknown types and shadowsocks map method`() {
        val yaml = """
            proxies:
              - plain-string-entry
              - name: unsupported
                type: tuic
                server: tuic.example.com
                port: 443
              - name: vless-aliases
                type: vless
                server: alias.example.com
                port: 443
                uuid: uuid
                net: xhttp
                reality: 1
                reality-opts:
                  public-key: reality-key
                  short-id: ab
                servername: sni.example.com
                client-fingerprint: chrome
                httpupgrade-opts:
                  path: /upgrade
                  host: upgrade.example.com
              - name: shadowsocks-map-method
                type: shadowsocks
                server: ss.example.com
                port: 8388
                cipher:
                  method: aes-128-gcm
                  ignored: value
                password: secret
        """.trimIndent()

        val parsed = ClashYamlParser.parse(yaml)

        assertEquals(2, parsed.size)
        val vless = parsed[0] as VLESSBean
        assertEquals("splithttp", vless.type)
        assertEquals("reality", vless.security)
        assertEquals("reality-key", vless.realityPublicKey)
        assertEquals("ab", vless.realityShortId)
        assertEquals("sni.example.com", vless.sni)
        assertEquals("chrome", vless.realityFingerprint)
        val ss = parsed[1] as ShadowsocksBean
        assertEquals("aes-128-gcm", ss.method)
        assertEquals("secret", ss.password)
    }

    @Test
    fun `parse infers httpupgrade transport and string fallback cipher map`() {
        val yaml = """
            proxies:
              - name: upgrade
                type: trojan
                server: upgrade.example.com
                port: 443
                password: secret
                httpupgrade-opts:
                  path: /u
                  host: front.example.com
              - name: ss-map-fallback
                type: ss
                server: ss.example.com
                port: 8388
                cipher:
                  mode: cfb
                  rounds: 2
                password: secret
        """.trimIndent()

        val parsed = ClashYamlParser.parse(yaml)

        assertEquals(2, parsed.size)
        val upgrade = parsed[0] as TrojanBean
        assertEquals("httpupgrade", upgrade.type)
        assertEquals("/u", upgrade.path)
        assertEquals("front.example.com", upgrade.host)
        val ss = parsed[1] as ShadowsocksBean
        assertEquals("mode=cfb,rounds=2", ss.method)
    }

    @Test
    fun `parse covers scalar alpn boolean false tls and numeric shadowsocks fields`() {
        val yaml = """
            proxies:
              - name: vmess-scalar-alpn
                type: vmess
                server: vmess.example.com
                port: 443
                uuid: uuid
                network: h2
                tls: false
                alpn: h2
                skip-cert-verify: yes
                fingerprint: firefox
              - name: ss-number-cipher
                type: ss
                server: ss-number.example.com
                port: 8388
                cipher: 2022
                password: 12345
              - name: ss-boolean-cipher
                type: ss
                server: ss-bool.example.com
                port: 8388
                cipher: true
                password: false
        """.trimIndent()

        val parsed = ClashYamlParser.parse(yaml)

        assertEquals(3, parsed.size)
        val vmess = parsed[0] as VMessBean
        assertEquals("http", vmess.type)
        assertEquals("none", vmess.security)
        assertEquals("h2", vmess.alpn)
        assertTrue(vmess.allowInsecure)
        assertEquals("firefox", vmess.utlsFingerprint)
        assertEquals("2022", (parsed[1] as ShadowsocksBean).method)
        assertEquals("12345", (parsed[1] as ShadowsocksBean).password)
        assertEquals("true", (parsed[2] as ShadowsocksBean).method)
        assertEquals("false", (parsed[2] as ShadowsocksBean).password)
    }

    @Test
    fun `parse covers explicit security and grpc direct serviceName aliases`() {
        val yaml = """
            proxies:
              - name: vless-explicit-security
                type: vless
                server: vless.example.com
                port: "443"
                uuid: uuid
                security: xtls
                net: grpc
                serviceName: direct-svc
                allowInsecure: 1
              - name: trojan-default-tls
                type: trojan
                server: trojan.example.com
                port: 443
                password: secret
        """.trimIndent()

        val parsed = ClashYamlParser.parse(yaml)

        assertEquals(2, parsed.size)
        val vless = parsed[0] as VLESSBean
        assertEquals("xtls", vless.security)
        assertEquals("grpc", vless.type)
        assertEquals("direct-svc", vless.grpcServiceName)
        assertTrue(vless.allowInsecure)
        val trojan = parsed[1] as TrojanBean
        assertEquals("tls", trojan.security)
    }
}
