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
import ru.ozero.engineurnetwork.UrnetworkConfigStore
import ru.ozero.enginewarp.AwgParams
import ru.ozero.enginewarp.WarpConfig
import ru.ozero.enginewarp.WarpConfigSlot
import ru.ozero.enginewarp.WarpConfigSlotStore
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppBackupManagerTest {

    private val ozeroDs = FakePreferencesDataStore()
    private val warpStore = FakeWarpSlotStore()
    private val urnStore = FakeUrnetworkStore()
    private val splitDao = FakeSplitRuleDao()

    private val manager = AppBackupManager(ozeroDs, warpStore, urnStore, splitDao)

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
        }

        val data = manager.export()
        assertEquals("per_app", data.settings.splitMode)
        assertEquals(true, data.settings.ipv6Enabled)
        assertEquals("byedpi", data.settings.manualEngine)
        assertEquals("ru", data.settings.uiLocaleTag)
    }

    @Test
    fun `export — WARP слоты конвертируются корректно`() = runTest {
        warpStore.slots.value = listOf(sampleWarpSlot)

        val data = manager.export()
        assertEquals(1, data.warpSlots.size)
        val slot = data.warpSlots[0]
        assertEquals("warp-id-1", slot.id)
        assertEquals("Main", slot.name)
        assertTrue(slot.isActive)
        assertEquals("privKey", slot.privateKey)
        assertEquals(5, slot.awgJc)
        assertEquals(100, slot.awgJmin)
        assertEquals(200, slot.awgJmax)
    }

    @Test
    fun `export — URnetwork config`() = runTest {
        urnStore.walletOverride = "0xwallet"
        urnStore.byJwt = "jwt-token"

        val data = manager.export()
        assertEquals("0xwallet", data.urnetwork.walletOverride)
        assertEquals("jwt-token", data.urnetwork.byJwt)
    }

    @Test
    fun `export — split rules из DAO`() = runTest {
        splitDao.rules.value = listOf(
            AppSplitRule("com.a", true),
            AppSplitRule("com.b", false),
        )

        val data = manager.export()
        assertEquals(2, data.splitRules.size)
        assertEquals("com.a", data.splitRules[0].packageName)
        assertTrue(data.splitRules[0].isExcluded)
    }

    @Test
    fun `import — настройки записываются в DataStore`() = runTest {
        val data = makeMinimalBackup().copy(
            settings = BackupSettings(
                splitMode = "global",
                ipv6Enabled = false,
                autoStart = true,
                manualEngine = "warp",
                bydpiWinningArgs = "--test",
                urnetworkEnabled = null,
                urnetworkJwt = null,
                customDnsServers = null,
                hostsMode = null,
                hostsList = null,
                uiLocaleTag = "en",
                appMode = null,
            ),
        )
        manager.import(data)

        val prefs = ozeroDs.data.first()
        assertEquals("global", prefs[SettingsKeys.SPLIT_MODE])
        assertEquals(false, prefs[SettingsKeys.IPV6_ENABLED])
        assertEquals(true, prefs[SettingsKeys.AUTO_START])
        assertEquals("warp", prefs[SettingsKeys.MANUAL_ENGINE])
        assertEquals("en", prefs[SettingsKeys.UI_LOCALE_TAG])
    }

    @Test
    fun `import — null настройки не перезаписывают DataStore`() = runTest {
        ozeroDs.edit { prefs -> prefs[SettingsKeys.SPLIT_MODE] = "existing" }
        val data = makeMinimalBackup().copy(
            settings = BackupSettings(
                splitMode = null, ipv6Enabled = null, autoStart = null,
                manualEngine = null, bydpiWinningArgs = null, urnetworkEnabled = null,
                urnetworkJwt = null, customDnsServers = null, hostsMode = null,
                hostsList = null, uiLocaleTag = null, appMode = null,
            ),
        )
        manager.import(data)
        assertEquals("existing", ozeroDs.data.first()[SettingsKeys.SPLIT_MODE])
    }

    @Test
    fun `import — WARP слоты восстанавливаются через replaceAll`() = runTest {
        val backup = makeMinimalBackup().copy(
            warpSlots = listOf(
                BackupWarpSlot(
                    id = "restored-id", name = "Restored", isActive = true,
                    privateKey = "pk", publicKey = "pub", peerPublicKey = "pp",
                    peerEndpoint = "ep:2408", interfaceAddressV4 = "10.0.0.1/32",
                    interfaceAddressV6 = "::", accountLicense = "", mtu = 1280,
                    dnsServers = listOf("1.1.1.1"), keepaliveSeconds = 25,
                    awgJc = 5, awgJmin = 100, awgJmax = 200, awgS1 = 0, awgS2 = 0,
                    awgH1 = 1L, awgH2 = 2L, awgH3 = 3L, awgH4 = 4L,
                ),
            ),
        )
        manager.import(backup)

        assertEquals(1, warpStore.slots.value.size)
        assertEquals("restored-id", warpStore.slots.value[0].id)
        assertEquals("Restored", warpStore.slots.value[0].name)
        assertTrue(warpStore.slots.value[0].isActive)
    }

    @Test
    fun `import — пустые WARP слоты — replaceAll не вызывается`() = runTest {
        warpStore.slots.value = listOf(sampleWarpSlot)
        manager.import(makeMinimalBackup().copy(warpSlots = emptyList()))
        assertEquals(listOf(sampleWarpSlot), warpStore.slots.value)
    }

    @Test
    fun `import — пустые split rules — существующие не стираются`() = runTest {
        splitDao.rules.value = listOf(AppSplitRule("com.existing", true))
        manager.import(makeMinimalBackup().copy(splitRules = emptyList()))
        assertEquals(1, splitDao.rules.value.size)
        assertEquals("com.existing", splitDao.rules.value[0].packageName)
    }

    @Test
    fun `import — null URnetwork поля — существующие не перезаписываются`() = runTest {
        urnStore.walletOverride = "existing-wallet"
        urnStore.byJwt = "existing-jwt"
        manager.import(makeMinimalBackup().copy(urnetwork = BackupUrnetwork(walletOverride = null, byJwt = null)))
        assertEquals("existing-wallet", urnStore.walletOverride)
        assertEquals("existing-jwt", urnStore.byJwt)
    }

    @Test
    fun `import — split rules — удаляет отсутствующие, добавляет новые`() = runTest {
        splitDao.rules.value = listOf(
            AppSplitRule("com.old", true),
            AppSplitRule("com.keep", false),
        )
        val backup = makeMinimalBackup().copy(
            splitRules = listOf(
                BackupSplitRule("com.keep", false),
                BackupSplitRule("com.new", true),
            ),
        )
        manager.import(backup)

        val packages = splitDao.rules.value.map { it.packageName }
        assertTrue("com.old" !in packages)
        assertTrue("com.keep" in packages)
        assertTrue("com.new" in packages)
    }

    @Test
    fun `export then import — полный roundtrip`() = runTest {
        ozeroDs.edit { prefs ->
            prefs[SettingsKeys.SPLIT_MODE] = "per_app"
            prefs[SettingsKeys.IPV6_ENABLED] = true
            prefs[SettingsKeys.UI_LOCALE_TAG] = "ru"
        }
        warpStore.slots.value = listOf(sampleWarpSlot)
        urnStore.walletOverride = "wallet"
        splitDao.rules.value = listOf(AppSplitRule("com.example", true))

        val exported = manager.export()
        val jsonString = AppBackupSerializer.serialize(exported)

        val freshDs = FakePreferencesDataStore()
        val freshWarp = FakeWarpSlotStore()
        val freshUrn = FakeUrnetworkStore()
        val freshDao = FakeSplitRuleDao()
        val freshManager = AppBackupManager(freshDs, freshWarp, freshUrn, freshDao)

        freshManager.import(AppBackupSerializer.deserialize(jsonString))

        assertEquals("per_app", freshDs.data.first()[SettingsKeys.SPLIT_MODE])
        assertEquals(true, freshDs.data.first()[SettingsKeys.IPV6_ENABLED])
        assertEquals("ru", freshDs.data.first()[SettingsKeys.UI_LOCALE_TAG])
        assertEquals(1, freshWarp.slots.value.size)
        assertEquals("warp-id-1", freshWarp.slots.value[0].id)
        assertEquals("wallet", freshUrn.walletOverride)
        assertEquals(1, freshDao.rules.value.size)
        assertEquals("com.example", freshDao.rules.value[0].packageName)
    }

    private fun makeMinimalBackup() = AppBackupData(
        exportedAt = "2026-05-05T00:00:00Z",
        settings = BackupSettings(
            splitMode = null, ipv6Enabled = null, autoStart = null,
            manualEngine = null, bydpiWinningArgs = null, urnetworkEnabled = null,
            urnetworkJwt = null, customDnsServers = null, hostsMode = null,
            hostsList = null, uiLocaleTag = null, appMode = null,
        ),
        urnetwork = BackupUrnetwork(walletOverride = null, byJwt = null),
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
        var walletOverride: String? = null
        var byJwt: String? = null

        override fun walletAddress(): Flow<String> = MutableStateFlow(walletOverride ?: "default")
        override fun walletOverride(): Flow<String?> = MutableStateFlow(walletOverride)
        override suspend fun setWalletOverride(value: String?) {
            walletOverride = value
        }
        override fun byJwt(): Flow<String?> = MutableStateFlow(byJwt)
        override suspend fun setByJwt(value: String?) {
            byJwt = value
        }
        var byClientJwt: String? = null
        override fun byClientJwt(): Flow<String?> = MutableStateFlow(byClientJwt)
        override suspend fun setByClientJwt(value: String?) {
            byClientJwt = value
        }
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
}
