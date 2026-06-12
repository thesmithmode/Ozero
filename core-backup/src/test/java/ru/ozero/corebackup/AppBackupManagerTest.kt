package ru.ozero.corebackup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.corestorage.dao.AppSplitRuleDao
import ru.ozero.corestorage.entity.AppSplitRule
import ru.ozero.enginescore.settings.SettingsKeys
import ru.ozero.enginefptn.FptnConfig
import ru.ozero.enginefptn.FptnConfigStore
import ru.ozero.engineurnetwork.UrnetworkConfigStore
import ru.ozero.engineurnetwork.UrnetworkLocationSelection
import ru.ozero.engineurnetwork.UrnetworkProvideControlMode
import ru.ozero.engineurnetwork.UrnetworkProvideNetworkMode
import ru.ozero.engineurnetwork.UrnetworkWindowType
import ru.ozero.engineurnetwork.auth.InMemoryUrnetworkDeviceIdentity
import ru.ozero.enginewarp.AwgParams
import ru.ozero.enginewarp.WarpConfig
import ru.ozero.enginewarp.WarpConfigSlot
import ru.ozero.enginewarp.WarpConfigSlotStore
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Suppress("LargeClass")
class AppBackupManagerTest {

    private val ozeroDs = FakePreferencesDataStore()
    private val warpStore = FakeWarpSlotStore()
    private val urnStore = FakeUrnetworkStore()
    private val fptnStore = FakeFptnStore()
    private val splitDao = FakeSplitRuleDao()
    private val urnIdentity = InMemoryUrnetworkDeviceIdentity(ByteArray(32) { (it + 1).toByte() })

    private val manager = AppBackupManager(
        ozeroDs,
        warpStore,
        urnStore,
        splitDao,
        fptnStore,
        urnetworkDeviceIdentity = urnIdentity,
    )

    private val sampleWarpSlot = WarpConfigSlot(
        id = "warp-id-1",
        name = "Main",
        isActive = true,
        config = WarpConfig(
            privateKey = "privKey",
            publicKey = "pubKey",
            peerPublicKey = "peerPub",
            peerEndpoint = "engage.cloudflareclient.com:2408",
            interfaceAddressV4 = "172.16.0.2/32",
            interfaceAddressV6 = "2606::1/128",
            accountLicense = "lic",
            mtu = 1280,
            dnsServers = listOf("1.1.1.1"),
            keepaliveSeconds = 25,
            awgParams = AwgParams(
                junkPacketCount = 5,
                junkPacketMinSize = 100,
                junkPacketMaxSize = 200,
                initPacketJunkSize = 0,
                responsePacketJunkSize = 0,
                initPacketMagicHeader = 1L,
                responsePacketMagicHeader = 2L,
                cookieReplyMagicHeader = 3L,
                transportMagicHeader = 4L,
            ),
        ),
    )

    @Test
    fun `export — настройки из DataStore попадают в backup`() = runTest {
        ozeroDs.edit { prefs ->
            prefs[SettingsKeys.SPLIT_MODE] = "per_app"
            prefs[SettingsKeys.IPV6_ENABLED] = true
            prefs[SettingsKeys.MANUAL_ENGINE] = "byedpi"
            prefs[SettingsKeys.UI_LOCALE_TAG] = "ru"
            prefs[SettingsKeys.ENGINE_AUTO_PRIORITY] = "warp,byedpi,urnetwork"
            prefs[SettingsKeys.TRAFFIC_MODE] = "PROXY"
            prefs[SettingsKeys.BYDPI_USE_UI_MODE] = true
            prefs[SettingsKeys.BYDPI_UI_SETTINGS_JSON] = """{"a":1}"""
            prefs[SettingsKeys.URNETWORK_COUNTRY_CODE] = "DE"
        }

        val data = manager.export()
        assertEquals("per_app", data.settings.splitMode)
        assertEquals(true, data.settings.ipv6Enabled)
        assertEquals("byedpi", data.settings.manualEngine)
        assertEquals("ru", data.settings.uiLocaleTag)
        assertEquals("warp,byedpi,urnetwork", data.settings.engineAutoPriority)
        assertEquals("PROXY", data.settings.trafficMode)
        assertEquals(true, data.settings.bydpiUseUiMode)
        assertEquals("""{"a":1}""", data.settings.bydpiUiSettingsJson)
        assertEquals("DE", data.settings.urnetworkCountryCode)
        assertNull(data.settings.fptnToken)
    }

