package ru.ozero.corebackup

import org.json.JSONObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("LargeClass")
class AppBackupSerializerTest {

    private val fullBackupSettings = BackupSettings(
        splitMode = "ALLOWLIST",
        ipv6Enabled = true,
        autoStart = false,
        manualEngine = "WARP",
        bydpiWinningArgs = "--fast",
        urnetworkEnabled = true,
        urnetworkJwt = "urn-jwt",
        customDnsServers = "1.1.1.1,8.8.8.8",
        hostsMode = "CUSTOM",
        hostsList = "example.com",
        uiLocaleTag = "ru-RU",
        appMode = "VPN",
        engineAutoPriority = "WARP,URNETWORK",
        trafficMode = "TUN",
        bydpiUseUiMode = true,
        bydpiUiSettingsJson = "{\"enabled\":true}",
        bydpiDefaultAccepted = false,
        urnetworkCountryCode = "DE",
        fptnToken = "token",
    )

    private val fullBackupUrnetwork = BackupUrnetwork(
        byJwt = "by-jwt",
        byClientJwt = "client-jwt",
        devicePubkey = "device-pub",
        deviceSeed = "device-seed",
        deviceNetworkName = "main",
        windowType = "QUALITY",
        fixedIpSize = true,
        allowDirect = false,
        provideEnabled = true,
        provideControlMode = "ALWAYS",
        provideNetworkMode = "PUBLIC",
        selectedLocation = BackupUrnetworkLocation(
            countryCode = "DE",
            region = "Bavaria",
            city = "Munich",
        ),
    )

    private val fullBackupWarpSlot = BackupWarpSlot(
        id = "slot-1",
        name = "Main",
        isActive = true,
        privateKey = "priv",
        publicKey = "pub",
        peerPublicKey = "peer-pub",
        peerEndpoint = "1.2.3.4:2408",
        interfaceAddressV4 = "172.16.0.2/32",
        interfaceAddressV6 = "2606:4700:110:8462::2/128",
        accountLicense = "license",
        mtu = 1280,
        dnsServers = listOf("1.1.1.1", "1.0.0.1"),
        keepaliveSeconds = 25,
        awgJc = 1,
        awgJmin = 2,
        awgJmax = 3,
        awgS1 = 4,
        awgS2 = 5,
        awgH1 = 6L,
        awgH2 = 7L,
        awgH3 = 8L,
        awgH4 = 9L,
        awgS3 = 10,
        awgS4 = 11,
        awgI1 = 12,
        awgI2 = 13,
        awgI3 = 14,
        awgI4 = 15,
        awgI5 = 16,
        awgI1Hex = "0c",
        awgI2Hex = "0d",
        awgI3Hex = "0e",
        awgI4Hex = "0f",
        awgI5Hex = "10",
    )

    private val fullBackupStrategy = BackupStrategy(
        settings = BackupStrategySettings(
            requestsPerDomain = 2,
            concurrentLimit = 3,
            timeoutSeconds = 4,
            delayBetweenMs = 500L,
            useCustomStrategies = true,
            customStrategies = "alpha",
            evolutionMode = true,
            evolutionPopulationSize = 5,
            evolutionMaxGenerations = 6,
            evolutionMutationRate = 0.7f,
            evolutionEliteCount = 1,
            evolutionTargetFitness = 0.9f,
        ),
        domainLists = listOf(
            BackupDomainList(
                id = "dl-1",
                name = "List",
                domains = listOf("one.example", "two.example"),
                isActive = true,
                isBuiltIn = false,
            ),
        ),
        savedStrategies = listOf(
            BackupSavedStrategy(
                id = "ss-1",
                command = "cmd",
                name = "Saved",
                isPinned = true,
            ),
        ),
    )

    private val fullBackupData = AppBackupData(
        version = AppBackupData.CURRENT_VERSION,
        exportedAt = "2026-06-05T10:11:12Z",
        settings = fullBackupSettings,
        urnetwork = fullBackupUrnetwork,
        warpSlots = listOf(fullBackupWarpSlot),
        splitRules = listOf(
            BackupSplitRule(packageName = "com.example.app", isExcluded = true),
        ),
        strategy = fullBackupStrategy,
    )

