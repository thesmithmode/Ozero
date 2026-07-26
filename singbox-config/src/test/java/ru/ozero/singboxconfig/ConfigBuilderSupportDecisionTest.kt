package ru.ozero.singboxconfig

import org.junit.jupiter.api.Test
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxfmt.ShadowsocksBean
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse

class ConfigBuilderSupportDecisionTest {
    private val validRealityPublicKey = "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA"

    @Test
    fun `tcp without header is supported`() {
        assertEquals(BeanSupportDecision.Supported, vless().apply { headerType = "none" }.supportDecision())
    }

    @Test
    fun `tcp http header is rejected until transport is implemented`() {
        val decision = vless().apply { headerType = "http" }.supportDecision()

        assertEquals(BeanSupportError.UNSUPPORTED_TCP_HEADER, assertIs<BeanSupportDecision.Unsupported>(decision).error)
    }

    @Test
    fun `xhttp and splithttp are unsupported transports`() {
        listOf("xhttp", "splithttp").forEach { transport ->
            val decision = vless(type = transport).supportDecision()

            assertSupportError(BeanSupportError.UNSUPPORTED_TRANSPORT, decision)
        }
    }

    @Test
    fun `invalid reality public key is rejected`() {
        val decision = reality().apply { realityPublicKey = "invalid" }.supportDecision()

        assertSupportError(BeanSupportError.INVALID_REALITY_PUBLIC_KEY, decision)
    }

    @Test
    fun `invalid reality short id is rejected`() {
        val decision = reality().apply { realityShortId = "not-hex" }.supportDecision()

        assertSupportError(BeanSupportError.INVALID_REALITY_SHORT_ID, decision)
    }

    @Test
    fun `valid VLESS Reality TCP is supported and builds JSON`() {
        val bean = reality()

        assertEquals(BeanSupportDecision.Supported, bean.supportDecision())
        assertContains(ConfigBuilder.buildSingboxConfig(bean), "\"reality\":")
    }

    @Test
    fun `unknown VLESS flow is rejected`() {
        val decision = vless().apply { flow = "xtls-rprx-direct" }.supportDecision()

        assertSupportError(BeanSupportError.UNSUPPORTED_VLESS_FLOW, decision)
    }

    @Test
    fun `Shadowsocks plugin is rejected`() {
        val bean = ShadowsocksBean().apply {
            serverAddress = "proxy.example.com"
            serverPort = 443
            method = "aes-128-gcm"
            password = "secret"
            plugin = "unknown"
        }

        assertSupportError(BeanSupportError.UNSUPPORTED_SHADOWSOCKS_PLUGIN, ConfigBuilder.supportDecision(bean))
    }

    @Test
    fun `legacy raw transport builds canonical TCP without transport object`() {
        val json = ConfigBuilder.buildSingboxConfig(vless(type = " RAW "))

        assertFalse(json.contains("\"transport\""))
    }

    private fun assertSupportError(error: BeanSupportError, decision: BeanSupportDecision) {
        assertEquals(error, assertIs<BeanSupportDecision.Unsupported>(decision).error)
    }

    private fun VLESSBean.supportDecision(): BeanSupportDecision = ConfigBuilder.supportDecision(this)

    private fun reality(): VLESSBean = vless(security = "reality").apply {
        sni = "front.example.com"
        realityPublicKey = validRealityPublicKey
        realityShortId = "ab12"
    }

    private fun vless(
        type: String = "tcp",
        security: String = "none",
    ): VLESSBean = VLESSBean().apply {
        uuid = "12345678-1234-1234-1234-123456789abc"
        serverAddress = "proxy.example.com"
        serverPort = 443
        this.type = type
        this.security = security
    }
}