    @Test
    fun `export and import — FPTN token сохраняется в backup`() = runTest {
        fptnStore.inject { it.copy(token = "fptn:secret-token") }
        val exported = manager.export()
        assertEquals("fptn:secret-token", exported.settings.fptnToken)

        val importedStore = FakeFptnStore()
        val importedManager = AppBackupManager(ozeroDs, warpStore, urnStore, splitDao, importedStore)
        importedManager.import(exported)
        assertEquals("fptn:secret-token", importedStore.currentConfig().token)
    }

    @Test
    fun `export — WARP слоты конвертируются корректно`() = runTest {
        warpStore.slots.value = listOf(sampleWarpSlot)
        val data = manager.export()
        assertEquals(1, data.warpSlots.size)
        assertEquals("warp-id-1", data.warpSlots[0].id)
        assertEquals(5, data.warpSlots[0].awgJc)
    }

    @Test
    fun `export — URnetwork config все поля`() = runTest {
        urnStore.inject {
            it.copy(
                byJwt = "jwt-token",
                byClientJwt = "client-jwt",
                devicePubkey = "pub-key-1",
                deviceNetworkName = "device-name-1",
                windowType = UrnetworkWindowType.QUALITY,
                fixedIpSize = true,
                allowDirect = false,
                provideEnabled = false,
                provideControlMode = UrnetworkProvideControlMode.AUTO,
                provideNetworkMode = UrnetworkProvideNetworkMode.ALL,
                selectedLocation = UrnetworkLocationSelection("DE", "Bavaria", "Munich"),
            )
        }
        val data = manager.export()
        assertEquals("jwt-token", data.urnetwork.byJwt)
        assertEquals("client-jwt", data.urnetwork.byClientJwt)
        assertEquals("pub-key-1", data.urnetwork.devicePubkey)
        assertEquals(44, data.urnetwork.deviceSeed?.length)
        assertEquals("device-name-1", data.urnetwork.deviceNetworkName)
        assertEquals("quality", data.urnetwork.windowType)
        assertEquals(true, data.urnetwork.fixedIpSize)
        assertEquals(false, data.urnetwork.allowDirect)
        assertEquals(false, data.urnetwork.provideEnabled)
        assertEquals("auto", data.urnetwork.provideControlMode)
        assertEquals("all", data.urnetwork.provideNetworkMode)
        assertEquals("DE", data.urnetwork.selectedLocation?.countryCode)
        assertEquals("Bavaria", data.urnetwork.selectedLocation?.region)
        assertEquals("Munich", data.urnetwork.selectedLocation?.city)
    }

    @Test
    fun `export — split rules из DAO`() = runTest {
        splitDao.rules.value = listOf(AppSplitRule("com.a", true), AppSplitRule("com.b", false))
        val data = manager.export()
        assertEquals(2, data.splitRules.size)
        assertEquals("com.a", data.splitRules[0].packageName)
    }

    @Test
    fun `export categories — только WARP — остальные пустые`() = runTest {
        ozeroDs.edit { it[SettingsKeys.SPLIT_MODE] = "per_app" }
        warpStore.slots.value = listOf(sampleWarpSlot)
        urnStore.inject { it.copy(byJwt = "jwt") }
        splitDao.rules.value = listOf(AppSplitRule("com.x", true))

        val data = manager.export(setOf(BackupCategory.WARP))

        assertEquals(1, data.warpSlots.size)
        assertNull(data.settings.splitMode)
        assertNull(data.urnetwork.byJwt)
        assertTrue(data.splitRules.isEmpty())
        assertNull(data.strategy)
    }

