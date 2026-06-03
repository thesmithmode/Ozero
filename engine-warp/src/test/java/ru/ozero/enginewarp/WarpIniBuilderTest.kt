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
    fun `VANILLA AwgParams не пишет AWG-строк`() {
        val ini = WarpIniBuilder.build(baseConfig.copy(awgParams = AwgParams.VANILLA))
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
    fun `default AwgParams пишет AWG-поля (Jc=5, Jmin=100, Jmax=200, H1-4=1-4)`() {
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
    fun `кастомные AWG параметры пишутся в INI ровно как заданы`() {
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
    fun `AWG-поля пишутся в Interface секцию, не в Peer`() {
        val ini = WarpIniBuilder.build(baseConfig)
        val ifacePart = ini.substringBefore("[Peer]")
        val peerPart = ini.substringAfter("[Peer]")
        assertTrue(ifacePart.contains("Jc = 5"))
        assertFalse(peerPart.contains("Jc ="))
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
        assertTrue(ifaceIdx < peerIdx)
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
        assertFalse(ini.contains("Address = 172.16.0.2/32,"))
    }

    @Test
    fun `partial override AwgParams (только Jc != VANILLA) пишет полный AWG-блок`() {
        val config = baseConfig.copy(
            awgParams = AwgParams.VANILLA.copy(junkPacketCount = 1),
        )
        val ini = WarpIniBuilder.build(config)
        assertTrue(ini.contains("Jc = 1"))
        assertTrue(ini.contains("Jmin = 0"))
        assertTrue(ini.contains("Jmax = 0"))
    }

    @Test
    fun `rebuild preserves unmodeled peer keys from existing raw ini`() {
        val rawIni = "[Interface]\n" +
            "PrivateKey = legacy\n" +
            "Address = 10.0.0.1/32, 2606:4700::1/128\n" +
            "DNS = 1.1.1.1\n" +
            "\n" +
            "[Peer]\n" +
            "PublicKey = peer\n" +
            "PresharedKey = very-secret\n" +
            "Endpoint = old.endpoint:2408\n"
        val rebuilt = WarpIniBuilder.build(baseConfig, rawIni)

        assertTrue(rebuilt.contains("PresharedKey = very-secret"))
        assertTrue(rebuilt.contains("PrivateKey = ${baseConfig.privateKey}"))
        assertTrue(rebuilt.contains("Endpoint = ${baseConfig.peerEndpoint}"))
        assertTrue(rebuilt.contains("MTU = 1280"))
    }
}
