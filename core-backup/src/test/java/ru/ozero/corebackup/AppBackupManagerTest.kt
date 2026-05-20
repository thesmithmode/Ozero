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
import ru.ozero.enginetelegram.TelegramConfigStore
import ru.ozero.enginetelegram.TelegramProxyConfig
import ru.ozero.engineurnetwork.UrnetworkConfigStore
import ru.ozero.engineurnetwork.UrnetworkLocationSelection
import ru.ozero.engineurnetwork.UrnetworkProvideControlMode
import ru.ozero.engineurnetwork.UrnetworkProvideNetworkMode
import ru.ozero.engineurnetwork.UrnetworkWindowType
import ru.ozero.enginewarp.AwgParams
import ru.ozero.enginewarp.WarpConfig
import ru.ozero.enginewarp.WarpConfigSlot
import ru.ozero.enginewarp.WarpConfigSlotStore
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppBackupManagerTest {

    private val ozeroDs = FakePreferencesDataStore()
    private val warpStore = FakeWarpSlotStore()
    private val urnStore = FakeUrnetworkStore()
    private val splitDao = FakeSplitRuleDao()
    private val telegramStore = FakeTelegramStore()

    private val manager = AppBackupManager(ozeroDs, warpStore, urnStore, splitDao, telegramStore)

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
        assertEquals(true, data.settings.bydpiUseUiMode)
        assertEquals("""{"a":1}""", data.settings.bydpiUiSettingsJson)
        assertEquals("DE", data.settings.urnetworkCountryCode)
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
    fun `export — Telegram MTProxy config`() = runTest {
        telegramStore.set(TelegramProxyConfig(enabled = true, port = 2080, domain = "tg.example", secret = "deadbeef"))
        val data = manager.export()
        assertNotNull(data.telegram)
        assertEquals(true, data.telegram!!.enabled)
        assertEquals(2080, data.telegram!!.port)
        assertEquals("tg.example", data.telegram!!.domain)
        assertEquals("deadbeef", data.telegram!!.secret)
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
        telegramStore.set(TelegramProxyConfig(enabled = true, port = 123, domain = "x", secret = "s"))
        splitDao.rules.value = listOf(AppSplitRule("com.x", true))

        val data = manager.export(setOf(BackupCategory.WARP))

        assertEquals(1, data.warpSlots.size)
        assertNull(data.settings.splitMode)
        assertNull(data.urnetwork.byJwt)
        assertNull(data.telegram)
        assertTrue(data.splitRules.isEmpty())
        assertNull(data.strategy)
    }

    @Test
    fun `export categories — только TELEGRAM — WARP пустой`() = runTest {
        warpStore.slots.value = listOf(sampleWarpSlot)
        telegramStore.set(TelegramProxyConfig(enabled = true, port = 9999, domain = "d", secret = "s"))

        val data = manager.export(setOf(BackupCategory.TELEGRAM))

        assertTrue(data.warpSlots.isEmpty())
        assertEquals(9999, data.telegram?.port)
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
        val data = makeMinimalBackup().copy(
            urnetwork = BackupUrnetwork(
                byJwt = "imported-jwt",
                windowType = "speed",
                fixedIpSize = true,
                allowDirect = false,
                provideEnabled = false,
                provideControlMode = "auto",
                provideNetworkMode = "all",
                selectedLocation = BackupUrnetworkLocation("ES", "Madrid", "Madrid"),
            ),
        )
        manager.import(data)

        val cfg = urnStore.config().first()
        assertEquals("imported-jwt", cfg.byJwt)
        assertEquals(UrnetworkWindowType.SPEED, cfg.windowType)
        assertEquals(true, cfg.fixedIpSize)
        assertEquals(false, cfg.allowDirect)
        assertEquals(false, cfg.provideEnabled)
        assertEquals(UrnetworkProvideControlMode.AUTO, cfg.provideControlMode)
        assertEquals(UrnetworkProvideNetworkMode.ALL, cfg.provideNetworkMode)
        assertEquals("ES", cfg.selectedLocation.countryCode)
        assertEquals("Madrid", cfg.selectedLocation.city)
    }

    @Test
    fun `import — Telegram config применяется через setters`() = runTest {
        val data = makeMinimalBackup().copy(
            telegram = BackupTelegram(enabled = true, port = 4242, domain = "tg.local", secret = "abc"),
        )
        manager.import(data)
        val cfg = telegramStore.config().first()
        assertEquals(true, cfg.enabled)
        assertEquals(4242, cfg.port)
        assertEquals("tg.local", cfg.domain)
        assertEquals("abc", cfg.secret)
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
    fun `import categories — TELEGRAM не вызывается если категория не выбрана`() = runTest {
        val data = makeMinimalBackup().copy(
            telegram = BackupTelegram(enabled = true, port = 1, domain = "x", secret = "y"),
        )
        manager.import(data, setOf(BackupCategory.WARP))
        val cfg = telegramStore.config().first()
        assertEquals(false, cfg.enabled)
    }

    @Test
    fun `import — WARP пустой — replaceAll не вызывается`() = runTest {
        warpStore.slots.value = listOf(sampleWarpSlot)
        manager.import(makeMinimalBackup())
        assertEquals(listOf(sampleWarpSlot), warpStore.slots.value)
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
        }
        warpStore.slots.value = listOf(sampleWarpSlot)
        urnStore.inject { it.copy(byJwt = "jwt", selectedLocation = UrnetworkLocationSelection("RU", null, null)) }
        telegramStore.set(TelegramProxyConfig(enabled = true, port = 8080, domain = "tg", secret = "secret"))
        splitDao.rules.value = listOf(AppSplitRule("com.example", true))

        val exported = manager.export()
        val jsonString = AppBackupSerializer.serialize(exported)

        val freshDs = FakePreferencesDataStore()
        val freshWarp = FakeWarpSlotStore()
        val freshUrn = FakeUrnetworkStore()
        val freshDao = FakeSplitRuleDao()
        val freshTg = FakeTelegramStore()
        val freshManager = AppBackupManager(freshDs, freshWarp, freshUrn, freshDao, freshTg)

        freshManager.import(AppBackupSerializer.deserialize(jsonString))

        assertEquals("per_app", freshDs.data.first()[SettingsKeys.SPLIT_MODE])
        assertEquals(true, freshDs.data.first()[SettingsKeys.IPV6_ENABLED])
        assertEquals("warp", freshDs.data.first()[SettingsKeys.ENGINE_AUTO_PRIORITY])
        assertEquals(1, freshWarp.slots.value.size)
        assertEquals("jwt", freshUrn.config().first().byJwt)
        assertEquals("RU", freshUrn.config().first().selectedLocation.countryCode)
        assertEquals("tg", freshTg.config().first().domain)
        assertEquals(1, freshDao.rules.value.size)
    }

    @Test
    fun `export with provider — strategy block в backup`() = runTest {
        val provider = FakeStrategyBackupProvider()
        provider.exported = BackupStrategy(
            settings = BackupStrategySettings(requestsPerDomain = 3, evolutionTargetFitness = 0.95f),
        )
        val mgr = AppBackupManager(ozeroDs, warpStore, urnStore, splitDao, telegramStore, provider)
        val data = mgr.export()
        assertEquals(provider.exported, data.strategy)
    }

    @Test
    fun `import strategy — provider получает данные`() = runTest {
        val provider = FakeStrategyBackupProvider()
        val mgr = AppBackupManager(ozeroDs, warpStore, urnStore, splitDao, telegramStore, provider)
        val strategy = BackupStrategy(settings = BackupStrategySettings(evolutionTargetFitness = 0.88f))
        mgr.import(makeMinimalBackup().copy(strategy = strategy))
        assertEquals(strategy, provider.imported)
    }

    @Test
    fun `import strategy null — provider не вызывается`() = runTest {
        val provider = FakeStrategyBackupProvider()
        val mgr = AppBackupManager(ozeroDs, warpStore, urnStore, splitDao, telegramStore, provider)
        mgr.import(makeMinimalBackup())
        assertNull(provider.imported)
    }

    @Test
    fun `v1 backup import — strategy и telegram null`() = runTest {
        val v1Json = """{"version":1,"exportedAt":"2025-01-01T00:00:00Z",""" +
            """"settings":{},"urnetwork":{},"warpSlots":[],"splitRules":[]}"""
        val data = AppBackupSerializer.deserialize(v1Json)
        assertEquals(1, data.version)
        assertNull(data.strategy)
        assertNull(data.telegram)
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
        override suspend fun addSlot(name: String, config: WarpConfig, rawIni: String?): String = error("not used")
        override suspend fun setActive(id: String) = Unit
        override suspend fun rename(id: String, name: String) = Unit
        override suspend fun updateSlot(id: String, name: String, config: WarpConfig, rawIni: String?) = Unit
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

    private class FakeTelegramStore : TelegramConfigStore {
        private val state = MutableStateFlow(TelegramProxyConfig())
        fun set(cfg: TelegramProxyConfig) {
            state.value = cfg
        }
        override fun config(): Flow<TelegramProxyConfig> = state
        override suspend fun setEnabled(value: Boolean) {
            state.value = state.value.copy(enabled = value)
        }
        override suspend fun setPort(value: Int) {
            state.value = state.value.copy(port = value)
        }
        override suspend fun setDomain(value: String) {
            state.value = state.value.copy(domain = value)
        }
        override suspend fun setSecret(value: String) {
            state.value = state.value.copy(secret = value)
        }
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