    @Test
    fun `export categories preserve dns byedpi and general settings independently`() = runTest {
        ozeroDs.edit { prefs ->
            prefs[SettingsKeys.SPLIT_MODE] = "per_app"
            prefs[SettingsKeys.IPV6_ENABLED] = false
            prefs[SettingsKeys.AUTO_START] = true
            prefs[SettingsKeys.MANUAL_ENGINE] = "warp"
            prefs[SettingsKeys.APP_MODE] = "VPN"
            prefs[SettingsKeys.BYDPI_WINNING_ARGS] = "--split"
            prefs[SettingsKeys.BYDPI_USE_UI_MODE] = false
            prefs[SettingsKeys.BYDPI_UI_SETTINGS_JSON] = "{}"
            prefs[SettingsKeys.BYDPI_DEFAULT_ACCEPTED] = true
            prefs[SettingsKeys.CUSTOM_DNS_SERVERS] = "9.9.9.9"
            prefs[SettingsKeys.HOSTS_MODE] = "CUSTOM"
            prefs[SettingsKeys.HOSTS_LIST] = "127.0.0.1 example.test"
            prefs[SettingsKeys.URNETWORK_ENABLED] = true
            prefs[SettingsKeys.URNETWORK_JWT] = "legacy-jwt"
            prefs[SettingsKeys.URNETWORK_COUNTRY_CODE] = "PL"
        }
        fptnStore.inject { it.copy(token = " ") }

        val data = manager.export(
            setOf(
                BackupCategory.GENERAL_SETTINGS,
                BackupCategory.DNS_HOSTS,
                BackupCategory.BYEDPI,
            ),
        )

        assertEquals("per_app", data.settings.splitMode)
        assertEquals(false, data.settings.ipv6Enabled)
        assertEquals(true, data.settings.autoStart)
        assertEquals("warp", data.settings.manualEngine)
        assertEquals("VPN", data.settings.appMode)
        assertEquals("--split", data.settings.bydpiWinningArgs)
        assertEquals(false, data.settings.bydpiUseUiMode)
        assertEquals("{}", data.settings.bydpiUiSettingsJson)
        assertEquals(true, data.settings.bydpiDefaultAccepted)
        assertEquals("9.9.9.9", data.settings.customDnsServers)
        assertEquals("CUSTOM", data.settings.hostsMode)
        assertEquals("127.0.0.1 example.test", data.settings.hostsList)
        assertNull(data.settings.urnetworkEnabled)
        assertNull(data.settings.urnetworkJwt)
        assertNull(data.settings.urnetworkCountryCode)
        assertNull(data.settings.fptnToken)
        assertEquals(BackupUrnetwork(), data.urnetwork)
    }

    @Test
    fun `import — настройки записываются в DataStore`() = runTest {
        val data = makeMinimalBackup().copy(
            settings = BackupSettings(
                splitMode = "global", ipv6Enabled = false, autoStart = true,
                manualEngine = "warp", bydpiWinningArgs = "--test",
                urnetworkEnabled = null, urnetworkJwt = null,
                customDnsServers = null, hostsMode = null, hostsList = null,
                uiLocaleTag = "en", appMode = null,
                engineAutoPriority = "urnetwork,warp,byedpi",
                trafficMode = "PROXY",
                bydpiUseUiMode = true, bydpiUiSettingsJson = """{"k":"v"}""",
                bydpiDefaultAccepted = true, urnetworkCountryCode = "FR",
            ),
        )
        manager.import(data)

        val prefs = ozeroDs.data.first()
        assertEquals("global", prefs[SettingsKeys.SPLIT_MODE])
        assertEquals(false, prefs[SettingsKeys.IPV6_ENABLED])
        assertEquals(true, prefs[SettingsKeys.AUTO_START])
        assertEquals("warp", prefs[SettingsKeys.MANUAL_ENGINE])
        assertEquals("en", prefs[SettingsKeys.UI_LOCALE_TAG])
        assertEquals("urnetwork,warp,byedpi", prefs[SettingsKeys.ENGINE_AUTO_PRIORITY])
        assertEquals("PROXY", prefs[SettingsKeys.TRAFFIC_MODE])
        assertEquals(true, prefs[SettingsKeys.BYDPI_USE_UI_MODE])
        assertEquals("""{"k":"v"}""", prefs[SettingsKeys.BYDPI_UI_SETTINGS_JSON])
        assertEquals(true, prefs[SettingsKeys.BYDPI_DEFAULT_ACCEPTED])
        assertEquals("FR", prefs[SettingsKeys.URNETWORK_COUNTRY_CODE])
    }

    @Test
    fun `import — null настройки не перезаписывают DataStore`() = runTest {
        ozeroDs.edit { it[SettingsKeys.SPLIT_MODE] = "existing" }
        manager.import(makeMinimalBackup())
        assertEquals("existing", ozeroDs.data.first()[SettingsKeys.SPLIT_MODE])
    }

