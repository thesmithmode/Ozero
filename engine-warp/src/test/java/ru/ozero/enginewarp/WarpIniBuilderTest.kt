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
    fun `default AwgParams produces correct AWG lines in Interface section`() {
        val ini = WarpIniBuilder.build(baseConfig)
        assertTrue(ini.contains("Jc = 5"))
        assertTrue(ini.contains("Jmin = 100"))
        assertTrue(ini.contains("Jmax = 200"))
        assertTrue(ini.contains("S1 = 0"))
        assertTrue(ini.contains("S2 = 0"))
        assertTrue(ini.contains("H1 = 1"))
        assertTrue(ini.contains("H2 = 2"))
        assertTrue(ini.contains("H3 = 3"))
        assertTrue(ini.contains("H4 = 4"))
    }

    @Test
    fun `custom AwgParams values are reflected in output`() {
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
        assertTrue(ini.contains("Jc = 10"))
        assertTrue(ini.contains("Jmin = 50"))
        assertTrue(ini.contains("Jmax = 150"))
        assertTrue(ini.contains("S1 = 15"))
        assertTrue(ini.contains("S2 = 20"))
        assertTrue(ini.contains("H1 = 100"))
        assertTrue(ini.contains("H2 = 200"))
        assertTrue(ini.contains("H3 = 300"))
        assertTrue(ini.contains("H4 = 400"))
    }

    @Test
    fun `AWG keys appear in Interface section not in Peer section`() {
        val ini = WarpIniBuilder.build(baseConfig)
        val interfacePart = ini.substringBefore("[Peer]")
        val peerPart = ini.substringAfter("[Peer]")
        assertTrue(interfacePart.contains("Jc ="))
        assertTrue(interfacePart.contains("H1 ="))
        assertFalse(peerPart.contains("Jc ="))
        assertFalse(peerPart.contains("H1 ="))
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
    fun `H-values as Long render without scientific notation`() {
        val config = baseConfig.copy(
            awgParams = AwgParams(
                initPacketMagicHeader = 2147483648L,
                responsePacketMagicHeader = 3000000000L,
                cookieReplyMagicHeader = 4294967295L,
                transportMagicHeader = 999999999L,
            ),
        )
        val ini = WarpIniBuilder.build(config)
        assertTrue(ini.contains("H1 = 2147483648"), "Expected H1 = 2147483648, got: $ini")
        assertTrue(ini.contains("H2 = 3000000000"), "Expected H2 = 3000000000, got: $ini")
        assertTrue(ini.contains("H3 = 4294967295"), "Expected H3 = 4294967295, got: $ini")
        assertTrue(ini.contains("H4 = 999999999"), "Expected H4 = 999999999, got: $ini")
    }

    @Test
    fun `Interface section contains PrivateKey Address MTU`() {
        val ini = WarpIniBuilder.build(baseConfig)
        val ifacePart = ini.substringBefore("[Peer]")
        assertTrue(ifacePart.contains("PrivateKey = ${baseConfig.privateKey}"))
        assertTrue(ifacePart.contains("Address = ${baseConfig.interfaceAddressV4},${baseConfig.interfaceAddressV6}"))
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
}
