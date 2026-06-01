package ru.ozero.singboxfmt

import org.junit.jupiter.api.Test
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class V2RayFmtCoverageTest {

    @Test
    fun `parse vless websocket reality uri`() {
        val bean = V2RayFmt.parseVLESS(
            "vless://uuid@example.com:8443?type=ws&security=reality&sni=sni.example.com" +
                "&host=front.example.com&path=%2Fws&flow=xtls-rprx-vision&pbk=pk&sid=ab" +
                "&fp=chrome&allowInsecure=1&ed=2048&eh=Sec-WebSocket-Protocol#VLESS",
        )

        assertEquals("uuid", bean.uuid)
        assertEquals("example.com", bean.serverAddress)
        assertEquals(8443, bean.serverPort)
        assertEquals("VLESS", bean.displayName())
        assertEquals("ws", bean.type)
        assertEquals("front.example.com", bean.host)
        assertEquals("/ws", bean.path)
        assertEquals("xtls-rprx-vision", bean.flow)
        assertEquals("reality", bean.security)
        assertEquals("sni.example.com", bean.sni)
        assertEquals("pk", bean.realityPublicKey)
        assertEquals("ab", bean.realityShortId)
        assertEquals("chrome", bean.realityFingerprint)
        assertEquals(2048, bean.maxEarlyData)
        assertEquals("Sec-WebSocket-Protocol", bean.earlyDataHeaderName)
        assertTrue(bean.allowInsecure)
    }

    @Test
    fun `parse vless transports and defaults`() {
        val http = V2RayFmt.parseVLESS("vless://id@h?type=h2&host=a,b&path=%2Fh")
        assertEquals("http", http.type)
        assertEquals("a,b", http.host)
        assertEquals("/h", http.path)
        assertEquals(443, http.serverPort)
        assertEquals("none", http.encryption)

        val grpc = V2RayFmt.parseVLESS("vless://id@h?type=grpc&serviceName=svc")
        assertEquals("grpc", grpc.type)
        assertEquals("svc", grpc.grpcServiceName)

        val split = V2RayFmt.parseVLESS("vless://id@h?type=xhttp&mode=stream-up&host=front&path=/x")
        assertEquals("splithttp", split.type)
        assertEquals("stream-up", split.splithttpMode)

        val kcp = V2RayFmt.parseVLESS("vless://id@h?type=kcp&headerType=srtp&seed=seed")
        assertEquals("kcp", kcp.type)
        assertEquals("srtp", kcp.headerType)
        assertEquals("seed", kcp.mKcpSeed)

        val quic = V2RayFmt.parseVLESS("vless://id@h?type=quic&headerType=utp&quicSecurity=aes-128-gcm&key=k")
        assertEquals("quic", quic.type)
        assertEquals("utp", quic.headerType)
        assertEquals("aes-128-gcm", quic.quicSecurity)
        assertEquals("k", quic.quicKey)
    }

    @Test
    fun `parse vmess json and standard uri`() {
        val json = """
            {"v":"2","ps":"vmess-json","add":"vm.example.com","port":"8443","id":"uuid",
            "aid":"4","scy":"","net":"ws","host":"front.example.com","path":"/ws",
            "type":"none","tls":"tls","sni":"tls.example.com","alpn":"h2","fp":"firefox"}
        """.trimIndent()
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())
        val fromJson = V2RayFmt.parseVMess("vmess://$encoded")

        assertEquals("vmess-json", fromJson.name)
        assertEquals("vm.example.com", fromJson.serverAddress)
        assertEquals(8443, fromJson.serverPort)
        assertEquals(4, fromJson.alterId)
        assertEquals("auto", fromJson.encryption)
        assertEquals("ws", fromJson.type)
        assertEquals("tls", fromJson.security)
        assertEquals("tls.example.com", fromJson.sni)
        assertEquals("firefox", fromJson.utlsFingerprint)

        val std = V2RayFmt.parseVMess(
            "vmess://uuid@vm.example.com:443?type=tcp&headerType=http&host=front&path=/tcp" +
                "&security=tls&allow_insecure=true#std",
        )
        assertEquals("uuid", std.uuid)
        assertEquals("tcp", std.type)
        assertEquals("http", std.headerType)
        assertEquals("front", std.host)
        assertEquals("/tcp", std.path)
        assertTrue(std.allowInsecure)
    }

    @Test
    fun `parse trojan and shadowsocks variants`() {
        val trojan = V2RayFmt.parseTrojan(
            "trojan://secret@trojan.example.com:443?type=grpc&serviceName=svc&security=tls" +
                "&sni=sni.example.com&alpn=h2&fp=chrome&allowInsecure=1#Trojan",
        )
        assertEquals("secret", trojan.password)
        assertEquals("trojan.example.com", trojan.serverAddress)
        assertEquals("grpc", trojan.type)
        assertEquals("svc", trojan.grpcServiceName)
        assertEquals("tls", trojan.security)
        assertTrue(trojan.allowInsecure)

        val userInfo = Base64.getUrlEncoder().withoutPadding().encodeToString("aes-256-gcm:pwd".toByteArray())
        val ss = V2RayFmt.parseShadowsocks("ss://$userInfo@ss.example.com:8388?plugin=v2ray-plugin#SS")
        assertEquals("aes-256-gcm", ss.method)
        assertEquals("pwd", ss.password)
        assertEquals("ss.example.com", ss.serverAddress)
        assertEquals(8388, ss.serverPort)
        assertEquals("v2ray-plugin", ss.plugin)

        val full = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("chacha20-ietf-poly1305:p@host:9000".toByteArray())
        val ssFull = V2RayFmt.parseShadowsocks("ss://$full")
        assertEquals("chacha20-ietf-poly1305", ssFull.method)
        assertEquals("p", ssFull.password)
        assertEquals("host", ssFull.serverAddress)
        assertEquals(9000, ssFull.serverPort)
    }

    @Test
    fun `parse rejects wrong schemes and malformed shadowsocks`() {
        assertFailsWith<IllegalArgumentException> { V2RayFmt.parseVLESS("vmess://x") }
        assertFailsWith<IllegalArgumentException> { V2RayFmt.parseVMess("vless://x") }
        assertFailsWith<IllegalArgumentException> { V2RayFmt.parseTrojan("vless://x") }
        assertFailsWith<IllegalArgumentException> { V2RayFmt.parseShadowsocks("vless://x") }
        val malformedSs = V2RayFmt.parseShadowsocks("ss://!!!!")
        assertEquals("127.0.0.1", malformedSs.serverAddress)
        assertEquals(1080, malformedSs.serverPort)
        assertFalse(V2RayFmt.parseVLESS("vless://id@h?skip-cert-verify=no").allowInsecure)
    }
}
