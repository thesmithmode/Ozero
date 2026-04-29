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
import ru.ozero.commonvpn.split.SplitTunnelMode
import ru.ozero.coreapi.EngineId
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

    private class FakeAutoStartGateway : AutoStartGateway {
        val invocations = mutableListOf<Boolean>()

        override fun setAutoStart(enabled: Boolean) {
            invocations += enabled
        }
    }
}
