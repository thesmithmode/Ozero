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
    fun `ui state and doh providers expose defaults and values`() {
        val draft = emptyWarpDraft()
        val state = WarpSettingsUiState(
            slots = listOf(slot()),
            activeSlotId = "active",
            isRegistering = true,
            errorMessage = "error",
            errorMessageRes = 7,
            progressMirror = "mirror",
            progressCurrent = 1,
            progressTotal = 2,
            importSuccessCount = 3,
            editDraft = draft,
            isProvingEndpoints = true,
            provingProgress = "1/2",
        )

        assertEquals("active", state.activeSlotId)
        assertTrue(state.isRegistering)
        assertEquals("error", state.errorMessage)
        assertEquals(7, state.errorMessageRes)
        assertEquals("mirror", state.progressMirror)
        assertEquals(1, state.progressCurrent)
        assertEquals(2, state.progressTotal)
        assertEquals(3, state.importSuccessCount)
        assertEquals(draft, state.editDraft)
        assertTrue(state.isProvingEndpoints)
        assertEquals("1/2", state.provingProgress)
        assertEquals(emptyList<WarpConfigSlot>(), WarpSettingsUiState().slots)
        assertTrue(DoHProvider.SYSTEM.isSystem)
        assertFalse(DoHProvider.CLOUDFLARE_1111.isSystem)
        assertEquals(DoHProvider.CLOUDFLARE_1111, WarpConfig.DEFAULT_DOH_PROVIDER)
        assertEquals(WarpConfig.DEFAULT_DOH_PROVIDER, emptyWarpDraft().doHProvider)
        assertEquals("Cloudflare (1.1.1.1)", DoHProvider.CLOUDFLARE_1111.displayName)
        assertEquals("https://1.1.1.1/dns-query", DoHProvider.CLOUDFLARE_1111.url)
        assertTrue(DoHProvider.entries.contains(DoHProvider.GOOGLE_8888))
    }

    @Test
    fun `slot and config expose optional metadata`() {
        val config = sampleConfig().copy(accountLicense = "license")
        val slot = WarpConfigSlot(
            name = "Meta",
            config = config,
            isActive = true,
            rawIniOverride = "ini",
            endpointList = listOf("endpoint:2408"),
        )

        assertEquals("license", slot.config.accountLicense)
        assertTrue(slot.isActive)
        assertEquals("ini", slot.rawIniOverride)
        assertEquals(listOf("endpoint:2408"), slot.endpointList)
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
    fun `buildNextWarpSlotName treats zero negative and padded names as used integers`() {
        val slots = listOf(
            slot(name = "Ozero-0"),
            slot(name = "Ozero--1"),
            slot(name = "Ozero-01"),
            slot(name = "Ozero-2"),
            slot(name = "Ozero- 3"),
        )

        assertEquals("Ozero-3", buildNextWarpSlotName(slots))
    }

    @Test
    fun `parseDnsServers trims blanks and falls back to defaults`() {
        assertEquals(listOf("1.1.1.1", "8.8.8.8"), parseDnsServers(" 1.1.1.1, ,8.8.8.8 "))
        assertEquals(WarpConfig.DEFAULT_DNS, parseDnsServers(""))
        assertEquals(WarpConfig.DEFAULT_DNS, parseDnsServers(" , , "))
    }

    @Test
    fun `hasRequiredFields rejects whitespace-only required fields`() {
        val valid = draftFromSlot(slot(config = sampleConfig()))

        assertFalse(hasRequiredFields(valid.copy(privateKey = "   ")))
        assertFalse(hasRequiredFields(valid.copy(peerPublicKey = "\t")))
        assertFalse(hasRequiredFields(valid.copy(endpoint = "\n")))
        assertFalse(hasRequiredFields(valid.copy(addressV4 = " ")))
    }

    @Test
    fun `toWarpConfig maps all numeric draft fields`() {
        val draft = draftFromSlot(slot(config = sampleConfig())).copy(
            mtu = "1300",
            keepalive = "25",
            jc = "8",
            jmin = "12",
            jmax = "24",
            s1 = "36",
            s2 = "48",
            h1 = "111",
            h2 = "222",
            h3 = "333",
            h4 = "444",
            doHProvider = DoHProvider.GOOGLE_8888,
        )

        val config = draft.toWarpConfig()

        assertEquals(1300, config.mtu)
        assertEquals(25, config.keepaliveSeconds)
        assertEquals(8, config.awgParams.junkPacketCount)
        assertEquals(12, config.awgParams.junkPacketMinSize)
        assertEquals(24, config.awgParams.junkPacketMaxSize)
        assertEquals(36, config.awgParams.initPacketJunkSize)
        assertEquals(48, config.awgParams.responsePacketJunkSize)
        assertEquals(111L, config.awgParams.initPacketMagicHeader)
        assertEquals(222L, config.awgParams.responsePacketMagicHeader)
        assertEquals(333L, config.awgParams.cookieReplyMagicHeader)
        assertEquals(444L, config.awgParams.transportMagicHeader)
        assertEquals(DoHProvider.GOOGLE_8888, config.doHProvider)
    }

    @Test
    fun `draftFromConfig uses supplied slot id and name defaults`() {
        val defaultDraft = draftFromConfig(sampleConfig())
        val customDraft = draftFromConfig(sampleConfig(), slotId = "slot-9", name = "Custom")

        assertEquals("", defaultDraft.slotId)
        assertEquals("WARP", defaultDraft.name)
        assertEquals("slot-9", customDraft.slotId)
        assertEquals("Custom", customDraft.name)
    }
}