    private val minimalBackupData = AppBackupData(
        exportedAt = "2026-06-05T10:11:12Z",
        settings = BackupSettings(null, null, null, null, null, null, null, null, null, null, null, null),
        urnetwork = BackupUrnetwork(),
        warpSlots = emptyList(),
        splitRules = emptyList(),
    )

    private val categoryBackupData = AppBackupData(
        exportedAt = "2026-06-05T10:11:12Z",
        settings = BackupSettings(
            splitMode = "ALLOWLIST",
            ipv6Enabled = true,
            autoStart = null,
            manualEngine = null,
            bydpiWinningArgs = null,
            urnetworkEnabled = null,
            urnetworkJwt = null,
            customDnsServers = null,
            hostsMode = null,
            hostsList = null,
            uiLocaleTag = null,
            appMode = null,
        ),
        urnetwork = BackupUrnetwork(
            byClientJwt = "client-jwt",
        ),
        warpSlots = listOf(
            BackupWarpSlot(
                id = "slot",
                name = "Slot",
                isActive = true,
                privateKey = "priv",
                publicKey = "pub",
                peerPublicKey = "peer-pub",
                peerEndpoint = "1.2.3.4:2408",
                interfaceAddressV4 = "172.16.0.2/32",
                interfaceAddressV6 = "2606:4700:110:8462::2/128",
                accountLicense = "",
                mtu = 1280,
                dnsServers = listOf("1.1.1.1"),
                keepaliveSeconds = 25,
                awgJc = 0,
                awgJmin = 0,
                awgJmax = 0,
                awgS1 = 0,
                awgS2 = 0,
                awgH1 = 0L,
                awgH2 = 0L,
                awgH3 = 0L,
                awgH4 = 0L,
            ),
        ),
        splitRules = listOf(
            BackupSplitRule(packageName = "com.example", isExcluded = true),
        ),
    )

    @Test
    fun `serialize and deserialize preserve nested backup data`() {
        assertEquals(fullBackupData, AppBackupSerializer.deserialize(AppBackupSerializer.serialize(fullBackupData)))
    }

    @Test
    fun `deserializeAuto reads encrypted and plain backups`() {
        val encrypted = AppBackupSerializer.serializeEncrypted(minimalBackupData)
        val plain = AppBackupSerializer.serialize(minimalBackupData).toByteArray(Charsets.UTF_8)

        assertEquals(minimalBackupData, AppBackupSerializer.deserializeAuto(encrypted))
        assertEquals(minimalBackupData, AppBackupSerializer.deserializeAuto(plain))
    }

    @Test
    fun `backup categories reflect present backup content`() {
        assertTrue(BackupCategory.GENERAL_SETTINGS.isPresentIn(categoryBackupData))
        assertTrue(BackupCategory.URNETWORK.isPresentIn(categoryBackupData))
        assertTrue(BackupCategory.WARP.isPresentIn(categoryBackupData))
        assertTrue(BackupCategory.SPLIT_TUNNEL.isPresentIn(categoryBackupData))
        assertFalse(BackupCategory.DNS_HOSTS.isPresentIn(categoryBackupData))
        assertFalse(BackupCategory.BYEDPI.isPresentIn(categoryBackupData))
        assertFalse(BackupCategory.STRATEGY.isPresentIn(categoryBackupData))
        assertEquals(
            setOf(
                BackupCategory.GENERAL_SETTINGS,
                BackupCategory.URNETWORK,
                BackupCategory.WARP,
                BackupCategory.SPLIT_TUNNEL,
            ),
            BackupCategory.availableIn(categoryBackupData),
        )
    }

    @Test
    fun `backup categories classify split mode as split tunnel`() {
        val data = makeMinimalBackup().copy(
            settings = BackupSettings(
                splitMode = "ALLOWLIST",
                ipv6Enabled = null,
                autoStart = null,
                manualEngine = null,
                bydpiWinningArgs = null,
                urnetworkEnabled = null,
                urnetworkJwt = null,
                customDnsServers = null,
                hostsMode = null,
                hostsList = null,
                uiLocaleTag = null,
                appMode = null,
            ),
            splitRules = emptyList(),
        )

        assertFalse(BackupCategory.GENERAL_SETTINGS.isPresentIn(data))
        assertTrue(BackupCategory.SPLIT_TUNNEL.isPresentIn(data))
        assertEquals(setOf(BackupCategory.SPLIT_TUNNEL), BackupCategory.availableIn(data))
    }