    @Test
    fun `import — Urnetwork prefs восстанавливаются`() = runTest {
        val importedIdentity = InMemoryUrnetworkDeviceIdentity(ByteArray(32) { 9 })
        val importingManager = AppBackupManager(
            ozeroDs,
            warpStore,
            urnStore,
            splitDao,
            fptnStore,
            urnetworkDeviceIdentity = importedIdentity,
        )
        val data = makeMinimalBackup().copy(
            urnetwork = BackupUrnetwork(
                byJwt = "imported-jwt",
                byClientJwt = "imported-client-jwt",
                devicePubkey = "imported-pub",
                deviceSeed = manager.export().urnetwork.deviceSeed,
                deviceNetworkName = "imported-device",
                windowType = "speed",
                fixedIpSize = true,
                allowDirect = false,
                provideEnabled = false,
                provideControlMode = "auto",
                provideNetworkMode = "all",
                selectedLocation = BackupUrnetworkLocation("ES", "Madrid", "Madrid"),
            ),
        )
        importingManager.import(data)

        val cfg = urnStore.config().first()
        assertEquals("imported-jwt", cfg.byJwt)
        assertEquals("imported-client-jwt", cfg.byClientJwt)
        assertEquals("imported-pub", cfg.devicePubkey)
        assertEquals("imported-device", cfg.deviceNetworkName)
        assertEquals(UrnetworkWindowType.SPEED, cfg.windowType)
        assertEquals(true, cfg.fixedIpSize)
        assertEquals(false, cfg.allowDirect)
        assertEquals(false, cfg.provideEnabled)
        assertEquals(UrnetworkProvideControlMode.AUTO, cfg.provideControlMode)
        assertEquals(UrnetworkProvideNetworkMode.ALL, cfg.provideNetworkMode)
        assertEquals("ES", cfg.selectedLocation.countryCode)
        assertEquals("Madrid", cfg.selectedLocation.city)
        assertEquals(urnIdentity.pubkeyBase58(), importedIdentity.pubkeyBase58())
    }

    @Test
    fun `import categories — только URNETWORK — WARP не трогает`() = runTest {
        warpStore.slots.value = listOf(sampleWarpSlot)
        val data = makeMinimalBackup().copy(
            warpSlots = emptyList(),
            urnetwork = BackupUrnetwork(byJwt = "new-jwt"),
        )
        manager.import(data, setOf(BackupCategory.URNETWORK))

        assertEquals(listOf(sampleWarpSlot), warpStore.slots.value)
        assertEquals("new-jwt", urnStore.config().first().byJwt)
    }

    @Test
    fun `import categories restore only dns and byedpi preferences when requested`() = runTest {
        ozeroDs.edit { prefs ->
            prefs[SettingsKeys.SPLIT_MODE] = "existing"
            prefs[SettingsKeys.CUSTOM_DNS_SERVERS] = "1.1.1.1"
            prefs[SettingsKeys.BYDPI_WINNING_ARGS] = "--old"
        }
        val data = makeMinimalBackup().copy(
            settings = BackupSettings(
                splitMode = "new-general",
                ipv6Enabled = true,
                autoStart = true,
                manualEngine = "warp",
                bydpiWinningArgs = "--new",
                urnetworkEnabled = true,
                urnetworkJwt = "urn",
                customDnsServers = "9.9.9.9",
                hostsMode = "CUSTOM",
                hostsList = "127.0.0.1 example.test",
                uiLocaleTag = "en",
                appMode = "VPN",
                bydpiUseUiMode = false,
                bydpiUiSettingsJson = "{}",
                bydpiDefaultAccepted = true,
                urnetworkCountryCode = "DE",
            ),
        )

        manager.import(data, setOf(BackupCategory.DNS_HOSTS, BackupCategory.BYEDPI))
        val prefs = ozeroDs.data.first()

        assertEquals("existing", prefs[SettingsKeys.SPLIT_MODE])
        assertEquals("9.9.9.9", prefs[SettingsKeys.CUSTOM_DNS_SERVERS])
        assertEquals("CUSTOM", prefs[SettingsKeys.HOSTS_MODE])
        assertEquals("127.0.0.1 example.test", prefs[SettingsKeys.HOSTS_LIST])
        assertEquals("--new", prefs[SettingsKeys.BYDPI_WINNING_ARGS])
        assertEquals(false, prefs[SettingsKeys.BYDPI_USE_UI_MODE])
        assertEquals("{}", prefs[SettingsKeys.BYDPI_UI_SETTINGS_JSON])
        assertEquals(true, prefs[SettingsKeys.BYDPI_DEFAULT_ACCEPTED])
        assertNull(prefs[SettingsKeys.URNETWORK_ENABLED])
    }

