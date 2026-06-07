package ru.ozero.corebackup

import org.json.JSONObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
}