class WarpConfParserTest {

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
    fun `parser reports each missing required field`() {
        val noAddress = WarpConfParser.parse(
            """
            [Interface]
            PrivateKey = private-key
            [Peer]
            PublicKey = peer-key
            Endpoint = endpoint:2408
            """.trimIndent(),
        )
        val noPeerKey = WarpConfParser.parse(
            """
            [Interface]
            PrivateKey = private-key
            Address = 10.0.0.2/32
            [Peer]
            Endpoint = endpoint:2408
            """.trimIndent(),
        )
        val noEndpoint = WarpConfParser.parse(
            """
            [Interface]
            PrivateKey = private-key
            Address = 10.0.0.2/32
            [Peer]
            PublicKey = peer-key
            """.trimIndent(),
        )

        assertTrue(noAddress.exceptionOrNull()!!.message!!.contains("Address"))
        assertTrue(noPeerKey.exceptionOrNull()!!.message!!.contains("PublicKey"))
        assertTrue(noEndpoint.exceptionOrNull()!!.message!!.contains("Endpoint"))
    }

    @Test
    fun `parser handles empty cidr inputs and default dns`() {
        val parsed = WarpConfParser.parse(
            """
            ignored = value
            [Interface]
            PrivateKey = private-key
            Address = ,
            DNS = ,
            broken-line
            [Peer]
            PublicKey = peer-key
            Endpoint = endpoint:2408
            """.trimIndent(),
        ).getOrThrow()

        assertEquals("", parsed.interfaceAddressV4)
        assertEquals("", parsed.interfaceAddressV6)
        assertEquals(WarpConfig.DEFAULT_DNS, parsed.dnsServers)
    }

    @Test
    fun `parser preserves only ipv4 or ipv6 address inputs`() {
        val ipv4Only = WarpConfParser.parse(
            """
            [Interface]
            PrivateKey = private-key
            Address = 10.0.0.2
            [Peer]
            PublicKey = peer-key
            Endpoint = endpoint:2408
            """.trimIndent(),
        ).getOrThrow()
        val ipv6Only = WarpConfParser.parse(
            """
            [Interface]
            PrivateKey = private-key
            Address = 2606:4700::2
            [Peer]
            PublicKey = peer-key
            Endpoint = endpoint:2408
            """.trimIndent(),
        ).getOrThrow()

        assertEquals("10.0.0.2/32", ipv4Only.interfaceAddressV4)
        assertEquals("", ipv4Only.interfaceAddressV6)
        assertEquals("", ipv6Only.interfaceAddressV4)
        assertEquals("2606:4700::2/128", ipv6Only.interfaceAddressV6)
    }

    @Test
    fun `parser preserves cidr addresses and integer awg payloads`() {
        val parsed = WarpConfParser.parse(
            """
            [Interface]
            PrivateKey = private-key
            Address = 10.0.0.2/32, 2606:4700::2/128
            I1 = 11
            I2 = 12
            I3 = 13
            I4 = 14
            I5 = 15
            S3 = 16
            S4 = 17
            [Peer]
            PublicKey = peer-key
            Endpoint = endpoint:2408
            """.trimIndent(),
        ).getOrThrow()

        assertEquals("10.0.0.2/32", parsed.interfaceAddressV4)
        assertEquals("2606:4700::2/128", parsed.interfaceAddressV6)
        assertEquals(11, parsed.awgParams.payloadPacketSizeCount1)
        assertEquals(12, parsed.awgParams.payloadPacketSizeCount2)
        assertEquals(13, parsed.awgParams.specialJunk3)
        assertEquals(14, parsed.awgParams.specialJunk4)
        assertEquals(15, parsed.awgParams.payloadPacketSizeCount3)
        assertEquals(16, parsed.awgParams.underloadPacketJunkSize)
        assertEquals(17, parsed.awgParams.payloadPacketJunkSize)
    }

