package ru.ozero.app.ui.settings

import android.content.Context
import android.content.pm.PackageInstaller
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.app.selfupdate.ApkDownloader
import ru.ozero.app.selfupdate.ApkUpdateVerifier
import ru.ozero.app.selfupdate.GithubReleaseFetcher
import ru.ozero.app.selfupdate.SilentPackageInstaller
import ru.ozero.app.selfupdate.UpdateCoordinator
import ru.ozero.app.settings.SettingsModel
import ru.ozero.app.settings.SettingsRepository
import ru.ozero.commonvpn.split.SplitTunnelMode
import ru.ozero.coreapi.EngineId
import ru.ozero.enginetor.dynamicmod.InstallResult
import ru.ozero.enginetor.dynamicmod.SplitInstallClient
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeSettingsRepository
    private lateinit var torClient: FakeSplitInstallClient
    private lateinit var coordinator: FakeUpdateCoordinator
    private lateinit var viewModel: SettingsViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeSettingsRepository()
        torClient = FakeSplitInstallClient()
        coordinator = FakeUpdateCoordinator()
        viewModel = SettingsViewModel(repository, torClient, coordinator)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Loading until repository emits`() = runTest {
        assertIs<SettingsUiState.Loading>(viewModel.uiState.value)
    }

    @Test
    fun `emits Content with default settings when repository emits default`() = runTest {
        repository.emit(SettingsModel.DEFAULT)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertIs<SettingsUiState.Content>(state)
        assertEquals(SettingsModel.DEFAULT, state.model)
    }

    @Test
    fun `emits Content with new model when repository emits update`() = runTest {
        repository.emit(SettingsModel.DEFAULT)
        advanceUntilIdle()

        val updated = SettingsModel(
            splitMode = SplitTunnelMode.ALLOWLIST,
            ipv6Enabled = true,
            autoStart = true,
            manualEngine = EngineId.XRAY,
        )
        repository.emit(updated)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertIs<SettingsUiState.Content>(state)
        assertEquals(updated, state.model)
    }

    @Test
    fun `onSplitModeChange forwards to repository`() = runTest {
        viewModel.onSplitModeChange(SplitTunnelMode.BYPASS_LAN)
        advanceUntilIdle()

        assertEquals(listOf(SplitTunnelMode.BYPASS_LAN), repository.splitModeUpdates)
    }

    @Test
    fun `onIpv6Toggle forwards to repository`() = runTest {
        viewModel.onIpv6Toggle(true)
        advanceUntilIdle()
        viewModel.onIpv6Toggle(false)
        advanceUntilIdle()

        assertEquals(listOf(true, false), repository.ipv6Updates)
    }

    @Test
    fun `onAutoStartToggle forwards to repository`() = runTest {
        viewModel.onAutoStartToggle(true)
        advanceUntilIdle()

        assertEquals(listOf(true), repository.autoStartUpdates)
    }

    @Test
    fun `onManualEngineSelect forwards engine and null clears`() = runTest {
        viewModel.onManualEngineSelect(EngineId.HYSTERIA2)
        advanceUntilIdle()
        viewModel.onManualEngineSelect(null)
        advanceUntilIdle()

        assertEquals(listOf(EngineId.HYSTERIA2, null), repository.manualEngineUpdates)
    }

    @Test
    fun `tor initial state is Installed when module already present`() {
        torClient = FakeSplitInstallClient(installed = setOf("dynamic_tor"))
        viewModel = SettingsViewModel(repository, torClient, coordinator)

        assertEquals(TorInstallUiState.Installed, viewModel.torInstall.value)
    }

    @Test
    fun `tor initial state is NotInstalled when module absent`() {
        assertEquals(TorInstallUiState.NotInstalled, viewModel.torInstall.value)
    }

    @Test
    fun `onInstallTor emits Installing percents and Installed on terminal`() = runTest {
        torClient.flow = flowOf(
            InstallResult.Installing(percent = 25),
            InstallResult.Installing(percent = 80),
            InstallResult.Installed,
        )

        viewModel.onInstallTor()
        advanceUntilIdle()

        assertEquals(TorInstallUiState.Installed, viewModel.torInstall.value)
        assertEquals(1, torClient.requestCount)
    }

    @Test
    fun `onInstallTor sets Failed when client emits Failed`() = runTest {
        torClient.flow = flowOf(
            InstallResult.Installing(percent = 30),
            InstallResult.Failed(reason = "code=-100"),
        )

        viewModel.onInstallTor()
        advanceUntilIdle()

        val state = viewModel.torInstall.value
        val failed = assertIs<TorInstallUiState.Failed>(state)
        assertEquals("code=-100", failed.reason)
    }

    @Test
    fun `onInstallTor propagates checksum mismatch reason from Failed`() = runTest {
        val reason = "checksum mismatch: libtor-arm64-v8a.so expected=39e1e4b1… actual=deadbeef…"
        torClient.flow = flowOf(
            InstallResult.Installing(percent = 90),
            InstallResult.Failed(reason = reason),
        )

        viewModel.onInstallTor()
        advanceUntilIdle()

        val failed = assertIs<TorInstallUiState.Failed>(viewModel.torInstall.value)
        assertEquals(reason, failed.reason)
    }

    @Test
    fun `onCancelTor cancels job and resets to NotInstalled`() = runTest {
        val gate = CompletableDeferred<InstallResult>()
        torClient.flow = flow {
            emit(InstallResult.Installing(percent = 10))
            // блок дальше до cancel
            emit(gate.await())
        }

        viewModel.onInstallTor()
        advanceUntilIdle()
        assertIs<TorInstallUiState.Installing>(viewModel.torInstall.value)

        viewModel.onCancelTor()
        advanceUntilIdle()

        assertEquals(TorInstallUiState.NotInstalled, viewModel.torInstall.value)
    }

    @Test
    fun `onInstallTor is idempotent when already Installed`() = runTest {
        torClient = FakeSplitInstallClient(installed = setOf("dynamic_tor"))
        viewModel = SettingsViewModel(repository, torClient, coordinator)

        viewModel.onInstallTor()
        advanceUntilIdle()

        assertEquals(TorInstallUiState.Installed, viewModel.torInstall.value)
        assertEquals(0, torClient.requestCount)
    }

    @Test
    fun `update initial state Idle`() = runTest {
        assertEquals(UpdateUiState.Idle, viewModel.update.value)
    }

    @Test
    fun `onCheckUpdate maps Checking and UpToDate`() = runTest {
        coordinator.script = flowOf(
            UpdateCoordinator.Progress.Checking,
            UpdateCoordinator.Progress.UpToDate,
        )
        viewModel.onCheckUpdate()
        advanceUntilIdle()
        assertEquals(UpdateUiState.UpToDate, viewModel.update.value)
    }

    @Test
    fun `onCheckUpdate maps Downloading percent`() = runTest {
        coordinator.script = flowOf(
            UpdateCoordinator.Progress.Checking,
            UpdateCoordinator.Progress.Downloading(42),
            UpdateCoordinator.Progress.Verifying,
            UpdateCoordinator.Progress.Installing,
            UpdateCoordinator.Progress.Submitted(sessionId = 1),
        )
        viewModel.onCheckUpdate()
        advanceUntilIdle()
        // Финал: Submitted маппится в Installing (system confirm-dialog ещё впереди).
        assertEquals(UpdateUiState.Installing, viewModel.update.value)
    }

    @Test
    fun `onCheckUpdate maps Failed reason includes stage`() = runTest {
        coordinator.script = flowOf(
            UpdateCoordinator.Progress.Checking,
            UpdateCoordinator.Progress.Failed(
                stage = UpdateCoordinator.Progress.Stage.VERIFY,
                reason = "подпись APK невалидна",
            ),
        )
        viewModel.onCheckUpdate()
        advanceUntilIdle()
        val failed = assertIs<UpdateUiState.Failed>(viewModel.update.value)
        assertEquals("VERIFY: подпись APK невалидна", failed.reason)
    }

    @Test
    fun `onCheckUpdate ignored when already in non-terminal state`() = runTest {
        val gate = CompletableDeferred<UpdateCoordinator.Progress>()
        coordinator.script = flow {
            emit(UpdateCoordinator.Progress.Checking)
            emit(gate.await())
        }
        viewModel.onCheckUpdate()
        advanceUntilIdle()
        assertEquals(UpdateUiState.Checking, viewModel.update.value)
        val callsBefore = coordinator.calls
        viewModel.onCheckUpdate()
        advanceUntilIdle()
        assertEquals(callsBefore, coordinator.calls, "повторный onCheckUpdate должен быть no-op")
    }

    @Test
    fun `onResetUpdate from Failed back to Idle`() = runTest {
        coordinator.script = flowOf(
            UpdateCoordinator.Progress.Checking,
            UpdateCoordinator.Progress.Failed(UpdateCoordinator.Progress.Stage.FETCH, "x"),
        )
        viewModel.onCheckUpdate()
        advanceUntilIdle()
        assertIs<UpdateUiState.Failed>(viewModel.update.value)
        viewModel.onResetUpdate()
        assertEquals(UpdateUiState.Idle, viewModel.update.value)
    }

    private class FakeUpdateCoordinator : UpdateCoordinator(
        fetcher = object : GithubReleaseFetcher("o", "r", OkHttpClient(), "http://x") {
            override fun latest() = null
        },
        downloader = object : ApkDownloader(OkHttpClient()) {
            override fun download(apkUrl: String, sigUrl: String, destDir: File) = emptyFlow<Event>()
        },
        verifier = object : ApkUpdateVerifier(ByteArray(32)) {
            override fun verify(apkFile: File, signatureFile: File): Boolean = false
        },
        installer = object : SilentPackageInstaller(
            mockk<Context>(relaxed = true), mockk<PackageInstaller>(relaxed = true),
        ) {
            override suspend fun install(
                apkFile: File,
                sessionName: String,
                resultIntentAction: String,
            ): Result = Result.FileError("test-stub")
        },
        currentVersion = "v0.1.0",
        cacheDir = File("/tmp"),
    ) {
        var script: Flow<Progress> = emptyFlow()
        var calls: Int = 0
            private set
        override fun check(): Flow<Progress> {
            calls += 1
            return script
        }
    }

    private class FakeSettingsRepository : SettingsRepository {
        private val flow = MutableStateFlow<SettingsModel?>(null)

        val splitModeUpdates = mutableListOf<SplitTunnelMode>()
        val ipv6Updates = mutableListOf<Boolean>()
        val autoStartUpdates = mutableListOf<Boolean>()
        val manualEngineUpdates = mutableListOf<EngineId?>()

        override val settings: Flow<SettingsModel> = kotlinx.coroutines.flow.flow {
            flow.collect { value ->
                if (value != null) emit(value)
            }
        }

        suspend fun emit(model: SettingsModel) {
            flow.value = model
        }

        override suspend fun setSplitMode(mode: SplitTunnelMode) {
            splitModeUpdates += mode
        }

        override suspend fun setIpv6Enabled(enabled: Boolean) {
            ipv6Updates += enabled
        }

        override suspend fun setAutoStart(enabled: Boolean) {
            autoStartUpdates += enabled
        }

        override suspend fun setManualEngine(engine: EngineId?) {
            manualEngineUpdates += engine
        }
    }

    private class FakeSplitInstallClient(
        installed: Set<String> = emptySet(),
    ) : SplitInstallClient {
        override val installedModules: Set<String> = installed
        var flow: Flow<InstallResult> = flowOf()
        var requestCount: Int = 0
            private set

        override fun requestInstall(moduleName: String): Flow<InstallResult> {
            requestCount += 1
            return flow
        }

        override suspend fun deferredUninstall(moduleName: String) = Unit
    }
}