    @Test
    fun `import general settings restores fptn token only when category is selected`() = runTest {
        val data = makeMinimalBackup().copy(
            settings = BackupSettings(
                splitMode = "global",
                ipv6Enabled = false,
                autoStart = true,
                manualEngine = "fptn",
                bydpiWinningArgs = "--ignored",
                urnetworkEnabled = true,
                urnetworkJwt = "ignored",
                customDnsServers = "ignored",
                hostsMode = "ignored",
                hostsList = "ignored",
                uiLocaleTag = "en",
                appMode = "VPN",
                fptnToken = "persisted-token",
            ),
        )

        manager.import(data, setOf(BackupCategory.DNS_HOSTS))
        assertEquals("", fptnStore.currentConfig().token)

        manager.import(data, setOf(BackupCategory.GENERAL_SETTINGS))

        val prefs = ozeroDs.data.first()
        assertEquals("global", prefs[SettingsKeys.SPLIT_MODE])
        assertEquals("fptn", prefs[SettingsKeys.MANUAL_ENGINE])
        assertEquals("persisted-token", fptnStore.currentConfig().token)
        assertNull(prefs[SettingsKeys.CUSTOM_DNS_SERVERS])
        assertNull(prefs[SettingsKeys.URNETWORK_ENABLED])
    }

    @Test
    fun `import general settings ignores blank fptn token`() = runTest {
        fptnStore.inject { it.copy(token = "existing-token") }
        manager.import(
            makeMinimalBackup().copy(
                settings = BackupSettings(
                    splitMode = null,
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
                    fptnToken = " ",
                ),
            ),
        )

        assertEquals("existing-token", fptnStore.currentConfig().token)
    }

    @Test
    fun `import urnetwork ignores invalid seed and preserves current nullable fields`() = runTest {
        urnStore.inject {
            it.copy(
                byJwt = "old-jwt",
                windowType = UrnetworkWindowType.QUALITY,
                fixedIpSize = true,
                allowDirect = false,
                provideEnabled = false,
                provideControlMode = UrnetworkProvideControlMode.ALWAYS,
                provideNetworkMode = UrnetworkProvideNetworkMode.WIFI,
                selectedLocation = UrnetworkLocationSelection("US", "CA", "LA"),
            )
        }
        val beforeSeed = urnIdentity.pubkeyBase58()

        manager.import(
            makeMinimalBackup().copy(
                urnetwork = BackupUrnetwork(
                    deviceSeed = "bad",
                    selectedLocation = BackupUrnetworkLocation(null, "Region", "City"),
                ),
            ),
            setOf(BackupCategory.URNETWORK),
        )

        val cfg = urnStore.config().first()
        assertEquals("old-jwt", cfg.byJwt)
        assertEquals(UrnetworkWindowType.QUALITY, cfg.windowType)
        assertEquals(true, cfg.fixedIpSize)
        assertEquals(false, cfg.allowDirect)
        assertEquals(false, cfg.provideEnabled)
        assertEquals(UrnetworkProvideControlMode.ALWAYS, cfg.provideControlMode)
        assertEquals(UrnetworkProvideNetworkMode.WIFI, cfg.provideNetworkMode)
        assertEquals(null, cfg.selectedLocation.countryCode)
        assertEquals("Region", cfg.selectedLocation.region)
        assertEquals("City", cfg.selectedLocation.city)
        assertEquals(beforeSeed, urnIdentity.pubkeyBase58())
    }

    @Test
    fun `import urnetwork applies raw enum values and decoded seed`() = runTest {
        val importedIdentity = InMemoryUrnetworkDeviceIdentity(ByteArray(32) { 0 })
        val importingManager = AppBackupManager(
            ozeroDs,
            warpStore,
            urnStore,
            splitDao,
            fptnStore,
            urnetworkDeviceIdentity = importedIdentity,
        )

        importingManager.import(
            makeMinimalBackup().copy(
                urnetwork = BackupUrnetwork(
                    deviceSeed = manager.export().urnetwork.deviceSeed,
                    windowType = "speed",
                    fixedIpSize = false,
                    allowDirect = true,
                    provideEnabled = true,
                    provideControlMode = "always",
                    provideNetworkMode = "public",
                ),
            ),
            setOf(BackupCategory.URNETWORK),
        )

        val cfg = urnStore.config().first()
        assertEquals(UrnetworkWindowType.SPEED, cfg.windowType)
        assertEquals(false, cfg.fixedIpSize)
        assertEquals(true, cfg.allowDirect)
        assertEquals(true, cfg.provideEnabled)
        assertEquals(UrnetworkProvideControlMode.ALWAYS, cfg.provideControlMode)
        assertEquals(UrnetworkProvideNetworkMode.WIFI, cfg.provideNetworkMode)
        assertEquals(urnIdentity.pubkeyBase58(), importedIdentity.pubkeyBase58())
    }

