package ru.ozero.app.ui.settings.engines

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.enginewarp.WarpAutoConfig
import ru.ozero.enginewarp.WarpConfig
import ru.ozero.enginewarp.WarpConfigStore
import ru.ozero.enginewarp.WarpFileImporter
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WarpEngineSettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var store: FakeWarpStore
    private lateinit var auto: FakeAutoConfig
    private lateinit var importer: FakeFileImporter
    private lateinit var vm: WarpEngineSettingsViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        store = FakeWarpStore()
        auto = FakeAutoConfig()
        importer = FakeFileImporter()
        vm = WarpEngineSettingsViewModel(store, auto, importer)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init без конфига auto-triggers register — сразу регистрируется при первом открытии`() = runTest {
        auto.result = Result.success(SAMPLE)
        advanceUntilIdle()
        assertEquals(
            1,
            auto.callCount,
            "Открытие screen с пустым cache → автоматический register",
        )
        assertEquals(SAMPLE, store.savedRaw, "Auto-register обязан сохранить config в store")
    }

    @Test
    fun `init подхватывает saved config из store без auto-trigger`() = runTest {
        val freshAuto = FakeAutoConfig()
        store.setRaw(SAMPLE)
        val freshVm = WarpEngineSettingsViewModel(store, freshAuto, FakeFileImporter())
        advanceUntilIdle()
        assertEquals(SAMPLE, freshVm.uiState.value.currentConfig)
        assertEquals(
            0,
            freshAuto.callCount,
            "Если config уже в store — auto-trigger НЕ срабатывает",
        )
    }

    @Test
    fun `auto-trigger запускается ровно один раз даже если store пушит null повторно`() = runTest {
        auto.result = Result.success(SAMPLE)
        advanceUntilIdle()
        assertEquals(1, auto.callCount)
        store.setRaw(null)
        advanceUntilIdle()
        assertEquals(
            1,
            auto.callCount,
            "Повторный null из store не должен повторно auto-trigger (avoid loop)",
        )
    }

    @Test
    fun `onGenerate success persist config + clears error`() = runTest {
        auto.result = Result.success(SAMPLE)
        vm.onGenerate()
        advanceUntilIdle()
        val s = vm.uiState.value
        assertFalse(s.isRegistering)
        assertNull(s.errorMessage)
        assertEquals(SAMPLE, store.savedRaw)
    }

    @Test
    fun `onGenerate failure ставит errorMessage и не persist`() = runTest {
        auto.result = Result.failure(IllegalStateException("network down"))
        vm.onGenerate()
        advanceUntilIdle()
        val s = vm.uiState.value
        assertFalse(s.isRegistering)
        assertEquals("network down", s.errorMessage)
        assertNull(store.savedRaw)
    }

    @Test
    fun `onGenerate failure без message — fallback register failed`() = runTest {
        auto.result = Result.failure(RuntimeException())
        vm.onGenerate()
        advanceUntilIdle()
        assertEquals("register failed", vm.uiState.value.errorMessage)
    }

    @Test
    fun `onGenerate во время isRegistering=true игнорируется (no-op)`() = runTest {
        auto.result = Result.success(SAMPLE)
        auto.callCount = 0
        vm.onGenerate()
        vm.onGenerate()
        vm.onGenerate()
        advanceUntilIdle()
        assertEquals(1, auto.callCount, "Параллельный onGenerate обязан игнорировать дубли")
    }

    @Test
    fun `onClear вызывает store_clear`() = runTest {
        store.setRaw(SAMPLE)
        vm.onClear()
        advanceUntilIdle()
        assertEquals(1, store.clearCalls)
    }

    @Test
    fun `onGenerate ставит isRegistering=true до завершения`() = runTest {
        var observed = false
        auto.beforeReturn = {
            observed = vm.uiState.value.isRegistering
        }
        auto.result = Result.success(SAMPLE)
        vm.onGenerate()
        advanceUntilIdle()
        assertTrue(observed, "isRegistering обязан быть true пока auto.register выполняется")
    }

    @Test
    fun `onCancelGenerate отменяет register и сбрасывает isRegistering`() = runTest {
        auto.delayMs = 60_000L
        auto.result = Result.success(SAMPLE)
        vm.onGenerate()
        runCurrent()
        assertTrue(vm.uiState.value.isRegistering, "после onGenerate isRegistering=true")
        vm.onCancelGenerate()
        runCurrent()
        assertFalse(vm.uiState.value.isRegistering, "после onCancelGenerate isRegistering=false")
        advanceTimeBy(120_000L)
        advanceUntilIdle()
        assertNull(store.savedRaw, "cancel прерывает суспендинг register — store.save не должен вызываться")
    }

    @Test
    fun `init продолжает реагировать на новые value из store_current()`() = runTest {
        advanceUntilIdle()
        assertNull(vm.uiState.value.currentConfig)
        store.setRaw(SAMPLE)
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.currentConfig)
        store.setRaw(null)
        advanceUntilIdle()
        assertNull(vm.uiState.value.currentConfig)
    }

    private class FakeWarpStore : WarpConfigStore {
        private val flow = MutableStateFlow<WarpConfig?>(null)
        var savedRaw: WarpConfig? = null
        var clearCalls: Int = 0
        fun setRaw(v: WarpConfig?) {
            flow.value = v
        }
        override fun current(): Flow<WarpConfig?> = flow
        override suspend fun save(config: WarpConfig) {
            savedRaw = config
            flow.value = config
        }
        override suspend fun clear() {
            clearCalls++
            flow.value = null
        }
    }

    @Test
    fun `onImportFile success сохраняет config и сбрасывает ошибку`() = runTest {
        importer.result = Result.success(SAMPLE)
        vm.onImportFile(ByteArrayInputStream(ByteArray(0)))
        advanceUntilIdle()
        assertEquals(SAMPLE, store.savedRaw)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `onImportFile failure ставит errorMessage`() = runTest {
        importer.result = Result.failure(IOException("bad file"))
        vm.onImportFile(ByteArrayInputStream(ByteArray(0)))
        advanceUntilIdle()
        assertEquals("bad file", vm.uiState.value.errorMessage)
        assertNull(store.savedRaw)
    }

    @Test
    fun `onImportFile failure без message — fallback import failed`() = runTest {
        importer.result = Result.failure(RuntimeException())
        vm.onImportFile(ByteArrayInputStream(ByteArray(0)))
        advanceUntilIdle()
        assertEquals("import failed", vm.uiState.value.errorMessage)
    }

    @Test
    fun `onImportFile success обновляет currentConfig через store`() = runTest {
        importer.result = Result.success(SAMPLE)
        vm.onImportFile(ByteArrayInputStream(ByteArray(0)))
        advanceUntilIdle()
        assertEquals(SAMPLE, vm.uiState.value.currentConfig)
    }

    private class FakeFileImporter : WarpFileImporter {
        var result: Result<WarpConfig> = Result.failure(IllegalStateException("not-stubbed"))
        override fun import(stream: InputStream): Result<WarpConfig> = result
    }

    private class FakeAutoConfig : WarpAutoConfig {
        var result: Result<WarpConfig> = Result.failure(IllegalStateException("not-stubbed"))
        var callCount: Int = 0
        var beforeReturn: () -> Unit = {}
        var delayMs: Long = 0L
        override suspend fun register(): Result<WarpConfig> {
            callCount++
            beforeReturn()
            if (delayMs > 0) delay(delayMs)
            return result
        }
    }

    private companion object {
        val SAMPLE = WarpConfig(
            privateKey = "priv",
            publicKey = "pub",
            peerPublicKey = "peer",
            peerEndpoint = "engage.cloudflareclient.com:2408",
            interfaceAddressV4 = "10.0.0.1",
            interfaceAddressV6 = "::/128",
            accountLicense = "LIC",
        )
    }
}