    @Test
    fun `parser ignores malformed hex payloads`() {
        val parsed = WarpConfParser.parse(
            """
            [Interface]
            PrivateKey = private-key
            Address = 10.0.0.2/32
            I1 = 0x1
            I2 = <b 0x123>
            I3 = <b 0xzz>
            I4 = <x 0x0102>
            I5 = <b 0102>
            [Peer]
            PublicKey = peer-key
            Endpoint = endpoint:2408
            """.trimIndent(),
        ).getOrThrow()

        assertEquals(AwgParams.DEFAULT_I1, parsed.awgParams.payloadPacketSizeCount1)
        assertEquals(AwgParams.DEFAULT_I2, parsed.awgParams.payloadPacketSizeCount2)
        assertEquals(AwgParams.DEFAULT_I3, parsed.awgParams.specialJunk3)
        assertEquals(AwgParams.DEFAULT_I4, parsed.awgParams.specialJunk4)
        assertEquals(AwgParams.DEFAULT_I5, parsed.awgParams.payloadPacketSizeCount3)
        assertEquals("0102", parsed.awgParams.payloadHexI5)
    }

    @Test
    fun `parser falls back when awg hex fields are absent entirely`() {
        val parsed = WarpConfParser.parse(
            """
            [Interface]
            PrivateKey = private-key
            Address = 10.0.0.2
            [Peer]
            PublicKey = peer-key
            Endpoint = endpoint:2408
            """.trimIndent(),
        ).getOrThrow()

        assertEquals(null, parsed.awgParams.payloadHexI1)
        assertEquals(null, parsed.awgParams.payloadHexI2)
        assertEquals(null, parsed.awgParams.payloadHexI3)
        assertEquals(null, parsed.awgParams.payloadHexI4)
        assertEquals(null, parsed.awgParams.payloadHexI5)
    }

    @Test
    fun `parser falls back for invalid numeric awg fields`() {
        val parsed = WarpConfParser.parse(
            """
            [Interface]
            PrivateKey = private-key
            Address = 10.0.0.2
            Jc = bad
            Jmin = bad
            Jmax = bad
            S1 = bad
            S2 = bad
            S3 = bad
            S4 = bad
            H1 = bad
            H2 = bad
            H3 = bad
            H4 = bad
            I1 = <b 0X0A0B>
            I2 = 0X0102
            I3 = <b 0x010203
            [Peer]
            PublicKey = peer-key
            Endpoint = endpoint:2408
            """.trimIndent(),
        ).getOrThrow()

        assertEquals("10.0.0.2/32", parsed.interfaceAddressV4)
        assertEquals("", parsed.interfaceAddressV6)
        assertEquals(AwgParams.DEFAULT_JC, parsed.awgParams.junkPacketCount)
        assertEquals(AwgParams.DEFAULT_JMIN, parsed.awgParams.junkPacketMinSize)
        assertEquals(AwgParams.DEFAULT_JMAX, parsed.awgParams.junkPacketMaxSize)
        assertEquals(AwgParams.DEFAULT_S1, parsed.awgParams.initPacketJunkSize)
        assertEquals(AwgParams.DEFAULT_S2, parsed.awgParams.responsePacketJunkSize)
        assertEquals(AwgParams.DEFAULT_S3, parsed.awgParams.underloadPacketJunkSize)
        assertEquals(AwgParams.DEFAULT_S4, parsed.awgParams.payloadPacketJunkSize)
        assertEquals(AwgParams.DEFAULT_H1, parsed.awgParams.initPacketMagicHeader)
        assertEquals(AwgParams.DEFAULT_H2, parsed.awgParams.responsePacketMagicHeader)
        assertEquals(AwgParams.DEFAULT_H3, parsed.awgParams.cookieReplyMagicHeader)
        assertEquals(AwgParams.DEFAULT_H4, parsed.awgParams.transportMagicHeader)
        assertEquals("0a0b", parsed.awgParams.payloadHexI1)
        assertEquals("0102", parsed.awgParams.payloadHexI2)
        assertEquals(AwgParams.DEFAULT_I3, parsed.awgParams.specialJunk3)
    }
}