    @Test
    fun `import — WARP пустой — replaceAll не вызывается`() = runTest {
        warpStore.slots.value = listOf(sampleWarpSlot)
        manager.import(makeMinimalBackup())
        assertEquals(listOf(sampleWarpSlot), warpStore.slots.value)
    }

    @Test
    fun `import warp restores optional amnezia values when present`() = runTest {
        val slot = BackupWarpSlot(
            id = "slot-id",
            name = "Renamed",
            isActive = false,
            privateKey = "priv",
            publicKey = "pub",
            peerPublicKey = "peer",
            peerEndpoint = "1.2.3.4:2408",
            interfaceAddressV4 = "172.16.0.2/32",
            interfaceAddressV6 = "2606:4700:110::2/128",
            accountLicense = "license",
            mtu = 1400,
            dnsServers = listOf("9.9.9.9"),
            keepaliveSeconds = 30,
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

        manager.import(makeMinimalBackup().copy(warpSlots = listOf(slot)), setOf(BackupCategory.WARP))

        val restored = warpStore.slots.value.single()
        assertEquals("slot-id", restored.id)
        assertEquals("Renamed", restored.name)
        assertEquals(false, restored.isActive)
        assertEquals(1400, restored.config.mtu)
        assertEquals(listOf("9.9.9.9"), restored.config.dnsServers)
        assertEquals(10, restored.config.awgParams.underloadPacketJunkSize)
        assertEquals(16, restored.config.awgParams.payloadPacketSizeCount3)
        assertEquals("0c", restored.config.awgParams.payloadHexI1)
        assertEquals("10", restored.config.awgParams.payloadHexI5)
    }

    @Test
    fun `import warp uses awg defaults when optional amnezia values are absent`() = runTest {
        manager.import(
            makeMinimalBackup().copy(
                warpSlots = listOf(
                    BackupWarpSlot(
                        id = "slot-id",
                        name = "Defaulted",
                        isActive = true,
                        privateKey = "priv",
                        publicKey = "pub",
                        peerPublicKey = "peer",
                        peerEndpoint = "1.2.3.4:2408",
                        interfaceAddressV4 = "172.16.0.2/32",
                        interfaceAddressV6 = "2606:4700:110::2/128",
                        accountLicense = "",
                        mtu = 1280,
                        dnsServers = emptyList(),
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
                    ),
                ),
            ),
            setOf(BackupCategory.WARP),
        )

        val awg = warpStore.slots.value.single().config.awgParams
        assertEquals(AwgParams.DEFAULT_S3, awg.underloadPacketJunkSize)
        assertEquals(AwgParams.DEFAULT_S4, awg.payloadPacketJunkSize)
        assertEquals(AwgParams.DEFAULT_I1, awg.payloadPacketSizeCount1)
        assertEquals(AwgParams.DEFAULT_I2, awg.payloadPacketSizeCount2)
        assertEquals(AwgParams.DEFAULT_I3, awg.specialJunk3)
        assertEquals(AwgParams.DEFAULT_I4, awg.specialJunk4)
        assertEquals(AwgParams.DEFAULT_I5, awg.payloadPacketSizeCount3)
        assertNull(awg.payloadHexI1)
        assertNull(awg.payloadHexI5)
    }

    @Test
    fun `import — split rules — удаляет отсутствующие, добавляет новые`() = runTest {
        splitDao.rules.value = listOf(AppSplitRule("com.old", true), AppSplitRule("com.keep", false))
        val data = makeMinimalBackup().copy(
            splitRules = listOf(BackupSplitRule("com.keep", false), BackupSplitRule("com.new", true)),
        )
        manager.import(data)
        val packages = splitDao.rules.value.map { it.packageName }
        assertFalse("com.old" in packages)
        assertTrue("com.keep" in packages)
        assertTrue("com.new" in packages)
    }

    @Test
    fun `export then import — полный roundtrip с категориями ALL`() = runTest {
        ozeroDs.edit { prefs ->
            prefs[SettingsKeys.SPLIT_MODE] = "per_app"
            prefs[SettingsKeys.IPV6_ENABLED] = true
            prefs[SettingsKeys.ENGINE_AUTO_PRIORITY] = "warp"
            prefs[SettingsKeys.TRAFFIC_MODE] = "PROXY"
        }
        warpStore.slots.value = listOf(sampleWarpSlot)
        urnStore.inject { it.copy(byJwt = "jwt", selectedLocation = UrnetworkLocationSelection("RU", null, null)) }
        splitDao.rules.value = listOf(AppSplitRule("com.example", true))

        val exported = manager.export()
        val jsonString = AppBackupSerializer.serialize(exported)

        val freshDs = FakePreferencesDataStore()
        val freshWarp = FakeWarpSlotStore()
        val freshUrn = FakeUrnetworkStore()
        val freshDao = FakeSplitRuleDao()
        val freshFptn = FakeFptnStore()
        val freshManager = AppBackupManager(freshDs, freshWarp, freshUrn, freshDao, freshFptn)

        freshManager.import(AppBackupSerializer.deserialize(jsonString))

        assertEquals("per_app", freshDs.data.first()[SettingsKeys.SPLIT_MODE])
        assertEquals(true, freshDs.data.first()[SettingsKeys.IPV6_ENABLED])
        assertEquals("warp", freshDs.data.first()[SettingsKeys.ENGINE_AUTO_PRIORITY])
        assertEquals("PROXY", freshDs.data.first()[SettingsKeys.TRAFFIC_MODE])
        assertEquals(1, freshWarp.slots.value.size)
        assertEquals("jwt", freshUrn.config().first().byJwt)
        assertEquals("RU", freshUrn.config().first().selectedLocation.countryCode)
        assertEquals(1, freshDao.rules.value.size)
    }

    @Test
    fun `export with provider — strategy block в backup`() = runTest {
        val provider = FakeStrategyBackupProvider()
        provider.exported = BackupStrategy(
            settings = BackupStrategySettings(requestsPerDomain = 3, evolutionTargetFitness = 0.95f),
        )
        val mgr = AppBackupManager(
            ozeroDs,
            warpStore,
            urnStore,
            splitDao,
            fptnStore = fptnStore,
            strategyProvider = provider,
        )
        val data = mgr.export()
        assertEquals(provider.exported, data.strategy)
    }

    @Test
    fun `import strategy — provider получает данные`() = runTest {
        val provider = FakeStrategyBackupProvider()
        val mgr = AppBackupManager(
            ozeroDs,
            warpStore,
            urnStore,
            splitDao,
            fptnStore = fptnStore,
            strategyProvider = provider,
        )
        val strategy = BackupStrategy(settings = BackupStrategySettings(evolutionTargetFitness = 0.88f))
        mgr.import(makeMinimalBackup().copy(strategy = strategy))
        assertEquals(strategy, provider.imported)
    }

    @Test
    fun `import strategy null — provider не вызывается`() = runTest {
        val provider = FakeStrategyBackupProvider()
        val mgr = AppBackupManager(
            ozeroDs,
            warpStore,
            urnStore,
            splitDao,
            fptnStore = fptnStore,
            strategyProvider = provider,
        )
        mgr.import(makeMinimalBackup())
        assertNull(provider.imported)
    }

    @Test
    fun `v1 backup import — strategy null в v1 формате`() = runTest {
        val v1Json = """{"version":1,"exportedAt":"2025-01-01T00:00:00Z",""" +
            """"settings":{},"urnetwork":{},"warpSlots":[],"splitRules":[]}"""
        val data = AppBackupSerializer.deserialize(v1Json)
        assertEquals(1, data.version)
        assertNull(data.strategy)
    }

    @Test
    fun `export — killswitchEnabled НЕ попадает в backup (намеренно per-device)`() = runTest {
        ozeroDs.edit { it[SettingsKeys.KILLSWITCH_ENABLED] = true }
        val data = manager.export()
        val json = AppBackupSerializer.serialize(data)
        assertFalse(
            json.contains("killswitch", ignoreCase = true),
            "killswitchEnabled — per-device kill switch, намеренно не сериализуется в backup " +
                "(project_killswitch_no_backup). Регрессия = restore включает kill switch на другом устройстве",
        )
    }

    @Test
    fun `import — killswitchEnabled в DataStore не перетирается backup'ом`() = runTest {
        ozeroDs.edit { it[SettingsKeys.KILLSWITCH_ENABLED] = true }
        manager.import(makeMinimalBackup())
        assertEquals(
            true,
            ozeroDs.data.first()[SettingsKeys.KILLSWITCH_ENABLED],
            "import не должен трогать KILLSWITCH_ENABLED — он намеренно вне backup-контракта",
        )
    }

    @Test
    fun `BackupCategory ALL покрывает все enum значения — sentinel против забытой категории`() {
        assertEquals(
            BackupCategory.values().toSet(),
            BackupCategory.ALL,
            "BackupCategory.ALL обязан содержать все enum значения — иначе новая категория " +
                "не попадёт в default export/import и приведёт к молчаливой потере настроек",
        )
    }

    @Test
    fun `v2 backup — walletOverride игнорируется при чтении`() = runTest {
        val v2Json = """{"version":2,"exportedAt":"","settings":{},""" +
            """"urnetwork":{"walletOverride":"0xdead","byJwt":"j"},"warpSlots":[],"splitRules":[]}"""
        val data = AppBackupSerializer.deserialize(v2Json)
        assertEquals("j", data.urnetwork.byJwt)
    }

    private fun makeMinimalBackup() = AppBackupData(
        exportedAt = "2026-05-05T00:00:00Z",
        settings = BackupSettings(
            splitMode = null, ipv6Enabled = null, autoStart = null,
            manualEngine = null, bydpiWinningArgs = null, urnetworkEnabled = null,
            urnetworkJwt = null, customDnsServers = null, hostsMode = null,
            hostsList = null, uiLocaleTag = null, appMode = null,
            fptnToken = null,
        ),
        urnetwork = BackupUrnetwork(),
        warpSlots = emptyList(),
        splitRules = emptyList(),
    )

    private class FakePreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> get() = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }

