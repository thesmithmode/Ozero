package ru.ozero.enginewarp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
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
    fun `buildNextWarpSlotName starts from one when no numeric ozero names exist`() {
        val slots = listOf(slot(name = "Manual"), slot(name = "Ozero-alpha"))

        assertEquals("Ozero-1", buildNextWarpSlotName(slots))
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
    fun `emptyWarpDraft has no required fields`() {
        val draft = emptyWarpDraft()

        assertFalse(hasRequiredFields(draft))
        assertEquals("WARP", draft.name)
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

    @Test
    fun `parser applies cidr suffixes and ignores comments`() {
        val parsed = WarpConfParser.parse(
            """
            # ignored
            [Interface]
            PrivateKey = private-key # hidden
            Address = 10.0.0.2, 2606:4700::2
            DNS = , 9.9.9.9 ,
            MTU = bad
            Jc = bad
            I1 = <b 0x0A0b>
            I2 = 0x0102
            I3 = <b 0x010203>
            I4 = bad
            I5 = 7

            [Peer]
            PublicKey = peer-key
            Endpoint = endpoint:2408
            PersistentKeepalive = bad
            """.trimIndent(),
        ).getOrThrow()

        assertEquals("10.0.0.2/32", parsed.interfaceAddressV4)
        assertEquals("2606:4700::2/128", parsed.interfaceAddressV6)
        assertEquals(listOf("9.9.9.9"), parsed.dnsServers)
        assertEquals(WarpConfig.DEFAULT_MTU, parsed.mtu)
        assertEquals(WarpConfig.DEFAULT_KEEPALIVE, parsed.keepaliveSeconds)
        assertEquals("0a0b", parsed.awgParams.payloadHexI1)
        assertEquals("0102", parsed.awgParams.payloadHexI2)
        assertEquals("010203", parsed.awgParams.payloadHexI3)
        assertEquals(AwgParams.DEFAULT_I4, parsed.awgParams.specialJunk4)
        assertEquals(7, parsed.awgParams.payloadPacketSizeCount3)
    }

    @Test
    fun `parser reports missing required fields`() {
        val result = WarpConfParser.parse("[Interface]\nAddress = 10.0.0.2/32")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("PrivateKey"))
    }

    @Test
    fun `builder omits awg section for vanilla params and blank ipv6`() {
        val built = WarpIniBuilder.build(
            sampleConfig().copy(
                interfaceAddressV6 = "",
                awgParams = AwgParams.VANILLA,
            ),
        )

        assertTrue(built.contains("Address = 10.0.0.2/32"))
        assertFalse(built.contains("Jc ="))
    }

    @Test
    fun `builder formats numeric and hex payload params`() {
        val awg = AwgParams(
            underloadPacketJunkSize = 3,
            payloadPacketJunkSize = 4,
            payloadPacketSizeCount1 = 5,
            payloadPacketSizeCount2 = 6,
            payloadPacketSizeCount3 = 7,
            specialJunk3 = 8,
            specialJunk4 = 9,
            payloadHexI1 = "0a0b",
            payloadHexI2 = "0102",
            payloadHexI3 = "010203",
            payloadHexI4 = "040506",
            payloadHexI5 = "0708",
        )

        val built = WarpIniBuilder.build(sampleConfig().copy(awgParams = awg))

        assertTrue(built.contains("S3 = 3"))
        assertTrue(built.contains("S4 = 4"))
        assertTrue(built.contains("I1 = <b 0x0a0b>"))
        assertTrue(built.contains("I2 = <b 0x0102>"))
        assertTrue(built.contains("I3 = <b 0x010203>"))
        assertTrue(built.contains("I4 = <b 0x040506>"))
        assertTrue(built.contains("I5 = <b 0x0708>"))
    }

    @Test
    fun `AwgParams rejects invalid values`() {
        assertThrows(IllegalArgumentException::class.java) {
            AwgParams(junkPacketMinSize = 200, junkPacketMaxSize = 100)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AwgParams(junkPacketCount = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AwgParams(initPacketJunkSize = 2000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AwgParams(initPacketMagicHeader = 1, responsePacketMagicHeader = 1)
        }
    }

    @Test
    fun `WarpConfig toString redacts short and long secrets`() {
        val longPeer = sampleConfig().toString()
        val shortPeer = sampleConfig().copy(peerPublicKey = "short").toString()

        assertTrue(longPeer.contains("peerPublicKey=***peer-key"))
        assertTrue(shortPeer.contains("peerPublicKey=***"))
        assertFalse(longPeer.contains("private-key"))
        assertFalse(longPeer.contains("2606:4700::2"))
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
