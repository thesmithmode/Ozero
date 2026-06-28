package ru.ozero.singboxfmt

import org.junit.jupiter.api.Test
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Suppress("LongMethod")
class FmtBranchCoverageTest {

    @Test
    fun `uri compat parses key without value fallback query and bad escapes`() {
        val parsed = UriCompat.parse("vless://id@[broken?flag&bad=%E0%A4%A#Name")

        assertEquals("", parsed.getQueryParameter("flag"))
        assertEquals("%E0%A4%A", parsed.getQueryParameter("bad"))
        assertNull(parsed.getQueryParameter("missing"))
    }

    @Test
    fun `vless defaults missing port name encryption and rejects missing parts`() {
        val bean = V2RayFmt.parseVLESS("vless://id@example.com?skip-cert-verify=yes#Name%20With%20Space")

        assertEquals(443, bean.serverPort)
        assertEquals("Name With Space", bean.name)
        assertEquals("none", bean.encryption)
        assertTrue(bean.allowInsecure)
        assertFailsWith<IllegalStateException> { V2RayFmt.parseVLESS("vless://id@:443") }
        assertFailsWith<IllegalStateException> { V2RayFmt.parseVLESS("vless://example.com:443") }
    }

    @Test
    fun `vmess json defaults invalid numbers and non tls security`() {
        val json = """
            {"add":"","port":"bad","id":"id","aid":"bad","scy":"","net":"xhttp","tls":"","ps":"Json"}
        """.trimIndent()
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())
        val bean = V2RayFmt.parseVMess("vmess://$encoded")