    private class FakeWarpSlotStore : WarpConfigSlotStore {
        val slots = MutableStateFlow<List<WarpConfigSlot>>(emptyList())
        override fun slots(): Flow<List<WarpConfigSlot>> = slots
        override fun activeSlot(): Flow<WarpConfigSlot?> = slots.map { it.firstOrNull { s -> s.isActive } }
        override fun activeConfig(): Flow<WarpConfig?> = slots.map { it.firstOrNull { s -> s.isActive }?.config }
        override suspend fun addSlot(
            name: String,
            config: WarpConfig,
            rawIni: String?,
            endpointList: List<String>,
        ): String = error("not used")
        override suspend fun setActive(id: String) = Unit
        override suspend fun rename(id: String, name: String) = Unit
        override suspend fun updateSlot(
            id: String,
            name: String,
            config: WarpConfig,
            rawIni: String?,
            endpointList: List<String>,
        ) = Unit
        override suspend fun delete(id: String) = Unit
        override suspend fun clear() {
            slots.value = emptyList()
        }
        override suspend fun replaceAll(newSlots: List<WarpConfigSlot>) {
            slots.value = newSlots
        }
    }

    private class FakeUrnetworkStore : UrnetworkConfigStore {
        private val delegate = ru.ozero.engineurnetwork.InMemoryUrnetworkConfigStore()
        fun inject(transform: (ru.ozero.engineurnetwork.UrnetworkConfig) -> ru.ozero.engineurnetwork.UrnetworkConfig) {
            delegate.inject(transform)
        }
        override fun config() = delegate.config()
        override suspend fun update(
            transform: (ru.ozero.engineurnetwork.UrnetworkConfig) -> ru.ozero.engineurnetwork.UrnetworkConfig,
        ) = delegate.update(transform)
    }

