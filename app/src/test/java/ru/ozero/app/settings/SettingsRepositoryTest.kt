package ru.ozero.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.settings.AppMode
import ru.ozero.enginescore.settings.AutoStartGateway
import ru.ozero.enginescore.settings.ByeDpiUiSettings
import ru.ozero.enginescore.settings.HostsMode
import ru.ozero.enginescore.settings.SettingsKeys
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.enginescore.settings.SettingsRepository
import ru.ozero.enginescore.settings.SplitTunnelMode
import ru.ozero.enginescore.settings.TrafficMode
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsRepositoryTest {

    @TempDir
    lateinit var tmpDir: File

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var autoStartGateway: FakeAutoStartGateway
    private lateinit var repository: SettingsRepository

    @BeforeEach
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(tmpDir, "settings.preferences_pb") },
        )
        autoStartGateway = FakeAutoStartGateway()
        repository = SettingsRepositoryImpl(dataStore, autoStartGateway)
    }

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `emits defaults when DataStore is empty`() = runTest {
        val current = repository.settings.first()

        assertEquals(SettingsModel.DEFAULT, current)
        assertEquals(SplitTunnelMode.ALL, current.splitMode)
        assertFalse(current.ipv6Enabled)
        assertFalse(current.autoStart)
        assertEquals(TrafficMode.TUN, current.trafficMode)
        assertNull(current.manualEngine)
    }

    @Test
    fun `setSplitMode persists and emits new value`() = runTest {
        repository.setSplitMode(SplitTunnelMode.BYPASS_LAN)

        val current = repository.settings.first()
        assertEquals(SplitTunnelMode.BYPASS_LAN, current.splitMode)
    }

    @Test
    fun `setSplitMode allowlist and blocklist round-trip`() = runTest {
        repository.setSplitMode(SplitTunnelMode.ALLOWLIST)
        assertEquals(SplitTunnelMode.ALLOWLIST, repository.settings.first().splitMode)

        repository.setSplitMode(SplitTunnelMode.BLOCKLIST)
        assertEquals(SplitTunnelMode.BLOCKLIST, repository.settings.first().splitMode)
    }

    @Test
    fun `setIpv6Enabled persists toggle`() = runTest {
        repository.setIpv6Enabled(true)
        assertTrue(repository.settings.first().ipv6Enabled)

        repository.setIpv6Enabled(false)
        assertFalse(repository.settings.first().ipv6Enabled)
    }

    @Test
    fun `setAutoStart persists toggle and notifies gateway`() = runTest {
        repository.setAutoStart(true)

        assertTrue(repository.settings.first().autoStart)
        assertEquals(listOf(true), autoStartGateway.invocations)

        repository.setAutoStart(false)
        assertFalse(repository.settings.first().autoStart)
        assertEquals(listOf(true, false), autoStartGateway.invocations)
    }

    @Test
    fun `setTrafficMode persists PROXY and round-trips back to TUN`() = runTest {
        repository.setTrafficMode(TrafficMode.PROXY)
        assertEquals(TrafficMode.PROXY, repository.settings.first().trafficMode)

        repository.setTrafficMode(TrafficMode.TUN)
        assertEquals(TrafficMode.TUN, repository.settings.first().trafficMode)
    }

    @Test
    fun `unknown traffic mode in DataStore falls back to TUN`() = runTest {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.TRAFFIC_MODE] = "WIRELESS"
        }

        assertEquals(TrafficMode.TUN, repository.settings.first().trafficMode)
    }

    @Test
    fun `setManualEngine stores engine id and null clears it`() = runTest {
        repository.setManualEngine(EngineId.XRAY)
        assertEquals(EngineId.XRAY, repository.settings.first().manualEngine)

        repository.setManualEngine(null)
        assertNull(repository.settings.first().manualEngine)
    }

    @Test
    fun `setManualEngine accepts every EngineId value`() = runTest {
        EngineId.entries.forEach { engine ->
            repository.setManualEngine(engine)
            assertEquals(engine, repository.settings.first().manualEngine)
        }
    }

    @Test
    fun `unknown split mode in DataStore falls back to default`() = runTest {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.SPLIT_MODE] = "NOT_A_MODE"
        }

        val current = repository.settings.first()
        assertEquals(SettingsModel.DEFAULT_SPLIT_MODE, current.splitMode)
    }

    @Test
    fun `unknown manual engine in DataStore falls back to null`() = runTest {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.MANUAL_ENGINE] = "ROGUE"
        }

        assertNull(repository.settings.first().manualEngine)
    }

    @Test
    fun `engineAutoPriority default содержит все non-stub engines в этом порядке`() = runTest {
        val current = repository.settings.first()
        assertEquals(
            listOf(
                EngineId.WARP, EngineId.URNETWORK, EngineId.BYEDPI,
                EngineId.MASTERDNS, EngineId.SINGBOX, EngineId.FPTN,
            ),
            current.engineAutoPriority,
        )
    }

    @Test
    fun `setEngineAutoPriority сохраняет CSV и читается обратно с reconcile`() = runTest {
        repository.setEngineAutoPriority(listOf(EngineId.WARP, EngineId.URNETWORK, EngineId.BYEDPI))
        assertEquals(
            listOf(
                EngineId.WARP, EngineId.URNETWORK, EngineId.BYEDPI,
                EngineId.MASTERDNS, EngineId.SINGBOX, EngineId.FPTN,
            ),
            repository.settings.first().engineAutoPriority,
        )
    }

    @Test
    fun `setEngineAutoPriority дедуплицирует stub движки и reconcile добавляет недостающие`() = runTest {
        repository.setEngineAutoPriority(
            listOf(
                EngineId.BYEDPI, EngineId.XRAY, EngineId.BYEDPI,
                EngineId.WARP, EngineId.TOR,
            ),
        )
        assertEquals(
            listOf(
                EngineId.BYEDPI, EngineId.WARP, EngineId.URNETWORK,
                EngineId.MASTERDNS, EngineId.SINGBOX, EngineId.FPTN,
            ),
            repository.settings.first().engineAutoPriority,
        )
    }

    @Test
    fun `setEngineAutoPriority с пустым списком возвращает default`() = runTest {
        repository.setEngineAutoPriority(emptyList())
        assertEquals(
            SettingsModel.DEFAULT_ENGINE_AUTO_PRIORITY,
            repository.settings.first().engineAutoPriority,
        )
    }

    @Test
    fun `engineAutoPriority с unknown id в CSV падает на default`() = runTest {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.ENGINE_AUTO_PRIORITY] = "ROGUE,NOPE"
        }
        assertEquals(
            SettingsModel.DEFAULT_ENGINE_AUTO_PRIORITY,
            repository.settings.first().engineAutoPriority,
        )
    }

    @Test
    fun `engineAutoPriority CSV с trim spaces и reconcile`() = runTest {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.ENGINE_AUTO_PRIORITY] = " WARP , BYEDPI "
        }
        assertEquals(
            listOf(
                EngineId.WARP, EngineId.BYEDPI, EngineId.URNETWORK,
                EngineId.MASTERDNS, EngineId.SINGBOX, EngineId.FPTN,
            ),
            repository.settings.first().engineAutoPriority,
        )
    }

    @Test
    fun `urnetwork disabled by default`() = runTest {
        assertFalse(repository.settings.first().urnetworkEnabled)
        assertNull(repository.settings.first().urnetworkJwt)
    }

    @Test
    fun `setUrnetworkEnabled persists toggle`() = runTest {
        repository.setUrnetworkEnabled(true)
        assertTrue(repository.settings.first().urnetworkEnabled)

        repository.setUrnetworkEnabled(false)
        assertFalse(repository.settings.first().urnetworkEnabled)
    }

    @Test
    fun `setUrnetworkJwt stores jwt and null clears it`() = runTest {
        repository.setUrnetworkJwt("my-test-jwt")
        assertEquals("my-test-jwt", repository.settings.first().urnetworkJwt)

        repository.setUrnetworkJwt(null)
        assertNull(repository.settings.first().urnetworkJwt)
    }

    @Test
    fun `byedpi winning args null by default`() = runTest {
        assertNull(repository.settings.first().byedpiWinningArgs)
    }

    @Test
    fun `setByedpiWinningArgs persists и null или blank очищает`() = runTest {
        val customArgs = "-Ku -An -At -o1 -d4+s"
        repository.setByedpiWinningArgs(customArgs)
        assertEquals(customArgs, repository.settings.first().byedpiWinningArgs)

        repository.setByedpiWinningArgs(null)
        assertNull(repository.settings.first().byedpiWinningArgs)

        repository.setByedpiWinningArgs(customArgs)
        assertEquals(customArgs, repository.settings.first().byedpiWinningArgs)

        repository.setByedpiWinningArgs("   ")
        assertNull(
            repository.settings.first().byedpiWinningArgs,
            "Пустые/whitespace args должны очищать override — иначе пользователь сломает byedpi пустым полем.",
        )
    }

    @Test
    fun `setByedpiDefaultAccepted persists boolean`() = runTest {
        assertFalse(repository.settings.first().byedpiDefaultAccepted)
        repository.setByedpiDefaultAccepted(true)
        assertTrue(repository.settings.first().byedpiDefaultAccepted)
        repository.setByedpiDefaultAccepted(false)
        assertFalse(repository.settings.first().byedpiDefaultAccepted)
    }

    @Test
    fun `customDnsServers default empty list`() = runTest {
        assertTrue(repository.settings.first().customDnsServers.isEmpty())
    }

    @Test
    fun `setCustomDnsServers persists list`() = runTest {
        repository.setCustomDnsServers(listOf("8.8.8.8", "1.1.1.1"))
        assertEquals(listOf("8.8.8.8", "1.1.1.1"), repository.settings.first().customDnsServers)
    }

    @Test
    fun `setCustomDnsServers empty list очищает`() = runTest {
        repository.setCustomDnsServers(listOf("8.8.8.8"))
        repository.setCustomDnsServers(emptyList())
        assertTrue(repository.settings.first().customDnsServers.isEmpty())
    }

    @Test
    fun `setCustomDnsServers trims и фильтрует blank entries`() = runTest {
        repository.setCustomDnsServers(listOf("  8.8.8.8  ", "", "   ", "1.1.1.1"))
        assertEquals(listOf("8.8.8.8", "1.1.1.1"), repository.settings.first().customDnsServers)
    }

    @Test
    fun `setCustomDnsServers filters invalid addresses and accepts ipv6`() = runTest {
        repository.setCustomDnsServers(listOf("256.1.1.1", "2001:4860:4860::8888", "bad,entry"))

        assertEquals(listOf("2001:4860:4860::8888"), repository.settings.first().customDnsServers)
    }

    @Test
    fun `setCustomDnsServers removes key when all entries invalid`() = runTest {
        repository.setCustomDnsServers(listOf("8.8.8.8"))
        repository.setCustomDnsServers(listOf("256.256.256.256", "bad,entry"))

        assertTrue(repository.settings.first().customDnsServers.isEmpty())
    }

    @Test
    fun `hosts mode and hosts round trip with validation`() = runTest {
        repository.setHostsMode(HostsMode.BLACKLIST)
        repository.setHosts(listOf(" example.com ", "bad host", "127.0.0.1:5353", ""))

        val current = repository.settings.first()
        assertEquals(HostsMode.BLACKLIST, current.hostsMode)
        assertEquals(listOf("example.com", "127.0.0.1:5353"), current.hosts)
    }

    @Test
    fun `unknown hosts mode falls back to default and invalid hosts clear list`() = runTest {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.HOSTS_MODE] = "UNKNOWN"
            prefs[SettingsKeys.HOSTS_LIST] = "one.test, two.test"
        }
        assertEquals(HostsMode.DISABLED, repository.settings.first().hostsMode)

        repository.setHosts(listOf("bad host", "also bad"))
        assertTrue(repository.settings.first().hosts.isEmpty())
    }

    @Test
    fun `urnetwork country code normalizes valid values and clears invalid values`() = runTest {
        repository.setUrnetworkCountryCode(" us ")
        assertEquals("US", repository.settings.first().urnetworkCountryCode)

        repository.setUrnetworkCountryCode("usa")
        assertNull(repository.settings.first().urnetworkCountryCode)

        repository.setUrnetworkCountryCode("de")
        repository.setUrnetworkCountryCode(null)
        assertNull(repository.settings.first().urnetworkCountryCode)
    }

    @Test
    fun `byedpi ui mode and settings round trip`() = runTest {
        repository.setByedpiUseUiMode(true)
        repository.setByedpiUiSettings(ByeDpiUiSettings.DEFAULT.copy(fakeSni = "front.example.com"))

        val current = repository.settings.first()
        assertTrue(current.byedpiUseUiMode)
        assertEquals("front.example.com", current.byedpiUiSettings.fakeSni)
    }

    @Test
    fun `uiLocaleTag default null`() = runTest {
        assertNull(repository.settings.first().uiLocaleTag)
    }

    @Test
    fun `setUiLocaleTag persists tag`() = runTest {
        repository.setUiLocaleTag("ru")
        assertEquals("ru", repository.settings.first().uiLocaleTag)
        repository.setUiLocaleTag("zh-CN")
        assertEquals("zh-CN", repository.settings.first().uiLocaleTag)
    }

    @Test
    fun `setUiLocaleTag null clears`() = runTest {
        repository.setUiLocaleTag("en")
        repository.setUiLocaleTag(null)
        assertNull(repository.settings.first().uiLocaleTag)
    }

    @Test
    fun `setUiLocaleTag blank clears`() = runTest {
        repository.setUiLocaleTag("en")
        repository.setUiLocaleTag("   ")
        assertNull(repository.settings.first().uiLocaleTag)
    }

    @Test
    fun `appMode default is SIMPLE`() = runTest {
        assertEquals(AppMode.SIMPLE, repository.settings.first().appMode)
    }

    @Test
    fun `setAppMode persists EXPERT and round-trips back to SIMPLE`() = runTest {
        repository.setAppMode(AppMode.EXPERT)
        assertEquals(AppMode.EXPERT, repository.settings.first().appMode)

        repository.setAppMode(AppMode.SIMPLE)
        assertEquals(AppMode.SIMPLE, repository.settings.first().appMode)
    }

    @Test
    fun `unknown app mode in DataStore falls back to SIMPLE`() = runTest {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.APP_MODE] = "UNKNOWN_MODE"
        }
        assertEquals(AppMode.SIMPLE, repository.settings.first().appMode)
    }

    @Test
    fun `alwaysOnBannerDismissed false by default`() = runTest {
        assertFalse(repository.settings.first().alwaysOnBannerDismissed)
    }

    @Test
    fun `setAlwaysOnBannerDismissed persists toggle`() = runTest {
        repository.setAlwaysOnBannerDismissed(true)
        assertTrue(repository.settings.first().alwaysOnBannerDismissed)

        repository.setAlwaysOnBannerDismissed(false)
        assertFalse(repository.settings.first().alwaysOnBannerDismissed)
    }

    @Test
    fun `alwaysOnBannerDismissed reads from DataStore key directly`() = runTest {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.ALWAYS_ON_BANNER_DISMISSED] = true
        }
        assertTrue(repository.settings.first().alwaysOnBannerDismissed)
    }

    @Test
    fun `setKillswitchEnabled persists toggle`() = runTest {
        repository.setKillswitchEnabled(true)
        assertTrue(repository.settings.first().killswitchEnabled)

        repository.setKillswitchEnabled(false)
        assertFalse(repository.settings.first().killswitchEnabled)
    }

    private class FakeAutoStartGateway : AutoStartGateway {
        val invocations = mutableListOf<Boolean>()

        override fun setAutoStart(enabled: Boolean) {
            invocations += enabled
        }
    }
}