    @Test
    fun `backup categories detect every single settings bucket`() {
        assertEquals(
            setOf(BackupCategory.GENERAL_SETTINGS),
            BackupCategory.availableIn(minimalBackupData.copy(settings = emptySettings(autoStart = true))),
        )
        assertEquals(
            setOf(BackupCategory.DNS_HOSTS),
            BackupCategory.availableIn(minimalBackupData.copy(settings = emptySettings(hostsList = "127.0.0.1 x"))),
        )
        assertEquals(
            setOf(BackupCategory.BYEDPI),
            BackupCategory.availableIn(minimalBackupData.copy(settings = emptySettings(bydpiDefaultAccepted = true))),
        )
        assertEquals(
            setOf(BackupCategory.URNETWORK),
            BackupCategory.availableIn(minimalBackupData.copy(settings = emptySettings(urnetworkCountryCode = "DE"))),
        )
        assertEquals(
            setOf(BackupCategory.STRATEGY),
            BackupCategory.availableIn(minimalBackupData.copy(strategy = BackupStrategy())),
        )
        assertEquals(
            emptySet(),
            BackupCategory.availableIn(minimalBackupData),
        )
    }

    @Test
    fun `backup categories detect urnetwork nested fields independently`() {
        assertEquals(
            setOf(BackupCategory.URNETWORK),
            BackupCategory.availableIn(minimalBackupData.copy(urnetwork = BackupUrnetwork(devicePubkey = "pub"))),
        )
        assertEquals(
            setOf(BackupCategory.URNETWORK),
            BackupCategory.availableIn(minimalBackupData.copy(urnetwork = BackupUrnetwork(fixedIpSize = false))),
        )
        assertEquals(
            setOf(BackupCategory.URNETWORK),
            BackupCategory.availableIn(
                minimalBackupData.copy(
                    urnetwork = BackupUrnetwork(selectedLocation = BackupUrnetworkLocation("DE", null, null)),
                ),
            ),
        )
    }

    @Test
    fun `warp serializer applies defaults for missing optional fields`() {
        val json = JSONObject()
            .put("id", "slot")
            .put("name", "Slot")
            .put("priv", "priv")
            .put("peerPub", "peer")
            .put("peerEndpoint", "1.2.3.4:2408")
            .put("ifaceV4", "172.16.0.2/32")
            .put("ifaceV6", "2606:4700:110::2/128")

        val slot = BackupWarpSerializer.fromJson(json)

        assertEquals(false, slot.isActive)
        assertEquals("", slot.publicKey)
        assertEquals("", slot.accountLicense)
        assertEquals(1280, slot.mtu)
        assertEquals(listOf("1.1.1.1", "1.0.0.1"), slot.dnsServers)
        assertEquals(25, slot.keepaliveSeconds)
        assertEquals(null, slot.awgS3)
        assertEquals(null, slot.awgI1Hex)
    }