    private class FakeSplitRuleDao : AppSplitRuleDao {
        val rules = MutableStateFlow<List<AppSplitRule>>(emptyList())
        override suspend fun upsert(rule: AppSplitRule) {
            val current = rules.value.toMutableList()
            current.removeAll { it.packageName == rule.packageName }
            current.add(rule)
            rules.value = current
        }
        override fun observeAll(): Flow<List<AppSplitRule>> = rules
        override suspend fun delete(packageName: String) {
            rules.value = rules.value.filter { it.packageName != packageName }
        }
    }

    private class FakeFptnStore : FptnConfigStore {
        private val delegate = ru.ozero.enginefptn.InMemoryFptnConfigStore()
        fun inject(transform: (FptnConfig) -> FptnConfig) = delegate.inject(transform)
        override fun config() = delegate.config()
        override fun currentConfig(): FptnConfig = delegate.currentConfig()
        override suspend fun update(transform: (FptnConfig) -> FptnConfig) = delegate.update(transform)
    }

    private class FakeStrategyBackupProvider : StrategyBackupProvider {
        var exported: BackupStrategy = BackupStrategy()
        var imported: BackupStrategy? = null
        override suspend fun export(): BackupStrategy = exported
        override suspend fun import(strategy: BackupStrategy) {
            imported = strategy
        }
    }
}
