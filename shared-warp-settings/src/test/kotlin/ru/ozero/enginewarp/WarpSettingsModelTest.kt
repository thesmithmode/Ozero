package ru.ozero.enginewarp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WarpSettingsModelTest {

    @Test
    fun `buildNextWarpSlotName skips used ozero numbers`() {
        val slots = listOf(
            slot(name = "Ozero-1"),
            slot(name = "Manual"),
            slot(name = "Ozero-3"),
        )

        assertEquals("Ozero-2", buildNextWarpSlotName(slots))
    }

    @Test
    fun `draftFromSlot maps config fields`() {
        val slot = slot(name = "Office", config = sampleConfig())

        val draft = draftFromSlot(slot)

        assertEquals(slot.id, draft.slotId)
        assertEquals("Office", draft.name)
        assertEquals("engage.cloudflareclient.com:2408", draft.endpoint)
        assertEquals("10.0.0.2/32", draft.addressV4)
        assertEquals("1.1.1.1, 8.8.8.8", draft.dns)
        assertEquals("7", draft.jc)
    }

    @Test
    fun `toWarpConfig trims fields and normalizes awg min max`() {
        val draft = draftFromSlot(slot(config = sampleConfig())).copy(
            endpoint = " endpoint:2408 ",
            privateKey = " private ",
            peerPublicKey = " peer ",
            addressV4 = " 10.1.0.2/32 ",
            dns = " 9.9.9.9, , 1.1.1.1 ",
            jmin = "300",
            jmax = "100",
        )

        val config = draft.toWarpConfig()

        assertEquals("endpoint:2408", config.peerEndpoint)
        assertEquals("private", config.privateKey)
        assertEquals("peer", config.peerPublicKey)
        assertEquals("10.1.0.2/32", config.interfaceAddressV4)
        assertEquals(listOf("9.9.9.9", "1.1.1.1"), config.dnsServers)
        assertEquals(100, config.awgParams.junkPacketMinSize)
        assertEquals(300, config.awgParams.junkPacketMaxSize)
    }

    @Test
    fun `toWarpConfig uses defaults for invalid numeric fields`() {
        val draft = draftFromSlot(slot(config = sampleConfig())).copy(
            mtu = "bad",
            keepalive = "bad",
            jc = "bad",
            dns = "",
        )

        val config = draft.toWarpConfig()

        assertEquals(WarpConfig.DEFAULT_MTU, config.mtu)
        assertEquals(WarpConfig.DEFAULT_KEEPALIVE, config.keepaliveSeconds)
        assertEquals(AwgParams.DEFAULT_JC, config.awgParams.junkPacketCount)
        assertEquals(WarpConfig.DEFAULT_DNS, config.dnsServers)
    }

    @Test
    fun `hasRequiredFields validates minimum start fields`() {
        val valid = draftFromSlot(slot(config = sampleConfig()))

        assertTrue(hasRequiredFields(valid))
        assertFalse(hasRequiredFields(valid.copy(privateKey = "")))
        assertFalse(hasRequiredFields(valid.copy(peerPublicKey = "")))
        assertFalse(hasRequiredFields(valid.copy(endpoint = "")))
        assertFalse(hasRequiredFields(valid.copy(addressV4 = "")))
    }

    @Test
    fun `parser and builder round trip required fields`() {
        val built = WarpIniBuilder.build(sampleConfig())
        val parsed = WarpConfParser.parse(built).getOrThrow()

        assertEquals("private-key", parsed.privateKey)
        assertEquals("peer-key", parsed.peerPublicKey)
        assertEquals("engage.cloudflareclient.com:2408", parsed.peerEndpoint)
        assertEquals("10.0.0.2/32", parsed.interfaceAddressV4)
        assertEquals("2606:4700::2/128", parsed.interfaceAddressV6)
    }

    private fun slot(
        name: String = "Ozero-1",
        config: WarpConfig = sampleConfig(),
    ): WarpConfigSlot = WarpConfigSlot(name = name, config = config)

    private fun sampleConfig(): WarpConfig =
        WarpConfig(
            privateKey = "private-key",
            publicKey = "public-key",
            peerPublicKey = "peer-key",
            peerEndpoint = "engage.cloudflareclient.com:2408",
            interfaceAddressV4 = "10.0.0.2/32",
            interfaceAddressV6 = "2606:4700::2/128",
            dnsServers = listOf("1.1.1.1", "8.8.8.8"),
            awgParams = AwgParams(junkPacketCount = 7),
        )
}