    @Test
    fun `warp serializer preserves optional amnezia fields when present`() {
        val json = JSONObject()
            .put("id", "slot")
            .put("name", "Slot")
            .put("isActive", true)
            .put("priv", "priv")
            .put("pub", "pub")
            .put("peerPub", "peer")
            .put("peerEndpoint", "1.2.3.4:2408")
            .put("ifaceV4", "172.16.0.2/32")
            .put("ifaceV6", "2606:4700:110::2/128")
            .put("license", "license")
            .put("mtu", 1420)
            .put("keepalive", 30)
            .put("awgS3", 0)
            .put("awgS4", 4)
            .put("awgI1", 1)
            .put("awgI2", 2)
            .put("awgI3", 3)
            .put("awgI4", 4)
            .put("awgI5", 5)
            .put("awgI1Hex", "01")
            .put("awgI2Hex", "02")
            .put("awgI3Hex", "03")
            .put("awgI4Hex", "04")
            .put("awgI5Hex", "05")

        val slot = BackupWarpSerializer.fromJson(json)
        val serialized = BackupWarpSerializer.toJson(slot)

        assertEquals(true, slot.isActive)
        assertEquals(1420, slot.mtu)
        assertEquals(30, slot.keepaliveSeconds)
        assertEquals(0, slot.awgS3)
        assertEquals(4, slot.awgS4)
        assertEquals(1, slot.awgI1)
        assertEquals(5, slot.awgI5)
        assertEquals("01", slot.awgI1Hex)
        assertEquals("05", slot.awgI5Hex)
        assertTrue(serialized.has("awgS3"))
        assertTrue(serialized.has("awgI5Hex"))
    }

    @Test
    fun `warp serializer omits absent optional amnezia fields`() {
        val json = BackupWarpSerializer.toJson(
            fullBackupWarpSlot.copy(
                awgS3 = null,
                awgS4 = null,
                awgI1 = null,
                awgI2 = null,
                awgI3 = null,
                awgI4 = null,
                awgI5 = null,
                awgI1Hex = null,
                awgI2Hex = null,
                awgI3Hex = null,
                awgI4Hex = null,
                awgI5Hex = null,
            ),
        )

        assertFalse(json.has("awgS3"))
        assertFalse(json.has("awgS4"))
        assertFalse(json.has("awgI1"))
        assertFalse(json.has("awgI5Hex"))
    }

    @Test
    fun `strategy deserializer applies defaults for absent arrays and settings`() {
        val data = AppBackupSerializer.deserialize(
            """
            {
              "version": ${AppBackupData.CURRENT_VERSION},
              "strategy": {}
            }
            """.trimIndent(),
        )

        assertEquals(BackupStrategy(), data.strategy)
    }

    @Test
    fun `strategy deserializer keeps false booleans and blank optional names nullable`() {
        val data = AppBackupSerializer.deserialize(
            """
            {
              "version": ${AppBackupData.CURRENT_VERSION},
              "strategy": {
                "settings": {
                  "useCustomStrategies": false,
                  "customStrategies": "",
                  "evolutionMode": false,
                  "evolutionMutationRate": 0.25,
                  "evolutionTargetFitness": 0.75
                },
                "domainLists": [
                  {"id": "dl", "name": "Domains"}
                ],
                "savedStrategies": [
                  {"id": "saved", "command": "cmd", "name": ""}
                ]
              }
            }
            """.trimIndent(),
        )

        val strategy = requireNotNull(data.strategy)

        assertEquals(false, strategy.settings?.useCustomStrategies)
        assertEquals(null, strategy.settings?.customStrategies)
        assertEquals(false, strategy.settings?.evolutionMode)
        assertEquals(0.25f, strategy.settings?.evolutionMutationRate)
        assertEquals(0.75f, strategy.settings?.evolutionTargetFitness)
        assertEquals(
            listOf(
                BackupDomainList(
                    id = "dl",
                    name = "Domains",
                    isActive = true,
                    isBuiltIn = false,
                    domains = emptyList(),
                ),
            ),
            strategy.domainLists,
        )
        assertEquals(
            listOf(BackupSavedStrategy(id = "saved", command = "cmd", name = null, isPinned = false)),
            strategy.savedStrategies,
        )
    }

    @Test
    fun `urnetwork selected location empty object keeps nullable fields`() {
        val data = AppBackupSerializer.deserialize(
            """
            {
              "version": ${AppBackupData.CURRENT_VERSION},
              "urnetwork": {
                "selectedLocation": {}
              }
            }
            """.trimIndent(),
        )

        assertEquals(BackupUrnetworkLocation(), data.urnetwork.selectedLocation)
    }

