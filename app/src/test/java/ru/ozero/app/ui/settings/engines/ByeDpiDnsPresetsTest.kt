package ru.ozero.app.ui.settings.engines

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ByeDpiDnsPresetsTest {

    @Test
    fun `BYEDPI_DNS_PRESETS contains Google Cloudflare Quad9 baseline`() {
        val labels = BYEDPI_DNS_PRESETS.map { it.first }
        assertTrue("Google" in labels, "Google preset обязан существовать: $labels")
        assertTrue("Cloudflare" in labels, "Cloudflare preset обязан существовать: $labels")
        assertTrue("Quad9" in labels, "Quad9 preset обязан существовать: $labels")
    }

    @Test
    fun `BYEDPI_DNS_PRESETS contains NextDNS 45_90_28_168 anti-censor primary`() {
        val nextDns = BYEDPI_DNS_PRESETS.firstOrNull { it.first == "NextDNS" }
        assertTrue(nextDns != null, "NextDNS preset обязан существовать: ${BYEDPI_DNS_PRESETS.map { it.first }}")
        assertEquals(listOf("45.90.28.168", "45.90.30.168"), nextDns.second)
    }

    @Test
    fun `BYEDPI_DNS_PRESETS contains AdGuard anti-tracker preset`() {
        val adGuard = BYEDPI_DNS_PRESETS.firstOrNull { it.first == "AdGuard" }
        assertTrue(adGuard != null, "AdGuard preset обязан существовать")
        assertEquals(listOf("94.140.14.14", "94.140.15.15"), adGuard.second)
    }

    @Test
    fun `BYEDPI_DNS_PRESETS contains ControlD free unrestricted preset`() {
        val controlD = BYEDPI_DNS_PRESETS.firstOrNull { it.first == "ControlD" }
        assertTrue(controlD != null, "ControlD preset обязан существовать")
        assertEquals(listOf("76.76.2.0", "76.76.10.0"), controlD.second)
    }

    @Test
    fun `all preset values are non-empty pairs of valid IPv4 strings`() {
        val ipv4Regex = Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")
        BYEDPI_DNS_PRESETS.forEach { (label, servers) ->
            assertTrue(servers.isNotEmpty(), "preset '$label' имеет пустой список серверов")
            servers.forEach { ip ->
                assertTrue(ipv4Regex.matches(ip), "preset '$label' содержит невалидный IPv4: '$ip'")
            }
        }
    }

    @Test
    fun `preset labels are unique`() {
        val labels = BYEDPI_DNS_PRESETS.map { it.first }
        assertEquals(labels.size, labels.toSet().size, "Дубли в preset labels: $labels")
    }
}
