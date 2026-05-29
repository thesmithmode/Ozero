package ru.ozero.singboxconfig

import org.junit.jupiter.api.Test
import ru.ozero.singboxfmt.TrojanBean
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxfmt.VMessBean
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigBuilderAutoSelectTest {

    private fun makeVless(host: String = "proxy.example.com", port: Int = 443) = VLESSBean().apply {
        uuid = "12345678-1234-1234-1234-123456789abc"
        serverAddress = host
        serverPort = port
        type = "tcp"
        security = "none"
    }

    private fun makeVmess(host: String = "vmess.example.com", port: Int = 80) = VMessBean().apply {
        uuid = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
        serverAddress = host
        serverPort = port
        type = "tcp"
        security = "none"
        encryption = "auto"
    }

    private fun makeTrojan(host: String = "trojan.example.com", port: Int = 443) = TrojanBean().apply {
        password = "secret"
        serverAddress = host
        serverPort = port
        type = "tcp"
        security = "none"
    }

    @Test
    fun `should build valid config with single bean`() {
        val json = ConfigBuilder.buildSingboxAutoConfig(listOf(makeVless()))

        assertTrue(json.startsWith("{"))
        assertTrue(json.endsWith("}"))
        assertContains(json, "\"type\":\"urltest\"")
        assertContains(json, "\"tag\":\"proxy\"")
        assertContains(json, "\"proxy-0\"")
        assertContains(json, "\"type\":\"vless\"")
        assertContains(json, "\"tag\":\"proxy-0\"")
    }

    @Test
    fun `should build urltest outbound with correct fields`() {
        val json = ConfigBuilder.buildSingboxAutoConfig(listOf(makeVless()))

        assertContains(json, "\"type\":\"urltest\"")
        assertContains(json, "https://www.gstatic.com/generate_204")
        assertContains(json, "\"interval\":\"3m\"")
        assertContains(json, "\"tolerance\":50")
        assertContains(json, "\"interrupt_exist_connections\":true")
        assertContains(json, "\"idle_timeout\":\"30m\"")
    }

    @Test
    fun `should build three outbounds with correct tags for three beans`() {
        val beans = listOf(makeVless(), makeVmess(), makeTrojan())
        val json = ConfigBuilder.buildSingboxAutoConfig(beans)

        assertContains(json, "\"proxy-0\"")
        assertContains(json, "\"proxy-1\"")
        assertContains(json, "\"proxy-2\"")
        assertContains(json, "\"type\":\"vless\"")
        assertContains(json, "\"type\":\"vmess\"")
        assertContains(json, "\"type\":\"trojan\"")
        assertFalse(json.contains("\"tag\":\"proxy\",\"server\""), "No individual outbound should have tag 'proxy'")
    }

    @Test
    fun `should list all proxy tags in urltest outbounds array`() {
        val beans = listOf(makeVless(), makeVmess(), makeTrojan())
        val json = ConfigBuilder.buildSingboxAutoConfig(beans)

        val urltestStart = json.indexOf("\"type\":\"urltest\"")
        val urltestSection = json.substring(urltestStart, urltestStart + 300)
        assertContains(urltestSection, "\"proxy-0\"")
        assertContains(urltestSection, "\"proxy-1\"")
        assertContains(urltestSection, "\"proxy-2\"")
    }

    @Test
    fun `should fail for empty beans list`() {
        val result = runCatching { ConfigBuilder.buildSingboxAutoConfig(emptyList()) }
        assertTrue(result.isFailure, "buildSingboxAutoConfig must throw for empty list")
    }

    @Test
    fun `should route traffic via proxy tag`() {
        val json = ConfigBuilder.buildSingboxAutoConfig(listOf(makeVless()))

        assertContains(json, "\"final\":\"proxy\"")
    }

    @Test
    fun `should include required infrastructure outbounds`() {
        val json = ConfigBuilder.buildSingboxAutoConfig(listOf(makeVless()))

        assertContains(json, "\"type\":\"direct\"")
        assertContains(json, "\"type\":\"block\"")
        assertFalse(json.contains("\"type\":\"dns\""), "dns outbound removed in sing-box 1.13.0")
        assertContains(json, "\"auto_detect_interface\":true")
    }

    @Test
    fun `existing buildSingboxConfig still produces single proxy tag`() {
        val json = ConfigBuilder.buildSingboxConfig(makeVless())

        assertContains(json, "\"tag\":\"proxy\"")
        assertFalse(json.contains("\"tag\":\"proxy-0\""), "Single-profile config must not use indexed tags")
        assertFalse(json.contains("\"type\":\"urltest\""), "Single-profile config must not have urltest")
    }

    @Test
    fun `tun config can expose local socks inbound for exit ip probe`() {
        val json = ConfigBuilder.buildSingboxConfig(makeVless(), probeSocksPort = 49421)

        assertContains(json, "\"type\":\"tun\"")
        assertContains(json, "\"type\":\"socks\"")
        assertContains(json, "\"listen\":\"127.0.0.1\"")
        assertContains(json, "\"listen_port\":49421")
        assertContains(json, "\"final\":\"proxy\"")
    }

    @Test
    fun `auto config with ten beans stays under 50KB`() {
        val beans = (1..10).map { makeVless("server$it.example.com", 443 + it) }
        val json = ConfigBuilder.buildSingboxAutoConfig(beans)

        val sizeBytes = json.toByteArray(Charsets.UTF_8).size
        assertTrue(sizeBytes < 51_200, "10-server auto config must be < 50KB, got ${sizeBytes}B")
    }
}