class WarpIniBuilderTest {

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
    fun `builder formats integer payload params`() {
        val awg = AwgParams(
            payloadPacketSizeCount1 = 1,
            payloadPacketSizeCount2 = 2,
            specialJunk3 = 3,
            specialJunk4 = 4,
            payloadPacketSizeCount3 = 5,
        )

        val built = WarpIniBuilder.build(sampleConfig().copy(interfaceAddressV4 = "", awgParams = awg))

        assertTrue(built.contains("Address = 2606:4700::2/128"))
        assertTrue(built.contains("I1 = 1"))
        assertTrue(built.contains("I2 = 2"))
        assertTrue(built.contains("I3 = 3"))
        assertTrue(built.contains("I4 = 4"))
        assertTrue(built.contains("I5 = 5"))
    }

    @Test
    fun `builder preserves raw peer fields while rebuilding known sections`() {
        val built = WarpIniBuilder.build(
            sampleConfig().copy(
                interfaceAddressV4 = "10.9.0.2/32",
                peerEndpoint = "new.example.com:2408",
            ),
            """
            [Interface]
            PrivateKey = old-private
            Address = 10.8.0.2/32
            DNS = 9.9.9.9

            [Peer]
            PublicKey = old-peer
            PresharedKey = shared-secret
            Endpoint = old.example.com:1234
            AllowedIPs = 0.0.0.0/0
            """.trimIndent(),
        )

        assertTrue(built.contains("PrivateKey = private-key"))
        assertTrue(built.contains("Address = 10.9.0.2/32, 2606:4700::2/128"))
        assertTrue(built.contains("Endpoint = new.example.com:2408"))
        assertTrue(built.contains("PresharedKey = shared-secret"))
        assertTrue(built.contains("AllowedIPs = 0.0.0.0/0"))
    }

    @Test
    fun `builder merges preserved raw ini with unknown sections and blank preserve shortcut`() {
        val generated = WarpIniBuilder.build(sampleConfig())
        val preserved = """
            [Custom]
            Comment line without equals

            [interface]
            PrivateKey = old-private
            Address = 10.8.0.2/32
            DNS = 9.9.9.9
            MTU = 1400

            [peer]
            PublicKey = old-peer
            Endpoint = old.example.com:1234
            AllowedIPs = 0.0.0.0/0
        """.trimIndent()

        val merged = WarpIniBuilder.build(sampleConfig(), preserved)
        val passthrough = WarpIniBuilder.build(sampleConfig(), "")

        assertEquals(generated, passthrough)
        assertTrue(merged.contains("[Custom]"))
        assertTrue(merged.contains("Comment line without equals"))
        assertTrue(merged.contains("PrivateKey = private-key"))
        assertTrue(merged.contains("Endpoint = engage.cloudflareclient.com:2408"))
        assertTrue(merged.contains("AllowedIPs = 0.0.0.0/0"))
    }

    @Test
    fun `builder uses default headers when preserved ini omits known sections`() {
        val merged = WarpIniBuilder.build(
            sampleConfig(),
            """
            [Custom]
            Keep = value
            """.trimIndent(),
        )

        assertTrue(merged.contains("[Interface]"))
        assertTrue(merged.contains("[Peer]"))
        assertTrue(merged.contains("[Custom]"))
        assertTrue(merged.contains("Keep = value"))
    }

    @Test
    fun `builder keeps default headers when only one known section is preserved`() {
        val merged = WarpIniBuilder.build(
            sampleConfig(),
            """
            [Peer]
            Endpoint = old.example.com:1234
            AllowedIPs = 0.0.0.0/0
            """.trimIndent(),
        )

        assertTrue(merged.contains("[Interface]"))
        assertTrue(merged.contains("[Peer]"))
        assertTrue(merged.contains("Endpoint = engage.cloudflareclient.com:2408"))
    }

    @Test
    fun `builder keeps malformed and duplicate preserved lines`() {
        val merged = WarpIniBuilder.build(
            sampleConfig(),
            """
            [Interface]
            PrivateKey = old-private
            PrivateKey = duplicate-private
            Note without equals

            [Peer]
            Endpoint = old.example.com:1234
            Endpoint = duplicate.example.com:1234
            """.trimIndent(),
        )

        assertTrue(merged.contains("Note without equals"))
        assertTrue(merged.contains("PrivateKey = private-key"))
        assertTrue(merged.contains("Endpoint = engage.cloudflareclient.com:2408"))
        assertFalse(merged.contains("duplicate-private"))
        assertFalse(merged.contains("duplicate.example.com:1234"))
    }

