package ru.ozero.app.ui.settings.engines

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import ru.ozero.enginefptn.FptnBypassMethod
import ru.ozero.enginefptn.FptnConfig
import ru.ozero.enginefptn.InMemoryFptnConfigStore
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FptnEngineSettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState maps valid token and config fields`() = runTest {
        val store = InMemoryFptnConfigStore(
            FptnConfig(
                token = validToken(),
                selectedServerName = "Exit B",
                bypassMethod = FptnBypassMethod.SNI.strategyName,
                sniDomain = "front.example",
                autoSelect = false,
                reconnectOnNetworkChange = false,
                reconnectOnIpChange = true,
                maxReconnectAttempts = 9,
                reconnectPauseSeconds = 4,
                resetServerOnDisconnect = false,
            ),
        )
        val viewModel = FptnEngineSettingsViewModel(store)
        collectState(viewModel)

        val state = viewModel.uiState.value

        assertEquals(store.snapshot.token, state.savedToken)
        assertEquals("Exit B", state.selectedServerName)
        assertFalse(state.autoSelect)
        assertEquals(FptnBypassMethod.SNI, state.bypassMethod)
        assertEquals("front.example", state.sniDomain)
        assertFalse(state.reconnectOnNetworkChange)
        assertTrue(state.reconnectOnIpChange)
        assertEquals(9, state.maxReconnectAttempts)
        assertEquals(4, state.reconnectPauseSeconds)
        assertFalse(state.resetServerOnDisconnect)
    }

    @Test
    fun `uiState marks invalid non-empty token and defaults unknown bypass`() = runTest {
        val store = InMemoryFptnConfigStore(
            FptnConfig(
                token = "bad-token",
                bypassMethod = "unknown",
            ),
        )
        val viewModel = FptnEngineSettingsViewModel(store)
        collectState(viewModel)

        val state = viewModel.uiState.value

        assertEquals("bad-token", state.savedToken)
        assertTrue(state.tokenInvalid)
        assertFalse(state.hasToken)
        assertTrue(state.servers.isEmpty())
        assertEquals(FptnBypassMethod.DEFAULT, state.bypassMethod)
    }

    @Test
    fun `onTokenSave trims token and clears selected server`() = runTest {
        val store = InMemoryFptnConfigStore(
            FptnConfig(
                token = "old",
                selectedServerName = "Exit A",
                autoSelect = false,
            ),
        )
        val viewModel = FptnEngineSettingsViewModel(store)

        viewModel.onTokenSave("  new-token  ")
        advanceUntilIdle()

        assertEquals("new-token", store.snapshot.token)
        assertNull(store.snapshot.selectedServerName)
        assertFalse(store.snapshot.autoSelect)
    }

    @Test
    fun `server selection toggles auto select`() = runTest {
        val store = InMemoryFptnConfigStore()
        val viewModel = FptnEngineSettingsViewModel(store)

        viewModel.onServerSelect("Exit A")
        advanceUntilIdle()

        assertEquals("Exit A", store.snapshot.selectedServerName)
        assertFalse(store.snapshot.autoSelect)

        viewModel.onServerSelect(null)
        advanceUntilIdle()

        assertNull(store.snapshot.selectedServerName)
        assertTrue(store.snapshot.autoSelect)
    }

    @Test
    fun `onAutoSelect clears selected server and enables auto select`() = runTest {
        val store = InMemoryFptnConfigStore(
            FptnConfig(selectedServerName = "Exit A", autoSelect = false),
        )
        val viewModel = FptnEngineSettingsViewModel(store)

        viewModel.onAutoSelect()
        advanceUntilIdle()

        assertNull(store.snapshot.selectedServerName)
        assertTrue(store.snapshot.autoSelect)
    }

    @Test
    fun `settings mutations update config store`() = runTest {
        val store = InMemoryFptnConfigStore()
        val viewModel = FptnEngineSettingsViewModel(store)

        viewModel.onBypassMethodChange(FptnBypassMethod.OBFUSCATION)
        viewModel.onReconnectNetworkChange(false)
        viewModel.onReconnectIpChange(true)
        viewModel.onMaxAttemptsChange(12)
        viewModel.onPauseSecondsChange(7)
        viewModel.onResetServerChange(false)
        advanceUntilIdle()

        assertEquals(FptnBypassMethod.OBFUSCATION.strategyName, store.snapshot.bypassMethod)
        assertFalse(store.snapshot.reconnectOnNetworkChange)
        assertTrue(store.snapshot.reconnectOnIpChange)
        assertEquals(12, store.snapshot.maxReconnectAttempts)
        assertEquals(7, store.snapshot.reconnectPauseSeconds)
        assertFalse(store.snapshot.resetServerOnDisconnect)
    }

    @Test
    fun `sni domain ignores blank input and trims valid input`() = runTest {
        val store = InMemoryFptnConfigStore(FptnConfig(sniDomain = "old.example"))
        val viewModel = FptnEngineSettingsViewModel(store)

        viewModel.onSniDomainChange("   ")
        advanceUntilIdle()

        assertEquals("old.example", store.snapshot.sniDomain)

        viewModel.onSniDomainChange("  new.example  ")
        advanceUntilIdle()

        assertEquals("new.example", store.snapshot.sniDomain)
    }

    private fun TestScope.collectState(viewModel: FptnEngineSettingsViewModel) {
        backgroundScope.launch {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()
    }

    private fun validToken(): String {
        val json = """{"version":1,"username":"user","password":"pass",
            "servers":[
                {"name":"Exit A","host":"a.example","port":443,"country_code":"nl"},
                {"name":"Exit B","host":"b.example","port":8443,"country_code":"de"}
            ]}"""
        return "fptn:${Base64.encodeToString(json.toByteArray(), Base64.NO_WRAP)}"
    }
}
