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
    fun `shadowsocks initialize defaults restores blank method`() {
        val bean = ShadowsocksBean().apply {
            method = ""
            initializeDefaultValues()
        }

        assertEquals("aes-128-gcm", bean.method)
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

    @Test
    fun `uri fallback keeps invalid percent user info and blank authority absent`() {
        val withInvalidUserInfo = UriCompat.parse("vless://bad%zz+user@example.com:443?x=%zz#frag")
        val blankAuthority = UriCompat.parse("vless://?type=tcp")

        assertEquals("bad%zz+user", withInvalidUserInfo.userInfo)
        assertEquals("%zz", withInvalidUserInfo.getQueryParameter("x"))
        assertEquals("frag", withInvalidUserInfo.fragment)
        assertEquals(null, blankAuthority.host)
        assertEquals(-1, blankAuthority.port)
    }

    @Test
    fun `vless parser covers quic and tcp http branches`() {
        val quic = V2RayFmt.parseVLESS(
            "vless://id@vl.example.com:443?type=quic&headerType=srtp&quicSecurity=aes-128-gcm&key=secret",
        )
        assertEquals("quic", quic.type)
        assertEquals("srtp", quic.headerType)
        assertEquals("aes-128-gcm", quic.quicSecurity)
        assertEquals("secret", quic.quicKey)

        val tcpHttp = V2RayFmt.parseVLESS(
            "vless://id@vl.example.com:443?type=tcp&headerType=http&host=front.example.com&path=/tcp",
        )
        assertEquals("tcp", tcpHttp.type)
        assertEquals("http", tcpHttp.headerType)
        assertEquals("front.example.com", tcpHttp.host)
        assertEquals("/tcp", tcpHttp.path)
    }

    @Test
    fun `uri fallback covers ipv6 blank port query flags and missing fragments`() {
        val ipv6 = UriCompat.parse("vless://user@[2001:db8::1]:8443?flag&empty=&encoded=a%2Bb")
        val blankPort = UriCompat.parse("vless://user@example.com:?type=tcp")
        val noQuery = UriCompat.parse("vless://user@example.com")
        val noScheme = UriCompat.parse("not-a-uri?x=1#frag")
        val noPort = UriCompat.parse("vless://user@example.com/path?type=tcp")
        val invalidIpv6Port = UriCompat.parse("vless://user@[2001:db8::2]:bad?type=tcp")

        assertEquals("user", ipv6.userInfo)
        assertEquals("[2001:db8::1]", ipv6.host)
        assertEquals(8443, ipv6.port)
        assertEquals("", ipv6.getQueryParameter("flag"))
        assertEquals("", ipv6.getQueryParameter("empty"))
        assertEquals("a+b", ipv6.getQueryParameter("encoded"))
        assertEquals("example.com", blankPort.host)
        assertEquals(-1, blankPort.port)
        assertEquals(null, noQuery.fragment)
        assertEquals(null, noQuery.getQueryParameter("missing"))
        assertEquals("1", noScheme.getQueryParameter("x"))
        assertEquals("frag", noScheme.fragment)
        assertEquals("example.com", noPort.host)
        assertEquals(-1, noPort.port)
        assertEquals("2001:db8::2", invalidIpv6Port.host)
        assertEquals(-1, invalidIpv6Port.port)
    }

    @Test
    fun `security parser accepts every insecure alias and defaults reality fingerprint`() {
        listOf("allowInsecure", "allow_insecure", "insecure", "skip-cert-verify").forEach { key ->
            val bean = VLESSBean()
            V2RayFmtUtils.parseSecurityParams(bean, UriCompat.parse("vless://id@host:443?$key=yes"))
            assertEquals(true, bean.allowInsecure, key)
            assertEquals("chrome", bean.realityFingerprint)
        }

        val falseBean = VLESSBean()
        V2RayFmtUtils.parseSecurityParams(falseBean, UriCompat.parse("vless://id@host:443?allowInsecure=no"))
        assertEquals(false, falseBean.allowInsecure)

        val oneBean = VLESSBean()
        V2RayFmtUtils.parseSecurityParams(oneBean, UriCompat.parse("vless://id@host:443?insecure=1"))
        assertEquals(true, oneBean.allowInsecure)

        val trueBean = VLESSBean()
        V2RayFmtUtils.parseSecurityParams(trueBean, UriCompat.parse("vless://id@host:443?skip-cert-verify=true"))
        assertEquals(true, trueBean.allowInsecure)
    }

    @Test
    fun `transport parser covers direct http ws grpc tcp and unknown branches`() {
        val ws = VLESSBean()
        V2RayFmtUtils.parseTransportParams(ws, UriCompat.parse("vless://id@host:443?type=ws&host=front&path=/ws"))
        assertEquals("ws", ws.type)
        assertEquals("front", ws.host)
        assertEquals("/ws", ws.path)

        val grpc = VLESSBean()
        V2RayFmtUtils.parseTransportParams(grpc, UriCompat.parse("vless://id@host:443?type=grpc&serviceName=svc"))
        assertEquals("grpc", grpc.type)
        assertEquals("svc", grpc.grpcServiceName)

        val tcp = VLESSBean()
        V2RayFmtUtils.parseTransportParams(tcp, UriCompat.parse("vless://id@host:443?headerType=none"))
        assertEquals("tcp", tcp.type)
        assertEquals("none", tcp.headerType)

        val unknown = VLESSBean()
        V2RayFmtUtils.parseTransportParams(unknown, UriCompat.parse("vless://id@host:443?type=quic"))
        assertEquals("quic", unknown.type)
    }

    @Test
    fun `base64 decoder covers mime fallback padding and invalid input`() {
        val raw = "hello\nworld"
        val mime = Base64.getMimeEncoder(4, "\n".toByteArray()).encodeToString(raw.toByteArray())

        assertEquals(raw, V2RayFmtUtils.tryBase64Decode(mime))
        assertEquals("test", V2RayFmtUtils.tryBase64Decode("dGVzdA"))
        assertEquals(null, V2RayFmtUtils.tryBase64Decode("   "))
    }

    @Test
    fun `shadowsocks parser covers userinfo base64 and no server separator branches`() {
        val userInfo = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("aes-256-gcm:pwd".toByteArray())
        val userInfoEncoded = V2RayFmt.parseShadowsocks("ss://$userInfo@ss.example.com:8388?plugin=v2ray#Named")
        val methodOnly = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("chacha20-ietf-poly1305:secret".toByteArray())
        val methodOnlyBean = V2RayFmt.parseShadowsocks("ss://$methodOnly")

        assertEquals("aes-256-gcm", userInfoEncoded.method)
        assertEquals("pwd", userInfoEncoded.password)
        assertEquals("ss.example.com", userInfoEncoded.serverAddress)
        assertEquals(8388, userInfoEncoded.serverPort)
        assertEquals("v2ray", userInfoEncoded.plugin)
        assertEquals("Named", userInfoEncoded.name)
        assertEquals("chacha20-ietf-poly1305", methodOnlyBean.method)
        assertEquals("secret", methodOnlyBean.password)
        assertEquals("127.0.0.1", methodOnlyBean.serverAddress)
        assertEquals(1080, methodOnlyBean.serverPort)
    }

    @Test
    fun `uri fallback covers blank user info and hostless authority`() {
        val blankUserInfo = UriCompat.parse("vless://@fallback.example.com:443?type=tcp")
        val hostless = UriCompat.parse("vless://user@:443?type=tcp")
        val authorityOnly = UriCompat.parse("vless://fallback.example.com")
        val overflowPort = UriCompat.parse("vless://user@fallback.example.com:999999999999999999999?type=tcp")

        assertEquals("", blankUserInfo.userInfo)
        assertEquals("fallback.example.com", blankUserInfo.host)
        assertEquals(443, blankUserInfo.port)
        assertEquals(null, hostless.host)
        assertEquals(443, hostless.port)
        assertEquals("fallback.example.com", authorityOnly.host)
        assertEquals(-1, authorityOnly.port)
        assertEquals("fallback.example.com", overflowPort.host)
        assertEquals(-1, overflowPort.port)
    }

    @Test
    fun `standard vmess and trojan cover missing fragment port and optional defaults`() {
        val vmess = V2RayFmt.parseVMess("vmess://id@vmess.example.com?type=http")
        val trojan = V2RayFmt.parseTrojan("trojan://secret@trojan.example.com?type=httpupgrade")

        assertEquals("id", vmess.uuid)
        assertEquals("vmess.example.com", vmess.serverAddress)
        assertEquals(443, vmess.serverPort)
        assertEquals("", vmess.name)
        assertEquals("auto", vmess.encryption)
        assertEquals("http", vmess.type)
        assertEquals("/", vmess.path)
        assertEquals("secret", trojan.password)
        assertEquals("trojan.example.com", trojan.serverAddress)
        assertEquals(443, trojan.serverPort)
        assertEquals("", trojan.name)
        assertEquals("tls", trojan.security)
        assertEquals("httpupgrade", trojan.type)
        assertEquals("/", trojan.path)
    }

    @Test
    fun `trojan defaults and shadowsocks error branches stay covered`() {
        val trojanBlank = TrojanBean().apply {
            security = ""
            initializeDefaultValues()
        }
        val trojanExplicit = TrojanBean().apply {
            security = "reality"
            initializeDefaultValues()
        }
        val ssWithIgnoredQuery = V2RayFmt.parseShadowsocks("ss://method:pwd@ss.example.com:8388?unused=1")

        assertEquals("tls", trojanBlank.security)
        assertEquals("reality", trojanExplicit.security)
        assertEquals("", ssWithIgnoredQuery.plugin)
        assertFailsWith<IllegalStateException> {
            V2RayFmt.parseShadowsocks("ss://abcde")
        }
    }

    @Test
    fun `security truthy parser covers uppercase and missing values`() {
        val missing = VLESSBean()
        V2RayFmtUtils.parseSecurityParams(missing, UriCompat.parse("vless://id@host:443"))
        assertEquals(false, missing.allowInsecure)

        val upper = VLESSBean()
        V2RayFmtUtils.parseSecurityParams(upper, UriCompat.parse("vless://id@host:443?allowInsecure=TRUE"))
        assertEquals(true, upper.allowInsecure)
    }

    @Test
    fun `bean display names and default initializers cover empty and named branches`() {
        val vless = VLESSBean().apply {
            serverAddress = "server.example.com"
            serverPort = 443
            encryption = ""
            initializeDefaultValues()
        }
        val vmess = VMessBean().apply { initializeDefaultValues() }
        val trojan = TrojanBean().apply { initializeDefaultValues() }

        assertEquals("none", vless.encryption)
        assertEquals("server.example.com:443", vless.displayName())
        vless.name = "Named"
        assertEquals("Named", vless.displayName())
        assertEquals("auto", vmess.encryption)
        assertEquals("", trojan.password)
    }
}