    @Test
    fun `private warp ini helpers cover unknown labels and missing preferred sections`() {
        val mergerClass = Class.forName("ru.ozero.enginewarp.RawWarpIniMerger")
        val ctor = mergerClass.getDeclaredConstructor(String::class.java).apply { isAccessible = true }
        val merger = ctor.newInstance("")
        val canonicalLabel = mergerClass
            .getDeclaredMethod("canonicalLabel", String::class.java)
            .apply { isAccessible = true }
        val appendPreferredSection = mergerClass.getDeclaredMethod(
            "appendPreferredSection",
            StringBuilder::class.java,
            Map::class.java,
            MutableSet::class.java,
            String::class.java,
        ).apply { isAccessible = true }

        assertEquals("custom", canonicalLabel.invoke(merger, "custom"))
        appendPreferredSection.invoke(
            merger,
            StringBuilder(),
            emptyMap<String, Any>(),
            mutableSetOf<String>(),
            "missing",
        )
    }

    @Test
    fun `builder preserves unknown key labels and handles duplicate preferred sections`() {
        val merged = WarpIniBuilder.build(
            sampleConfig(),
            """
            [Interface]
            CustomKey = old
            PrivateKey = old-private

            [Interface]
            Other = ignored

            [Peer]
            Endpoint = old.example.com:1234
            custompeer = keep
            """.trimIndent(),
        )

        assertTrue(merged.contains("CustomKey = old"))
        assertTrue(merged.contains("custompeer = keep"))
        assertTrue(merged.contains("PrivateKey = private-key"))
        assertTrue(merged.contains("Endpoint = engage.cloudflareclient.com:2408"))
        assertTrue(merged.contains("Other = ignored"))
    }
}

class WarpConfigValidationTest {

    @Test
    fun `parser defaults allowed ips and dns for missing or blank peer values`() {
        val parsed = WarpConfParser.parse(
            """
            [Interface]
            PrivateKey = private-key
            Address = 10.0.0.2/32
            DNS =
            [Peer]
            PublicKey = peer-key
            Endpoint = endpoint:2408
            AllowedIPs = ,
            """.trimIndent(),
        ).getOrThrow()

        assertEquals(WarpConfig.DEFAULT_DNS, parsed.dnsServers)
        assertEquals(WarpConfig.DEFAULT_ALLOWED_IPS, parsed.allowedIps)
    }

    @Test
    fun `toWarpConfig falls back for invalid optional awg fields`() {
        val fallback = AwgParams(underloadPacketJunkSize = 9, payloadHexI1 = "0a0b")
        val config = draftFromSlot(slot(config = sampleConfig())).copy(
            jmin = "bad",
            jmax = "bad",
            s1 = "bad",
            s2 = "bad",
            h1 = "bad",
            h2 = "bad",
            h3 = "bad",
            h4 = "bad",
        ).toWarpConfig(fallback)

        assertEquals(AwgParams.DEFAULT_JMIN, config.awgParams.junkPacketMinSize)
        assertEquals(AwgParams.DEFAULT_JMAX, config.awgParams.junkPacketMaxSize)
        assertEquals(AwgParams.DEFAULT_S1, config.awgParams.initPacketJunkSize)
        assertEquals(AwgParams.DEFAULT_S2, config.awgParams.responsePacketJunkSize)
        assertEquals(AwgParams.DEFAULT_H1, config.awgParams.initPacketMagicHeader)
        assertEquals(AwgParams.DEFAULT_H2, config.awgParams.responsePacketMagicHeader)
        assertEquals(AwgParams.DEFAULT_H3, config.awgParams.cookieReplyMagicHeader)
        assertEquals(AwgParams.DEFAULT_H4, config.awgParams.transportMagicHeader)
        assertEquals(9, config.awgParams.underloadPacketJunkSize)
        assertEquals("0a0b", config.awgParams.payloadHexI1)
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
            AwgParams(junkPacketCount = 129)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AwgParams(initPacketJunkSize = 2000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AwgParams(junkPacketMinSize = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AwgParams(junkPacketMaxSize = 2000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AwgParams(responsePacketJunkSize = 2000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AwgParams(initPacketMagicHeader = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AwgParams(initPacketMagicHeader = 0x1_0000_0000L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AwgParams(initPacketMagicHeader = 1, responsePacketMagicHeader = 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AwgParams(transportMagicHeader = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AwgParams(cookieReplyMagicHeader = 1, transportMagicHeader = 1)
        }
        assertTrue(AwgParams.JC_RANGE.contains(AwgParams.DEFAULT_JC))
        assertTrue(AwgParams.SIZE_RANGE.contains(AwgParams.DEFAULT_JMIN))
        assertTrue(AwgParams.HEADER_RANGE.contains(AwgParams.DEFAULT_H1))
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