    @Test
    fun `serialize writes selected location only with present nested fields`() {
        val json = JSONObject(
            AppBackupSerializer.serialize(
                minimalBackupData.copy(
                    urnetwork = BackupUrnetwork(
                        selectedLocation = BackupUrnetworkLocation(
                            countryCode = null,
                            region = "Bavaria",
                            city = "Munich",
                        ),
                    ),
                ),
            ),
        )
        val location = json.getJSONObject("urnetwork").getJSONObject("selectedLocation")

        assertFalse(location.has("countryCode"))
        assertEquals("Bavaria", location.getString("region"))
        assertEquals("Munich", location.getString("city"))
    }

    @Test
    fun `settings serializer preserves every nullable bucket when present`() {
        val json = BackupSettingsSerializer.serialize(fullBackupSettings)
        val restored = BackupSettingsSerializer.deserialize(json)

        assertEquals(fullBackupSettings, restored)
        assertEquals(false, restored.autoStart)
        assertEquals(false, restored.bydpiDefaultAccepted)
    }

    @Test
    fun `settings deserializer treats blank strings as absent and false booleans as present`() {
        val settings = BackupSettingsSerializer.deserialize(
            JSONObject()
                .put("splitMode", "")
                .put("manualEngine", "")
                .put("bydpiWinningArgs", "")
                .put("urnetworkJwt", "")
                .put("customDnsServers", "")
                .put("hostsMode", "")
                .put("hostsList", "")
                .put("uiLocaleTag", "")
                .put("appMode", "")
                .put("engineAutoPriority", "")
                .put("trafficMode", "")
                .put("bydpiUiSettingsJson", "")
                .put("urnetworkCountryCode", "")
                .put("fptnToken", "")
                .put("ipv6Enabled", false)
                .put("autoStart", false)
                .put("urnetworkEnabled", false)
                .put("bydpiUseUiMode", false)
                .put("bydpiDefaultAccepted", false),
        )

        assertEquals(
            BackupSettings(
                splitMode = null,
                ipv6Enabled = false,
                autoStart = false,
                manualEngine = null,
                bydpiWinningArgs = null,
                urnetworkEnabled = false,
                urnetworkJwt = null,
                customDnsServers = null,
                hostsMode = null,
                hostsList = null,
                uiLocaleTag = null,
                appMode = null,
                bydpiUseUiMode = false,
                bydpiDefaultAccepted = false,
            ),
            settings,
        )
    }

    @Test
    fun `strategy serializer writes nullable settings and optional saved strategy name`() {
        val json = BackupStrategySerializer.serialize(fullBackupStrategy)
        val restored = BackupStrategySerializer.deserialize(json)

        assertEquals(fullBackupStrategy, restored)
        assertTrue(json.getJSONArray("savedStrategies").getJSONObject(0).has("name"))
        assertEquals(500L, restored.settings?.delayBetweenMs)
        assertEquals("alpha", restored.settings?.customStrategies)
        assertEquals(listOf("one.example", "two.example"), restored.domainLists.single().domains)
    }

    @Test
    fun `strategy serializer omits absent settings and saved strategy names`() {
        val strategy = BackupStrategy(
            settings = BackupStrategySettings(),
            savedStrategies = listOf(BackupSavedStrategy(id = "saved", command = "cmd")),
        )
        val json = BackupStrategySerializer.serialize(strategy)
        val restored = BackupStrategySerializer.deserialize(json)

        assertEquals(strategy, restored)
        assertFalse(json.getJSONArray("savedStrategies").getJSONObject(0).has("name"))
        assertEquals(emptyList(), restored.domainLists)
    }

    @Test
    fun `base64 text handles padding whitespace and invalid input`() {
        assertEquals("", Base64Text.encode(byteArrayOf()))
        assertEquals("AQ==", Base64Text.encode(byteArrayOf(1)))
        assertEquals("AQI=", Base64Text.encode(byteArrayOf(1, 2)))
        assertEquals("AQID", Base64Text.encode(byteArrayOf(1, 2, 3)))
        assertEquals(listOf(1, 2), Base64Text.decode("AQ\nI=").map { it.toInt() })

        val badLength = assertFailsWith<IllegalArgumentException> { Base64Text.decode("A") }
        val badChar = assertFailsWith<IllegalArgumentException> { Base64Text.decode("????") }
        assertTrue(badLength.message.orEmpty().contains("bad base64 length"))
        assertTrue(badChar.message.orEmpty().contains("bad base64 char"))
    }

