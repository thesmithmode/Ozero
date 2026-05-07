package ru.ozero.enginewarp

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WarpIniBuilderTest {

    private val baseConfig = WarpConfig(
        privateKey = "dGVzdC1wcml2YXRlLWtleS1iYXNlNjQ=",
        peerPublicKey = "dGVzdC1wZWVyLXB1YmxpYy1rZXk=",
        peerEndpoint = "engage.cloudflareclient.com:4500",
        interfaceAddressV4 = "172.16.0.2/32",
        interfaceAddressV6 = "2606:4700::1/128",
    )

    @Test
    fun `default AwgParams не пишет AWG-строк — WARP всегда vanilla WireGuard`() {
        val ini = WarpIniBuilder.build(baseConfig)
        assertFalse(ini.contains("Jc ="), "WARP INI не должен содержать Jc — am-go активирует AWG при наличии этого поля")
        assertFalse(ini.contains("Jmin ="))
        assertFalse(ini.contains("Jmax ="))
        assertFalse(ini.contains("H1 ="))
        assertFalse(ini.contains("H2 ="))
        assertFalse(ini.contains("H3 ="))
        assertFalse(ini.contains("H4 ="))
    }

    @Test
    fun `кастомные AWG параметры не попадают в INI — builder всегда vanilla`() {
        val config = baseConfig.copy(
            awgParams = AwgParams(
                junkPacketCount = 10,
                junkPacketMinSize = 50,
                junkPacketMaxSize = 150,
                initPacketJunkSize = 15,
                responsePacketJunkSize = 20,
                initPacketMagicHeader = 100L,
                responsePacketMagicHeader = 200L,
                cookieReplyMagicHeader = 300L,
                transportMagicHeader = 400L,
            ),
        )
        val ini = WarpIniBuilder.build(config)
        assertFalse(ini.contains("Jc ="))
        assertFalse(ini.contains("Jmin ="))
        assertFalse(ini.contains("Jmax ="))
        assertFalse(ini.contains("S1 ="))
        assertFalse(ini.contains("S2 ="))
        assertFalse(ini.contains("H1 ="))
        assertFalse(ini.contains("H2 ="))
        assertFalse(ini.contains("H3 ="))
        assertFalse(ini.contains("H4 ="))
    }

    @Test
    fun `AWG ключи отсутствуют в обеих секциях Interface и Peer`() {
        val ini = WarpIniBuilder.build(baseConfig)
        assertFalse(ini.contains("Jc ="))
        assertFalse(ini.contains("H1 ="))
    }

    @Test
    fun `Peer section contains required WireGuard fields`() {
        val ini = WarpIniBuilder.build(baseConfig)
        val peerPart = ini.substringAfter("[Peer]")
        assertTrue(peerPart.contains("PublicKey = ${baseConfig.peerPublicKey}"))
        assertTrue(peerPart.contains("AllowedIPs = 0.0.0.0/0, ::/0"))
        assertTrue(peerPart.contains("Endpoint = ${baseConfig.peerEndpoint}"))
        assertTrue(peerPart.contains("PersistentKeepalive = 25"))
    }

    @Test
    fun `DNS servers formatted with comma-space separator`() {
        val ini = WarpIniBuilder.build(baseConfig)
        assertTrue(ini.contains("DNS = 1.1.1.1, 2606:4700:4700::1111"))
    }

    @Test
    fun `single DNS server renders without trailing comma`() {
        val config = baseConfig.copy(dnsServers = listOf("8.8.8.8"))
        val ini = WarpIniBuilder.build(config)
        assertTrue(ini.contains("DNS = 8.8.8.8"))
        assertFalse(ini.contains("DNS = 8.8.8.8,"))
    }

    @Test
    fun `AllowedIPs formatted with comma-space separator`() {
        val ini = WarpIniBuilder.build(baseConfig)
        assertTrue(ini.contains("AllowedIPs = 0.0.0.0/0, ::/0"))
    }

    @Test
    fun `Interface section contains PrivateKey Address MTU`() {
        val ini = WarpIniBuilder.build(baseConfig)
        val ifacePart = ini.substringBefore("[Peer]")
        assertTrue(ifacePart.contains("PrivateKey = ${baseConfig.privateKey}"))
        assertTrue(ifacePart.contains("Address = ${baseConfig.interfaceAddressV4}, ${baseConfig.interfaceAddressV6}"))
        assertTrue(ifacePart.contains("MTU = 1280"))
    }

    @Test
    fun `custom keepalive reflected in Peer section`() {
        val config = baseConfig.copy(keepaliveSeconds = 30)
        val ini = WarpIniBuilder.build(config)
        assertTrue(ini.contains("PersistentKeepalive = 30"))
    }

    @Test
    fun `output starts with Interface section header`() {
        val ini = WarpIniBuilder.build(baseConfig)
        assertEquals("[Interface]", ini.lines().first())
    }

    @Test
    fun `Peer section follows Interface section`() {
        val ini = WarpIniBuilder.build(baseConfig)
        val ifaceIdx = ini.indexOf("[Interface]")
        val peerIdx = ini.indexOf("[Peer]")
        assertTrue(ifaceIdx < peerIdx, "Expected [Interface] before [Peer]")
    }

    @Test
    fun `MTU value matches WarpConfig`() {
        val config = baseConfig.copy(mtu = 1420)
        val ini = WarpIniBuilder.build(config)
        assertTrue(ini.contains("MTU = 1420"))
        assertFalse(ini.contains("MTU = 1280"))
    }

    @Test
    fun `IPv4-only config без IPv6 не добавляет trailing запятую`() {
        val config = baseConfig.copy(interfaceAddressV6 = "")
        val ini = WarpIniBuilder.build(config)
        assertTrue(ini.contains("Address = 172.16.0.2/32"))
        assertFalse(ini.contains("Address = 172.16.0.2/32,"), "Trailing запятая при пустом IPv6")
    }

    @Test
    fun `импортированный конфиг с ненулевыми AWG полями не пишет AWG в INI — WARP всегда vanilla`() {
        val config = baseConfig.copy(
            awgParams = AwgParams(
                junkPacketCount = 5,
                junkPacketMinSize = 100,
                junkPacketMaxSize = 200,
                initPacketJunkSize = 0,
                responsePacketJunkSize = 0,
                initPacketMagicHeader = 1L,
                responsePacketMagicHeader = 2L,
                cookieReplyMagicHeader = 3L,
                transportMagicHeader = 4L,
            ),
        )
        val ini = WarpIniBuilder.build(config)
        assertFalse(ini.contains("Jc ="), "Jc не должна попасть в INI: Cloudflare WARP = vanilla WireGuard, am-go при наличии Jc активирует AWG-обфускацию → Cloudflare дропает handshake")
        assertFalse(ini.contains("Jmin ="), "Jmin в INI → AWG обфускация → нет трафика")
        assertFalse(ini.contains("Jmax ="), "Jmax в INI → AWG обфускация → нет трафика")
        assertFalse(ini.contains("H1 ="), "H1 в INI → AWG обфускация → нет трафика")
        assertFalse(ini.contains("H2 ="), "H2 в INI → AWG обфускация → нет трафика")
        assertFalse(ini.contains("H3 ="), "H3 в INI → AWG обфускация → нет трафика")
        assertFalse(ini.contains("H4 ="), "H4 в INI → AWG обфускация → нет трафика")
        assertTrue(ini.contains("[Interface]"))
        assertTrue(ini.contains("[Peer]"))
        assertTrue(ini.contains("PrivateKey = ${baseConfig.privateKey}"))
    }

    @Test
    fun `VANILLA AwgParams не пишет AWG-строк (Cloudflare WARP vanilla handshake)`() {
        val config = baseConfig.copy(awgParams = AwgParams.VANILLA)
        val ini = WarpIniBuilder.build(config)
        assertFalse(ini.contains("Jc ="), "Jc не должна писаться в vanilla INI")
        assertFalse(ini.contains("Jmin ="), "Jmin не должна писаться в vanilla INI")
        assertFalse(ini.contains("Jmax ="), "Jmax не должна писаться в vanilla INI")
        assertFalse(ini.contains("S1 ="), "S1 не должна писаться в vanilla INI")
        assertFalse(ini.contains("S2 ="), "S2 не должна писаться в vanilla INI")
        assertFalse(ini.contains("H1 ="), "H1 не должна писаться в vanilla INI")
        assertFalse(ini.contains("H2 ="), "H2 не должна писаться в vanilla INI")
        assertFalse(ini.contains("H3 ="), "H3 не должна писаться в vanilla INI")
        assertFalse(ini.contains("H4 ="), "H4 не должна писаться в vanilla INI")
        assertTrue(ini.contains("[Interface]"))
        assertTrue(ini.contains("[Peer]"))
        assertTrue(ini.contains("PrivateKey = ${baseConfig.privateKey}"))
        assertTrue(ini.contains("PublicKey = ${baseConfig.peerPublicKey}"))
    }

    @Test
    fun `non-VANILLA AwgParams не пишет AWG-строк — WARP vanilla-only независимо от awgParams`() {
        val config = baseConfig.copy(
            awgParams = AwgParams(
                junkPacketCount = 7,
                junkPacketMinSize = 50,
                junkPacketMaxSize = 150,
                initPacketJunkSize = 10,
                responsePacketJunkSize = 20,
                initPacketMagicHeader = 100L,
                responsePacketMagicHeader = 200L,
                cookieReplyMagicHeader = 300L,
                transportMagicHeader = 400L,
            ),
        )
        val ini = WarpIniBuilder.build(config)
        assertFalse(ini.contains("Jc ="))
        assertFalse(ini.contains("Jmin ="))
        assertFalse(ini.contains("Jmax ="))
        assertFalse(ini.contains("S1 ="))
        assertFalse(ini.contains("S2 ="))
        assertFalse(ini.contains("H1 ="))
        assertFalse(ini.contains("H4 ="))
    }

    @Test
    fun `partial override AwgParams не пишет AWG-строк`() {
        val config = baseConfig.copy(
            awgParams = AwgParams.VANILLA.copy(junkPacketCount = 1),
        )
        val ini = WarpIniBuilder.build(config)
        assertFalse(ini.contains("Jc ="))
        assertFalse(ini.contains("Jmin ="))
        assertFalse(ini.contains("H1 ="))
    }
}
