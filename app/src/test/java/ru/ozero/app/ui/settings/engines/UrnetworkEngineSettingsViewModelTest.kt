package ru.ozero.app.ui.settings.engines

import com.bringyour.sdk.ConnectLocation
import com.bringyour.sdk.LocationsViewController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.engineurnetwork.UrnetworkSdkBridge
import kotlin.test.assertIs
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class UrnetworkEngineSettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState стартует как Loading`() = runTest {
        val vm = UrnetworkEngineSettingsViewModel(FakeUrnetworkBridge())
        assertSame(UrnetworkSettingsUiState.Loading, vm.uiState.value)
    }

    @Test
    fun `uiState переходит в NotConnected когда bridge не подключён`() = runTest {
        val vm = UrnetworkEngineSettingsViewModel(FakeUrnetworkBridge(connected = false))
        advanceUntilIdle()
        assertIs<UrnetworkSettingsUiState.NotConnected>(vm.uiState.value)
    }
}

private class FakeUrnetworkBridge(private val connected: Boolean = false) : UrnetworkSdkBridge {
    override suspend fun start(
        walletAddress: String,
        apiUrl: String,
        connectUrl: String,
        byClientJwt: String,
    ): UrnetworkSdkBridge.StartResult = UrnetworkSdkBridge.StartResult.Success

    override suspend fun stop() = Unit
    override fun isRunning(): Boolean = connected
    override suspend fun attachTun(tunFd: Int): UrnetworkSdkBridge.AttachResult =
        UrnetworkSdkBridge.AttachResult.Success
    override fun connectTo(location: ConnectLocation) = Unit
    override fun connectBestAvailable() = Unit
    override fun selectedLocation(): ConnectLocation? = null
    override fun openLocationsViewController(): LocationsViewController? = null
    override fun setProvidePaused(paused: Boolean) = Unit
    override fun isProvidePaused(): Boolean = true
    override fun peerCount(): Int = 0
    override fun unpaidByteCount(): Long = 0L
    override fun fetchTransferStats() = Unit
}