    @Test
    fun `json extension helpers keep optional primitives nullable`() {
        val json = JSONObject()
            .put("int", 7)
            .put("float", 1.5)
            .put("bool", true)

        assertEquals(7, json.intOrNull("int"))
        assertEquals(1.5f, json.floatOrNull("float"))
        assertEquals(true, json.booleanOrNull("bool"))
        assertEquals(false, JSONObject().put("bool", false).booleanOrNull("bool"))
        assertEquals(null, json.intOrNull("missing"))
        assertEquals(null, JSONObject().floatOrNull("missing"))
        assertEquals(null, JSONObject().booleanOrNull("missing"))
    }

    @Test
    fun `deserialize uses defaults when optional sections are absent`() {
        val data = AppBackupSerializer.deserialize("""{"version":${AppBackupData.CURRENT_VERSION}}""")

        assertEquals(AppBackupData.CURRENT_VERSION, data.version)
        assertEquals("", data.exportedAt)
        assertEquals(BackupUrnetwork(), data.urnetwork)
        assertEquals(emptyList(), data.warpSlots)
        assertEquals(emptyList(), data.splitRules)
        assertEquals(null, data.strategy)
    }

    @Test
    fun `deserialize split rule defaults isExcluded to false`() {
        val data = AppBackupSerializer.deserialize(
            """
            {
              "version": ${AppBackupData.CURRENT_VERSION},
              "splitRules": [
                {"packageName": "com.example.default"},
                {"packageName": "com.example.excluded", "isExcluded": true}
              ]
            }
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                BackupSplitRule("com.example.default", isExcluded = false),
                BackupSplitRule("com.example.excluded", isExcluded = true),
            ),
            data.splitRules,
        )
    }

    @Test
    fun `deserialize rejects unsupported versions and malformed json`() {
        val tooOld = assertFailsWith<AppBackupSerializer.BackupParseException> {
            AppBackupSerializer.deserialize("""{"version":0}""")
        }
        val tooNew = assertFailsWith<AppBackupSerializer.BackupParseException> {
            AppBackupSerializer.deserialize("""{"version":999}""")
        }
        val malformed = assertFailsWith<AppBackupSerializer.BackupParseException> {
            AppBackupSerializer.deserialize("{")
        }

        assertTrue(tooOld.message.orEmpty().contains("Unsupported backup version"))
        assertTrue(tooNew.message.orEmpty().contains("Unsupported backup version"))
        assertTrue(malformed.message.orEmpty().contains("Malformed backup JSON"))
    }

    @Test
    fun `deserializeAuto rejects oversized and unreadable payloads`() {
        val oversized = ByteArray(10 * 1024 * 1024 + 1)
        val encrypted = AppBackupSerializer.serializeEncrypted(minimalBackupData)
        encrypted[encrypted.lastIndex] = encrypted.last().inc()

        val tooLarge = assertFailsWith<AppBackupSerializer.BackupParseException> {
            AppBackupSerializer.deserializeAuto(oversized)
        }
        val unreadable = assertFailsWith<AppBackupSerializer.BackupParseException> {
            AppBackupSerializer.deserializeAuto(encrypted)
        }

        assertTrue(tooLarge.message.orEmpty().contains("Backup file too large"))
        assertTrue(unreadable.message.orEmpty().contains("Failed to read backup payload"))
    }

    private fun emptySettings(
        autoStart: Boolean? = null,
        hostsList: String? = null,
        bydpiDefaultAccepted: Boolean? = null,
        urnetworkCountryCode: String? = null,
    ) = BackupSettings(
        splitMode = null,
        ipv6Enabled = null,
        autoStart = autoStart,
        manualEngine = null,
        bydpiWinningArgs = null,
        urnetworkEnabled = null,
        urnetworkJwt = null,
        customDnsServers = null,
        hostsMode = null,
        hostsList = hostsList,
        uiLocaleTag = null,
        appMode = null,
        bydpiDefaultAccepted = bydpiDefaultAccepted,
        urnetworkCountryCode = urnetworkCountryCode,
    )
}
