package ru.ozero.singboxconfig

import org.junit.jupiter.api.Test
import ru.ozero.singboxfmt.VLESSBean
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ConfigBuilderDnsTest {
    private fun bean() = VLESSBean().apply {
        uuid = "12345678-1234-1234-1234-123456789abc"
        serverAddress = "proxy.example.com"
        serverPort = 443
        type = "tcp"
        security = "none"
    }

    @Test
    fun `custom DNS servers are emitted into tun JSON`() {
        val json = ConfigBuilder.buildSingboxConfig(
            bean(),
            dnsServers = listOf("9.9.9.9", "https://dns.example/dns-query"),
        )

        assertContains(json, "\"server\":\"9.9.9.9\"")
        assertContains(json, "\"server\":\"dns.example\"")
        assertContains(json, "\"path\":\"/dns-query\"")
        assertContains(json, "\"type\":\"https\"")
        assertContains(json, "\"domain_resolver\":\"dns-local\"")
        assertContains(json, "\"tag\":\"dns-local\"")
        assertContains(json, "\"detour\":\"proxy\"")
        assertFalse(json.contains("https://dns.example/dns-query"))
    }

    @Test
    fun `tls DNS servers are emitted as host without URI scheme`() {
        val json = ConfigBuilder.buildSingboxConfig(bean(), dnsServers = listOf("tls://dns.example"))

        assertContains(json, "\"type\":\"tls\"")
        assertContains(json, "\"server\":\"dns.example\"")
        assertContains(json, "\"domain_resolver\":\"dns-local\"")
        assertFalse(json.contains("tls://dns.example"))
    }

    @Test
    fun `IP literal secure DNS does not add domain resolver`() {
        val json = ConfigBuilder.buildSingboxConfig(
            bean(),
            dnsServers = listOf("https://1.1.1.1/dns-query", "tls://8.8.8.8"),
        )

        assertContains(json, "\"server\":\"1.1.1.1\"")
        assertContains(json, "\"server\":\"8.8.8.8\"")
        assertContains(json, "\"default_domain_resolver\":\"dns-local\"")
        assertFalse(json.contains("\"domain_resolver\":\"dns-local\""))
    }

    @Test
    fun `secure DNS ports are emitted separately from server`() {
        val json = ConfigBuilder.buildSingboxConfig(
            bean(),
            dnsServers = listOf("tls://1.1.1.1:853", "https://dns.example:8443/dns-query"),
        )

        assertContains(json, "\"server\":\"1.1.1.1\",\"server_port\":853")
        assertContains(json, "\"server\":\"dns.example\",\"server_port\":8443")
        assertFalse(json.contains("1.1.1.1:853"))
        assertFalse(json.contains("dns.example:8443"))
    }

    @Test
    fun `chain hostname secure DNS uses Android local bootstrap`() {
        val json = ConfigBuilder.buildChainConfig(bean(), socksPort = 2080, dnsServers = listOf("tls://dns.example"))

        assertContains(json, "\"domain_resolver\":\"dns-local\"")
        assertContains(json, "\"tag\":\"dns-local\"")
        assertFalse(json.contains("dns-domain-resolver"))
    }

    @Test
    fun `route uses Android local DNS as domain resolver in every config shape`() {
        val tun = ConfigBuilder.buildSingboxConfig(bean(), dnsServers = listOf("8.8.8.8"))
        val chain = ConfigBuilder.buildChainConfig(bean(), socksPort = 2080, dnsServers = listOf("8.8.8.8"))
        val auto = ConfigBuilder.buildSingboxAutoConfig(listOf(bean()), dnsServers = listOf("8.8.8.8"))
        val probe = ConfigBuilder.buildProbeConfig(
            listOf(ConfigBuilder.ProbeTarget(bean(), 2081)),
            dnsServers = listOf("8.8.8.8"),
        )

        listOf(tun, chain, auto, probe).forEach { json ->
            assertContains(json, "\"default_domain_resolver\":\"dns-local\"")
            assertContains(json, "\"tag\":\"dns-local\"")
            assertContains(json, "\"auto_detect_interface\":true")
        }
    }

    @Test
    fun `empty DNS servers fall back to Android local DNS`() {
        val json = ConfigBuilder.buildSingboxConfig(bean(), dnsServers = emptyList())

        assertContains(json, "\"final\":\"dns-local\"")
        assertFalse(json.contains("\"tag\":\"dns-0\""))
    }

    @Test
    fun `invalid DNS servers fall back to Android local DNS`() {
        val json = ConfigBuilder.buildSingboxConfig(bean(), dnsServers = listOf("not a dns server"))

        assertContains(json, "\"final\":\"dns-local\"")
        assertFalse(json.contains("not a dns server"))
    }

    @Test
    fun `chain DNS routes plain DNS directly for bootstrap`() {
        val json = ConfigBuilder.buildChainConfig(bean(), socksPort = 2080, dnsServers = listOf("8.8.8.8"))

        assertContains(json, "\"type\":\"udp\"")
        assertContains(json, "\"server\":\"8.8.8.8\"")
        assertContains(json, "\"detour\":\"proxy\"")
        assertFalse(json.contains("\"address\""))
        assertFalse(json.contains("legacy DoH fallback"))
    }

    @Test
    fun `IPv6 DNS is filtered when IPv6 is disabled and preserved when enabled`() {
        val disabled = ConfigBuilder.buildChainConfig(
            bean(),
            socksPort = 2080,
            dnsServers = listOf("8.8.8.8", "2001:4860:4860::8888"),
            ipv6Enabled = false,
        )
        val enabled = ConfigBuilder.buildChainConfig(
            bean(),
            socksPort = 2080,
            dnsServers = listOf("8.8.8.8", "2001:4860:4860::8888"),
            ipv6Enabled = true,
        )

        assertContains(disabled, "\"server\":\"8.8.8.8\"")
        assertFalse(disabled.contains("2001:4860:4860::8888"))
        assertContains(enabled, "\"server\":\"2001:4860:4860::8888\"")
    }
}
