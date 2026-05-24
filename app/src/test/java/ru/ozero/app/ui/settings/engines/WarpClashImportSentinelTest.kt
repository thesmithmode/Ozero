package ru.ozero.app.ui.settings.engines

import org.junit.jupiter.api.Test
import ru.ozero.enginewarp.ClashYamlParser
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WarpClashImportSentinelTest {

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
        - name: "[WARP] 188.114.97.1:2408"
          <<: *warp-common
          server: 188.114.97.1
          port: 2408
        - name: "[WARP] 188.114.97.2:500"
          <<: *warp-common
          server: 188.114.97.2
          port: 500
    """.trimIndent()

    @Test
    fun `slotNameFromFilename WARPc18455 yaml returns Ozero-Ultra-18455`() {
        assertEquals("Ozero-Ultra-18455", ClashYamlParser.slotNameFromFilename("WARPc18455.yaml"))
    }

    @Test
    fun `slotNameFromFilename config yaml fallback starts with Ozero-Ultra-`() {
        val name = ClashYamlParser.slotNameFromFilename("config.yaml")
        assertTrue(name.startsWith("Ozero-Ultra-"), "должен начинаться с Ozero-Ultra-")
        assertTrue(name.removePrefix("Ozero-Ultra-").isNotEmpty())
    }

    @Test
    fun `slotNameFromFilename fallback deterministic`() {
        assertEquals(
            ClashYamlParser.slotNameFromFilename("config.yaml"),
            ClashYamlParser.slotNameFromFilename("config.yaml"),
        )
    }

    @Test
    fun `parse returns non-empty endpoints`() {
        val result = ClashYamlParser.parse(minimalYaml)
        assertTrue(result.endpoints.isNotEmpty(), "parse должен вернуть непустой список эндпоинтов")
    }

    @Test
    fun `parse I1 hex blob preserved exactly`() {
        val result = ClashYamlParser.parse(minimalYaml)
        assertEquals("ce000001", result.awgParams.payloadHexI1, "I1 hex blob должен сохраняться точно")
    }

    @Test
    fun `parse H3=4 H4=3 swap preserved`() {
        val result = ClashYamlParser.parse(minimalYaml)
        assertEquals(4L, result.awgParams.cookieReplyMagicHeader, "H3 должен быть 4")
        assertEquals(3L, result.awgParams.transportMagicHeader, "H4 должен быть 3")
    }

    @Test
    fun `parse large I1 hex blob preserved exactly`() {
        val bigHex = "ce" + "00".repeat(50)
        val yaml = minimalYaml.replace("<b 0xce000001>", "<b 0x$bigHex>")
        val result = ClashYamlParser.parse(yaml)
        assertEquals(bigHex, result.awgParams.payloadHexI1)
    }
}
