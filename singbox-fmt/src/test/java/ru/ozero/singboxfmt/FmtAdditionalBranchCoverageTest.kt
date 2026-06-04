package ru.ozero.singboxfmt

import org.junit.jupiter.api.Test
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FmtAdditionalBranchCoverageTest {

    @Test
    fun `format parsers reject wrong schemes and cover tls json branches`() {
        assertFailsWith<IllegalArgumentException> { V2RayFmt.parseVLESS("vmess://id@example.com") }
        assertFailsWith<IllegalArgumentException> { V2RayFmt.parseVMess("vless://id@example.com") }
        assertFailsWith<IllegalArgumentException> { V2RayFmt.parseTrojan("vless://pwd@example.com") }
        assertFailsWith<IllegalArgumentException> { V2RayFmt.parseShadowsocks("vless://x") }

        val json = """
            {
              "add":"vm.example.com",
              "port":"8443",
              "id":"id",
              "aid":"2",
              "scy":"chacha20",
              "net":"h2",
              "host":"front",
              "path":"/h",
              "type":"http",
              "tls":"tls",
              "sni":"sni.example.com",
              "alpn":"h2",
              "fp":"chrome",
              "ps":"Tls Json"
            }
        """.trimIndent()
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())
        val bean = V2RayFmt.parseVMess("vmess://$encoded")

        assertEquals("vm.example.com", bean.serverAddress)
        assertEquals(8443, bean.serverPort)
        assertEquals(2, bean.alterId)
        assertEquals("chacha20", bean.encryption)
        assertEquals("http", bean.type)
        assertEquals("tls", bean.security)
        assertEquals("sni.example.com", bean.sni)
        assertEquals("Tls Json", bean.name)
    }

    @Test
    fun `vless and shadowsocks cover filled optional branch values`() {
        val vlessUri = "vless://id@vl.example.com:8443" +
            "?type=xhttp&host=front&path=/split&mode=stream-up" +
            "&ed=512&eh=Header&flow=xtls-rprx-vision" +
            "&encryption=none&security=reality&pbk=pk&sid=sid&fp=firefox"
        val vless = V2RayFmt.parseVLESS(vlessUri)
        assertEquals("splithttp", vless.type)
        assertEquals("front", vless.host)
        assertEquals("/split", vless.path)
        assertEquals("stream-up", vless.splithttpMode)
        assertEquals("xtls-rprx-vision", vless.flow)
        assertEquals("reality", vless.security)
        assertEquals("pk", vless.realityPublicKey)
        assertEquals("sid", vless.realityShortId)
        assertEquals("firefox", vless.realityFingerprint)

        val full = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("aes-128-gcm:pwd@ss.example.com:8388".toByteArray())
        val ss = V2RayFmt.parseShadowsocks("ss://$full#Full")
        assertEquals("aes-128-gcm", ss.method)
        assertEquals("pwd", ss.password)
        assertEquals("ss.example.com", ss.serverAddress)
        assertEquals(8388, ss.serverPort)
        assertEquals("Full", ss.name)
    }

    @Test
    fun `format parsers cover default optional branches`() {
        val vless = V2RayFmt.parseVLESS("vless://id@vl.example.com:443?type=grpc")
        assertEquals("", vless.grpcServiceName)
        assertEquals("", vless.name)

        val h2 = V2RayFmt.parseVLESS("vless://id@vl.example.com:443?type=h2")
        assertEquals("http", h2.type)
        assertEquals("", h2.host)
        assertEquals("/", h2.path)

        val split = V2RayFmt.parseVLESS("vless://id@vl.example.com:443?type=xhttp")
        assertEquals("splithttp", split.type)
        assertEquals("", split.host)
        assertEquals("/", split.path)
        assertEquals("auto", split.splithttpMode)

        val kcp = V2RayFmt.parseVLESS("vless://id@vl.example.com:443?type=mkcp")
        assertEquals("kcp", kcp.type)
        assertEquals("none", kcp.headerType)

        val ss = V2RayFmt.parseShadowsocks("ss://method:pwd@ss.example.com:bad")
        assertEquals(443, ss.serverPort)
        assertEquals("", ss.plugin)
    }
}
