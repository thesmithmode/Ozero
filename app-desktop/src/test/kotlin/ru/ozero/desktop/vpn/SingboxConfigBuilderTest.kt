package ru.ozero.desktop.vpn

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SingboxConfigBuilderTest {

    @Test
    fun `buildTun2Socks generates valid JSON`() {
        val config = SingboxConfigBuilder()
            .socksUpstream(1080)
            .buildTun2Socks()

        val json = JSONObject(config)
        assertNotNull(json.getJSONObject("dns"))
        assertNotNull(json.getJSONArray("inbounds"))
        assertNotNull(json.getJSONArray("outbounds"))
        assertNotNull(json.getJSONObject("route"))
    }

    @Test
    fun `buildTun2Socks has correct TUN inbound`() {
        val config = SingboxConfigBuilder()
            .socksUpstream(1080)
            .buildTun2Socks()

        val json = JSONObject(config)
        val inbound = json.getJSONArray("inbounds").getJSONObject(0)
        assertEquals("tun", inbound.getString("type"))
        assertEquals("ozero-tun", inbound.getString("interface_name"))
        assertTrue(inbound.getBoolean("auto_route"))
        assertTrue(inbound.getBoolean("strict_route"))
    }

    @Test
    fun `buildTun2Socks has SOCKS outbound on correct port`() {
        val config = SingboxConfigBuilder()
            .socksUpstream(9050)
            .buildTun2Socks()

        val json = JSONObject(config)
        val outbounds = json.getJSONArray("outbounds")
        val socksOutbound = outbounds.getJSONObject(0)
        assertEquals("socks", socksOutbound.getString("type"))
        assertEquals(9050, socksOutbound.getInt("server_port"))
    }

    @Test
    fun `buildTun2Socks includes direct bypass processes`() {
        val config = SingboxConfigBuilder()
            .socksUpstream(1080)
            .bypassProcesses(listOf("chrome.exe", "firefox.exe"))
            .buildTun2Socks()

        val json = JSONObject(config)
        val rules = json.getJSONObject("route").getJSONArray("rules")
        var found = false
        for (i in 0 until rules.length()) {
            val rule = rules.getJSONObject(i)
            if (rule.has("process_name") && rule.optString("outbound") == "direct") {
                val names = rule.getJSONArray("process_name")
                assertEquals("chrome.exe", names.getString(0))
                assertEquals("firefox.exe", names.getString(1))
                found = true
            }
        }
        assertTrue(found, "Expected process_name direct rule")
    }

    @Test
    fun `buildTun2Socks includes proxy-only processes`() {
        val config = SingboxConfigBuilder()
            .socksUpstream(1080)
            .proxyOnlyProcesses(listOf("telegram.exe"))
            .buildTun2Socks()

        val json = JSONObject(config)
        val rules = json.getJSONObject("route").getJSONArray("rules")
        var proxyFound = false
        var defaultDirectFound = false
        for (i in 0 until rules.length()) {
            val rule = rules.getJSONObject(i)
            if (rule.has("process_name") && rule.optString("outbound") == "proxy") {
                assertEquals("telegram.exe", rule.getJSONArray("process_name").getString(0))
                proxyFound = true
            }
            if (!rule.has("process_name") && !rule.has("ip_is_private") &&
                !rule.has("protocol") && !rule.has("action") &&
                rule.optString("outbound") == "direct"
            ) {
                defaultDirectFound = true
            }
        }
        assertTrue(proxyFound, "Expected proxy-only process_name rule")
        assertTrue(defaultDirectFound, "Expected default direct outbound rule")
    }

    @Test
    fun `buildTun2Socks throws on invalid port`() {
        assertThrows<IllegalArgumentException> {
            SingboxConfigBuilder().socksUpstream(0).buildTun2Socks()
        }
    }

    @Test
    fun `custom TUN address is applied`() {
        val config = SingboxConfigBuilder()
            .socksUpstream(1080)
            .tunAddress("10.0.0.1/24")
            .buildTun2Socks()

        val json = JSONObject(config)
        val addr = json.getJSONArray("inbounds").getJSONObject(0).getJSONArray("address").getString(0)
        assertEquals("10.0.0.1/24", addr)
    }
}
