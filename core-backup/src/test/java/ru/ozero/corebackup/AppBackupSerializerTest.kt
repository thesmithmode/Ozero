package ru.ozero.corebackup

import org.json.JSONObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppBackupSerializerTest {

    @Test
    fun `serialize and deserialize preserve nested backup data`() {
        val data = AppBackupData(
            version = AppBackupData.CURRENT_VERSION,
            exportedAt = "2026-06-05T10:11:12Z",
            settings = BackupSettings(
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
            ),
            urnetwork = BackupUrnetwork(
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
            ),
            warpSlots = listOf(
                BackupWarpSlot(
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
                ),
            ),
            splitRules = listOf(
                BackupSplitRule(packageName = "com.example.app", isExcluded = true),
            ),
            strategy = BackupStrategy(
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
            ),
        )

        val json = AppBackupSerializer.serialize(data)
        val restored = AppBackupSerializer.deserialize(json)

        assertEquals(data, restored)
    }

    @Test
    fun `deserializeAuto reads encrypted and plain backups`() {
        val data = AppBackupData(
            exportedAt = "2026-06-05T10:11:12Z",
            settings = BackupSettings(null, null, null, null, null, null, null, null, null, null, null, null),
            urnetwork = BackupUrnetwork(),
            warpSlots = emptyList(),
            splitRules = emptyList(),
        )
        val encrypted = AppBackupSerializer.serializeEncrypted(data)
        val plain = AppBackupSerializer.serialize(data).toByteArray(Charsets.UTF_8)

        assertEquals(data, AppBackupSerializer.deserializeAuto(encrypted))
        assertEquals(data, AppBackupSerializer.deserializeAuto(plain))
    }

    @Test
    fun `backup categories reflect present backup content`() {
        val data = AppBackupData(
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

        assertTrue(BackupCategory.GENERAL_SETTINGS.isPresentIn(data))
        assertTrue(BackupCategory.URNETWORK.isPresentIn(data))
        assertTrue(BackupCategory.WARP.isPresentIn(data))
        assertTrue(BackupCategory.SPLIT_TUNNEL.isPresentIn(data))
        assertFalse(BackupCategory.DNS_HOSTS.isPresentIn(data))
        assertFalse(BackupCategory.BYEDPI.isPresentIn(data))
        assertFalse(BackupCategory.STRATEGY.isPresentIn(data))
        assertEquals(
            setOf(
                BackupCategory.GENERAL_SETTINGS,
                BackupCategory.URNETWORK,
                BackupCategory.WARP,
                BackupCategory.SPLIT_TUNNEL,
            ),
            BackupCategory.availableIn(data),
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
        assertEquals(null, json.intOrNull("missing"))
    }
}
