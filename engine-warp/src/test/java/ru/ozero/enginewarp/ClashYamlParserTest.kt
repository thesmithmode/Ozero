package ru.ozero.enginewarp

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ClashYamlParserTest {

    private val minimalYaml = """
        warp-common: &warp-common
          type: wireguard
          ip: 172.16.0.2
          ipv6: 2606:4700:110:8a07:c9b6:2e4b:59b2:d428
          private-key: AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
          public-key: BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=
          mtu: 1280
          dns: [1.1.1.1, 8.8.8.8]
          amnezia-wg-option:
           jc: 4
           jmin: 40
           jmax: 70
           s1: 0
           s2: 0
           h1: 1
           h2: 2
           h4: 3
           h3: 4
           i1: <b 0xce000001>

        proxies:
        - name: "[⭐] 188.114.97.1:2408"
          <<: *warp-common
          server: 188.114.97.1
          port: 2408
        - name: "[⭐] 188.114.97.2:500"
          <<: *warp-common
          server: 188.114.97.2
          port: 500
    """.trimIndent()

    @Test
    fun `parse returns two endpoints`() {
        val result = ClashYamlParser.parse(minimalYaml)
        assertEquals(listOf("188.114.97.1:2408", "188.114.97.2:500"), result.endpoints)
    }

    @Test
    fun `parse extracts privateKey and peerPublicKey`() {
        val result = ClashYamlParser.parse(minimalYaml)
        assertEquals("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", result.privateKey)
        assertEquals("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=", result.peerPublicKey)
    }

    @Test
    fun `parse adds cidr to ip and ipv6`() {
        val result = ClashYamlParser.parse(minimalYaml)
        assertEquals("172.16.0.2/32", result.interfaceAddressV4)
        assertEquals("2606:4700:110:8a07:c9b6:2e4b:59b2:d428/128", result.interfaceAddressV6)
    }

    @Test
    fun `parse extracts dns list`() {
        val result = ClashYamlParser.parse(minimalYaml)
        assertEquals(listOf("1.1.1.1", "8.8.8.8"), result.dnsServers)
    }

    @Test
    fun `parse extracts mtu`() {
        val result = ClashYamlParser.parse(minimalYaml)
        assertEquals(1280, result.mtu)
    }

    @Test
    fun `parse awgParams h3=4 h4=3 swap`() {
        val result = ClashYamlParser.parse(minimalYaml)
        assertEquals(4, result.awgParams.junkPacketCount)
        assertEquals(40, result.awgParams.junkPacketMinSize)
        assertEquals(70, result.awgParams.junkPacketMaxSize)
        assertEquals(4L, result.awgParams.cookieReplyMagicHeader, "H3 должен быть 4")
        assertEquals(3L, result.awgParams.transportMagicHeader, "H4 должен быть 3")
    }

    @Test
    fun `parse extracts i1 hex blob`() {
        val result = ClashYamlParser.parse(minimalYaml)
        assertEquals("ce000001", result.awgParams.payloadHexI1)
    }

    @Test
    fun `parse throws on empty yaml`() {
        assertFailsWith<ClashYamlParser.ParseException> {
            ClashYamlParser.parse("")
        }
    }

    @Test
    fun `parse throws when no proxies`() {
        val noProxies = minimalYaml.replace("proxies:", "proxy-groups:")
        assertFailsWith<ClashYamlParser.ParseException> {
            ClashYamlParser.parse(noProxies)
        }
    }

    @Test
    fun `parse large i1 hex blob preserved exactly`() {
        val bigHex = "ce" + "00".repeat(50)
        val yaml = minimalYaml.replace("<b 0xce000001>", "<b 0x$bigHex>")
        val result = ClashYamlParser.parse(yaml)
        assertEquals(bigHex, result.awgParams.payloadHexI1)
    }

    @Test
    fun `parse ip without cidr gets cidr added`() {
        val result = ClashYamlParser.parse(minimalYaml)
        assertTrue(result.interfaceAddressV4.endsWith("/32"))
    }

    @Test
    fun `slotNameFromFilename with digits`() {
        assertEquals("Ozero-Ultra-18455", ClashYamlParser.slotNameFromFilename("WARPc18455.yaml"))
        assertEquals("Ozero-Ultra-2024", ClashYamlParser.slotNameFromFilename("config2024.yml"))
    }

    @Test
    fun `slotNameFromFilename fallback starts with Ozero-Ultra-`() {
        val name = ClashYamlParser.slotNameFromFilename("config.yaml")
        assertTrue(name.startsWith("Ozero-Ultra-"), "должен начинаться с Ozero-Ultra-")
        assertTrue(name.removePrefix("Ozero-Ultra-").isNotEmpty())
    }

    @Test
    fun `slotNameFromFilename fallback deterministic`() {
        val a = ClashYamlParser.slotNameFromFilename("config.yaml")
        val b = ClashYamlParser.slotNameFromFilename("config.yaml")
        assertEquals(a, b)
    }
}
