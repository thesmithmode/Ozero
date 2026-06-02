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
    fun `shadowsocks parses plain userinfo ipv6 fallback port and base64 method only`() {
        val ipv6 = V2RayFmt.parseShadowsocks("ss://aes-128-gcm:pwd@[2001:db8::1]:bad?plugin=obfs-local#IPv6")
        assertEquals("aes-128-gcm", ipv6.method)
        assertEquals("pwd", ipv6.password)
        assertEquals("2001:db8::1", ipv6.serverAddress)
        assertEquals(443, ipv6.serverPort)
        assertEquals("obfs-local", ipv6.plugin)
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
}