        assertEquals("", bean.serverAddress)
        assertEquals(443, bean.serverPort)
        assertEquals(0, bean.alterId)
        assertEquals("auto", bean.encryption)
        assertEquals("splithttp", bean.type)
        assertEquals("none", bean.security)
        assertEquals("Json", bean.name)
    }

    @Test
    fun `vmess standard defaults and rejects missing host or uuid`() {
        val bean = V2RayFmt.parseVMess("vmess://id@example.com?type=tcp&headerType=none")

        assertEquals(443, bean.serverPort)
        assertEquals("auto", bean.encryption)
        assertEquals("none", bean.headerType)
        assertFalse(bean.allowInsecure)
        assertFailsWith<IllegalStateException> { V2RayFmt.parseVMess("vmess://id@:443") }
        assertFailsWith<IllegalStateException> { V2RayFmt.parseVMess("vmess://example.com:443") }
    }

    @Test
    fun `trojan defaults port security and rejects missing parts`() {
        val bean = V2RayFmt.parseTrojan("trojan://secret@example.com?allowInsecure=0#Trojan%20Name")

        assertEquals(443, bean.serverPort)
        assertEquals("tls", bean.security)
        assertEquals("Trojan Name", bean.name)
        assertFalse(bean.allowInsecure)
        assertFailsWith<IllegalStateException> { V2RayFmt.parseTrojan("trojan://secret@:443") }
        assertFailsWith<IllegalStateException> { V2RayFmt.parseTrojan("trojan://example.com:443") }
    }

    @Test
    fun `trojan ignores subscription tls downgrade flags`() {
        listOf("security=none", "allowInsecure=1", "allow-insecure=yes", "skip-cert-verify=true").forEach { query ->
            val bean = V2RayFmt.parseTrojan("trojan://secret@example.com?$query")

            assertEquals("tls", bean.security, query)
            assertFalse(bean.allowInsecure, query)
        }
    }

    @Test
    fun `shadowsocks parses plain userinfo ipv6 fallback port and base64 method only`() {
        val ipv6 = V2RayFmt.parseShadowsocks(
            "ss://aes-128-gcm:pwd@[2001:db8::1]:bad" +
                "?plugin=obfs-local&plugin-opts=obfs=http%3Bobfs-host=front.example.com#IPv6",
        )
        assertEquals("aes-128-gcm", ipv6.method)
        assertEquals("pwd", ipv6.password)
        assertEquals("2001:db8::1", ipv6.serverAddress)
        assertEquals(443, ipv6.serverPort)
        assertEquals("obfs-local", ipv6.plugin)
        assertEquals("obfs=http;obfs-host=front.example.com", ipv6.pluginOpts)
        assertEquals("IPv6", ipv6.name)

        val methodOnly = Base64.getUrlEncoder().withoutPadding().encodeToString("aes-256-gcm:pwd".toByteArray())
        val bean = V2RayFmt.parseShadowsocks("ss://$methodOnly")
        assertEquals("aes-256-gcm", bean.method)
        assertEquals("pwd", bean.password)
        assertEquals("127.0.0.1", bean.serverAddress)
        assertEquals(1080, bean.serverPort)
    }

    @Test
    fun `base64 utility handles empty invalid url safe and mime variants`() {
        assertNull(V2RayFmtUtils.tryBase64Decode(""))
        assertNull(V2RayFmtUtils.tryBase64Decode("%%%"))
        val urlSafe = Base64.getUrlEncoder().withoutPadding().encodeToString("hello".toByteArray())
        val mime = Base64.getMimeEncoder().encodeToString("world".toByteArray())
        assertEquals("hello", V2RayFmtUtils.tryBase64Decode(urlSafe))
        assertEquals("world", V2RayFmtUtils.tryBase64Decode(mime))
    }

    @Test
    fun `v2ray utils cover truthy aliases transport mapping and tcp defaults`() {
        val bean = VLESSBean()
        val parsed = UriCompat.parse(
            "vless://id@host:443?security=tls&sni=sni&alpn=h2&fp=chrome&pbk=pk&sid=sid" +
                "&allowInsecure=yes&type=ws&host=front&path=/x",
        )

        V2RayFmtUtils.parseSecurityParams(bean, parsed)
        V2RayFmtUtils.parseTransportParams(bean, parsed)

        assertEquals("reality", bean.security)
        assertEquals("sni", bean.sni)
        assertEquals("h2", bean.alpn)
        assertEquals("chrome", bean.utlsFingerprint)
        assertEquals("pk", bean.realityPublicKey)
        assertEquals("sid", bean.realityShortId)
        assertTrue(bean.allowInsecure)
        assertEquals("ws", bean.type)
        assertEquals("front", bean.host)
        assertEquals("/x", bean.path)

        val tcp = VLESSBean()
        val tcpParsed = UriCompat.parse("vless://id@host:443?type=tcp&headerType=http&host=front&path=/tcp")
        V2RayFmtUtils.parseTransportParams(tcp, tcpParsed)
        assertEquals("tcp", tcp.type)
        assertEquals("http", tcp.headerType)
        assertEquals("front", tcp.host)
        assertEquals("/tcp", tcp.path)
        val split = VLESSBean()
        V2RayFmtUtils.parseTransportParams(split, UriCompat.parse("vless://id@host:443?type=xhttp&mode=stream-up"))
        assertEquals("splithttp", V2RayFmtUtils.mapTransportType("xhttp"))
        assertEquals("splithttp", split.type)
        assertEquals("other", V2RayFmtUtils.mapTransportType("other"))
        assertEquals("http", V2RayFmtUtils.mapTransportType("h2"))
    }

    @Test
    fun `vless covers httpupgrade tcp http kcp and quic default branches`() {
        val httpUpgrade = V2RayFmt.parseVLESS("vless://id@h?type=httpupgrade")
        assertEquals("httpupgrade", httpUpgrade.type)
        assertEquals("", httpUpgrade.host)
        assertEquals("/", httpUpgrade.path)
        assertEquals(0, httpUpgrade.maxEarlyData)

        val tcpHttp = V2RayFmt.parseVLESS("vless://id@h?type=tcp&headerType=http")
        assertEquals("http", tcpHttp.headerType)
        assertEquals("", tcpHttp.host)
        assertEquals("/", tcpHttp.path)

        val kcp = V2RayFmt.parseVLESS("vless://id@h?type=mkcp")
        assertEquals("kcp", kcp.type)
        assertEquals("none", kcp.headerType)
        assertEquals("", kcp.mKcpSeed)

        val quic = V2RayFmt.parseVLESS("vless://id@h?type=quic")
        assertEquals("none", quic.headerType)
        assertEquals("none", quic.quicSecurity)
        assertEquals("", quic.quicKey)
    }

    @Test
    fun `standard v2ray parser covers transport and insecure aliases`() {
        val vmessHttp = V2RayFmt.parseVMess("vmess://id@vm.example.com?type=h2&host=front&path=/h&insecure=yes")
        assertEquals("http", vmessHttp.type)
        assertEquals("front", vmessHttp.host)
        assertEquals("/h", vmessHttp.path)
        assertTrue(vmessHttp.allowInsecure)

        val vmessGrpc = V2RayFmt.parseVMess("vmess://id@vm.example.com?type=grpc&serviceName=svc&allowInsecure=1")
        assertEquals("grpc", vmessGrpc.type)
        assertEquals("svc", vmessGrpc.grpcServiceName)
        assertTrue(vmessGrpc.allowInsecure)

        val trojanWs = V2RayFmt.parseTrojan("trojan://pwd@tr.example.com?type=httpupgrade&host=front&path=/u")
        assertEquals("httpupgrade", trojanWs.type)
        assertEquals("front", trojanWs.host)
        assertEquals("/u", trojanWs.path)
    }
}
