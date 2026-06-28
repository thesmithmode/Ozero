package ru.ozero.singboxconfig

import org.junit.jupiter.api.Test
import ru.ozero.singboxfmt.VLESSBean
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ConfigBuilderProfileChainTest {

    @Test
    fun `one wrapper makes selected detour through wrapper`() {
        val json = ConfigBuilder.buildProfileChainConfig(
            selected = vless("eu.example.com"),
            wrappers = listOf(vless("ru.example.com")),
        )

        assertContains(json, "\"tag\":\"chain-0\"")
        assertContains(json, "\"server\":\"ru.example.com\"")
        assertContains(json, "\"tag\":\"proxy\"")
        assertContains(json, "\"server\":\"eu.example.com\"")
        assertContains(json, "\"detour\":\"chain-0\"")
    }

    @Test
    fun `two wrappers chain in declared order`() {
        val json = ConfigBuilder.buildProfileChainConfig(
            selected = vless("eu.example.com"),
            wrappers = listOf(vless("ru.example.com"), vless("de.example.com")),
        )

        assertContains(json, "\"tag\":\"chain-1\"")
        assertContains(json, "\"server\":\"de.example.com\"")
        assertContains(json, "\"detour\":\"chain-0\"")
        assertContains(json, "\"tag\":\"proxy\"")
        assertContains(json, "\"detour\":\"chain-1\"")
    }

    @Test
    fun `proxy chain config keeps SOCKS inbound`() {
        val json = ConfigBuilder.buildProfileChainProxyConfig(
            selected = vless("eu.example.com"),
            wrappers = listOf(vless("ru.example.com")),
            socksPort = 49444,
        )

        assertContains(json, "\"type\":\"socks\"")
        assertContains(json, "\"listen_port\":49444")
        assertFalse(json.contains("\"type\":\"tun\""))
        assertContains(json, "\"detour\":\"chain-0\"")
    }

    @Test
    fun `tun profile chain does not expose socks inbound`() {
        val json = ConfigBuilder.buildProfileChainConfig(
            selected = vless("eu.example.com"),
            wrappers = listOf(vless("ru.example.com"), vless("de.example.com")),
        )

        assertContains(json, "\"type\":\"tun\"")
        assertFalse(json.contains("\"type\":\"socks\""))
        assertFalse(json.contains("\"listen_port\""))
        assertContains(json, "\"tag\":\"proxy\"")
        assertContains(json, "\"detour\":\"chain-1\"")
        assertContains(json, "\"final\":\"proxy\"")
    }

    private fun vless(host: String): VLESSBean =
        VLESSBean().apply {
            uuid = "12345678-1234-1234-1234-123456789abc"
            serverAddress = host
            serverPort = 443
            type = "tcp"
            security = "none"
        }
}
