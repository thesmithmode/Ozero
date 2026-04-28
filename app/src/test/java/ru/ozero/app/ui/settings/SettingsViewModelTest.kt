package ru.ozero.app.ui.settings

import android.content.Context
import android.content.pm.PackageInstaller
import io.mockk.mockk
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
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeSettingsRepository
    private lateinit var coordinator: FakeUpdateCoordinator
    private lateinit var viewModel: SettingsViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeSettingsRepository()
        coordinator = FakeUpdateCoordinator()
        viewModel = SettingsViewModel(repository, coordinator)
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
    fun `onManualEngineSelect forwards engine and null clears`() = runTest {
        viewModel.onManualEngineSelect(EngineId.HYSTERIA2)
        advanceUntilIdle()
        viewModel.onManualEngineSelect(null)
        advanceUntilIdle()
        assertEquals(listOf(EngineId.HYSTERIA2, null), repository.manualEngineUpdates)
    }

    @Test
    fun `update initial state Idle`() = runTest {
        assertEquals(UpdateUiState.Idle, viewModel.update.value)
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
            override suspend fun install(apkFile: File, sessionName: String, resultIntentAction: String): Result =
                Result.FileError("test-stub")
        },
        currentVersion = "v0.1.0",
        currentVersionCode = 1L,
        cacheDir = File("/tmp"),
    ) {
        var script: Flow<Progress> = emptyFlow()
        override fun check(): Flow<Progress> = script
    }

    private class FakeSettingsRepository : SettingsRepository {
        private val flow = MutableStateFlow<SettingsModel?>(null)
        val manualEngineUpdates = mutableListOf<EngineId?>()
        override val settings: Flow<SettingsModel> = flow {
            flow.collect { value -> if (value != null) emit(value) }
        }
        override suspend fun setSplitMode(mode: SplitTunnelMode) = Unit
        override suspend fun setIpv6Enabled(enabled: Boolean) = Unit
        override suspend fun setAutoStart(enabled: Boolean) = Unit
        override suspend fun setManualEngine(engine: EngineId?) { manualEngineUpdates += engine }
        override suspend fun setUrnetworkEnabled(enabled: Boolean) = Unit
        override suspend fun setUrnetworkJwt(jwt: String?) = Unit
    }
}
