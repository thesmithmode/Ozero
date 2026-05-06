package ru.ozero.app.ui.settings.engines

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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
import ru.ozero.enginewarp.AwgParams
import ru.ozero.enginewarp.WarpAutoConfig
import ru.ozero.enginewarp.WarpConfig
import ru.ozero.enginewarp.WarpConfigSlot
import ru.ozero.enginewarp.WarpConfigSlotStore
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
    fun `init подхватывает слоты из store`() = runTest {
        store.addSlot("Test", SAMPLE)
        val freshVm = WarpEngineSettingsViewModel(store, FakeAutoConfig(), FakeFileImporter())
        advanceUntilIdle()
        assertEquals(1, freshVm.uiState.value.slots.size)
        assertEquals("Test", freshVm.uiState.value.slots[0].name)
    }

    @Test
    fun `init с пустым store — нет auto-trigger`() = runTest {
        advanceUntilIdle()
        assertEquals(0, auto.callCount, "При пустом store auto-register НЕ срабатывает")
        assertTrue(vm.uiState.value.slots.isEmpty())
    }

    @Test
    fun `activeSlotId обновляется когда слот становится активным`() = runTest {
        val id = store.addSlot("Slot1", SAMPLE)
        advanceUntilIdle()
        assertEquals(id, vm.uiState.value.activeSlotId)
    }

    @Test
    fun `onGenerate success добавляет ровно один слот`() = runTest {
        auto.result = Result.success(SAMPLE)
        vm.onGenerate()
        advanceUntilIdle()
        assertEquals(1, store.slotCount())
    }

    @Test
    fun `onGenerate failure ставит errorMessage и не добавляет слот`() = runTest {
        auto.result = Result.failure(IllegalStateException("network down"))
        vm.onGenerate()
        advanceUntilIdle()
        val s = vm.uiState.value
        assertFalse(s.isRegistering)
        assertEquals("network down", s.errorMessage)
        assertEquals(0, store.slotCount())
    }

    @Test
    fun `onGenerate failure без message — fallback register failed`() = runTest {
        auto.result = Result.failure(RuntimeException())
        vm.onGenerate()
        advanceUntilIdle()
        assertEquals("register failed", vm.uiState.value.errorMessage)
    }

    @Test
    fun `onGenerate во время isRegistering=true игнорируется`() = runTest {
        auto.result = Result.success(SAMPLE)
        auto.callCount = 0
        vm.onGenerate()
        vm.onGenerate()
        vm.onGenerate()
        advanceUntilIdle()
        assertEquals(1, auto.callCount, "Параллельный onGenerate обязан игнорировать дубли")
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
        assertTrue(vm.uiState.value.isRegistering)
        vm.onCancelGenerate()
        runCurrent()
        assertFalse(vm.uiState.value.isRegistering)
        advanceTimeBy(120_000L)
        advanceUntilIdle()
        assertEquals(0, store.slotCount(), "cancel прерывает register — слот не добавляется")
    }

    @Test
    fun `slots обновляется в реальном времени через store`() = runTest {
        advanceUntilIdle()
        assertEquals(0, vm.uiState.value.slots.size)
        store.addSlot("A", SAMPLE)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.slots.size)
    }

    @Test
    fun `onImportFile success добавляет слот и ставит importSuccess`() = runTest {
        importer.result = Result.success(SAMPLE)
        vm.onImportFile(ByteArrayInputStream(ByteArray(0)))
        advanceUntilIdle()
        assertEquals(1, store.slotCount())
        assertNull(vm.uiState.value.errorMessage)
        assertTrue(vm.uiState.value.importSuccess)
    }

    @Test
    fun `onImportFile failure ставит errorMessage и не добавляет слот`() = runTest {
        importer.result = Result.failure(IOException("bad file"))
        vm.onImportFile(ByteArrayInputStream(ByteArray(0)))
        advanceUntilIdle()
        assertEquals("bad file", vm.uiState.value.errorMessage)
        assertEquals(0, store.slotCount())
    }

    @Test
    fun `onImportFile failure без message — fallback import failed`() = runTest {
        importer.result = Result.failure(RuntimeException())
        vm.onImportFile(ByteArrayInputStream(ByteArray(0)))
        advanceUntilIdle()
        assertEquals("import failed", vm.uiState.value.errorMessage)
    }

    @Test
    fun `onImportSuccessConsumed сбрасывает importSuccess`() = runTest {
        importer.result = Result.success(SAMPLE)
        vm.onImportFile(ByteArrayInputStream(ByteArray(0)))
        advanceUntilIdle()
        vm.onImportSuccessConsumed()
        assertFalse(vm.uiState.value.importSuccess)
    }

    @Test
    fun `onSetActive делегирует в store`() = runTest {
        val id1 = store.addSlot("A", SAMPLE)
        val id2 = store.addSlot("B", SAMPLE.copy(privateKey = "p2"))
        advanceUntilIdle()
        assertEquals(id1, vm.uiState.value.activeSlotId)
        vm.onSetActive(id2)
        advanceUntilIdle()
        assertEquals(id2, vm.uiState.value.activeSlotId)
        assertEquals(id2, store.setActiveCalls.lastOrNull())
    }

    @Test
    fun `onDeleteSlot делегирует в store`() = runTest {
        val id = store.addSlot("A", SAMPLE)
        advanceUntilIdle()
        vm.onDeleteSlot(id)
        advanceUntilIdle()
        assertEquals(0, store.slotCount())
    }

    @Test
    fun `onStartEdit открывает draft с полями слота`() = runTest {
        val id = store.addSlot("MySlot", SAMPLE)
        advanceUntilIdle()
        vm.onStartEdit(id)
        val draft = vm.uiState.value.editDraft
        assertNotNull(draft)
        assertEquals(id, draft.slotId)
        assertEquals("MySlot", draft.name)
        assertEquals(SAMPLE.peerEndpoint, draft.endpoint)
        assertEquals(SAMPLE.privateKey, draft.privateKey)
        assertEquals(SAMPLE.interfaceAddressV4, draft.addressV4)
    }

    @Test
    fun `onStartEdit с неизвестным id — draft не открывается`() = runTest {
        advanceUntilIdle()
        vm.onStartEdit("unknown-id")
        assertNull(vm.uiState.value.editDraft)
    }

    @Test
    fun `onEditCancel закрывает draft`() = runTest {
        val id = store.addSlot("X", SAMPLE)
        advanceUntilIdle()
        vm.onStartEdit(id)
        assertNotNull(vm.uiState.value.editDraft)
        vm.onEditCancel()
        assertNull(vm.uiState.value.editDraft)
    }

    @Test
    fun `onEditDraftChange обновляет draft`() = runTest {
        val id = store.addSlot("X", SAMPLE)
        advanceUntilIdle()
        vm.onStartEdit(id)
        val draft = vm.uiState.value.editDraft!!
        vm.onEditDraftChange(draft.copy(name = "NewName", endpoint = "1.2.3.4:51820"))
        val updated = vm.uiState.value.editDraft!!
        assertEquals("NewName", updated.name)
        assertEquals("1.2.3.4:51820", updated.endpoint)
    }

    @Test
    fun `onSaveEdit вызывает store_updateSlot и закрывает draft`() = runTest {
        val id = store.addSlot("Old", SAMPLE)
        advanceUntilIdle()
        vm.onStartEdit(id)
        vm.onEditDraftChange(vm.uiState.value.editDraft!!.copy(name = "New", endpoint = "9.9.9.9:2408"))
        vm.onSaveEdit()
        advanceUntilIdle()
        assertNull(vm.uiState.value.editDraft)
        val call = store.lastUpdateCall
        assertNotNull(call)
        assertEquals(id, call.first)
        assertEquals("New", call.second)
        assertEquals("9.9.9.9:2408", call.third.peerEndpoint)
    }

    @Test
    fun `onSaveEdit с невалидным MTU — использует DEFAULT_MTU`() = runTest {
        val id = store.addSlot("S", SAMPLE)
        advanceUntilIdle()
        vm.onStartEdit(id)
        vm.onEditDraftChange(vm.uiState.value.editDraft!!.copy(mtu = "not-a-number"))
        vm.onSaveEdit()
        advanceUntilIdle()
        assertEquals(WarpConfig.DEFAULT_MTU, store.lastUpdateCall?.third?.mtu)
    }

    @Test
    fun `onSaveEdit нормализует jmin_jmax — min всегда не больше max`() = runTest {
        val id = store.addSlot("S", SAMPLE)
        advanceUntilIdle()
        vm.onStartEdit(id)
        vm.onEditDraftChange(vm.uiState.value.editDraft!!.copy(jmin = "300", jmax = "100"))
        vm.onSaveEdit()
        advanceUntilIdle()
        val awg = store.lastUpdateCall?.third?.awgParams!!
        assertTrue(awg.junkPacketMinSize <= awg.junkPacketMaxSize)
        assertEquals(100, awg.junkPacketMinSize)
        assertEquals(300, awg.junkPacketMaxSize)
    }

    @Test
    fun `onSaveEdit с пустым именем — fallback WARP`() = runTest {
        val id = store.addSlot("S", SAMPLE)
        advanceUntilIdle()
        vm.onStartEdit(id)
        vm.onEditDraftChange(vm.uiState.value.editDraft!!.copy(name = "   "))
        vm.onSaveEdit()
        advanceUntilIdle()
        assertEquals("WARP", store.lastUpdateCall?.second)
    }

    @Test
    fun `onSaveEdit с пустым DNS — использует DEFAULT_DNS`() = runTest {
        val id = store.addSlot("S", SAMPLE)
        advanceUntilIdle()
        vm.onStartEdit(id)
        vm.onEditDraftChange(vm.uiState.value.editDraft!!.copy(dns = "   "))
        vm.onSaveEdit()
        advanceUntilIdle()
        assertEquals(WarpConfig.DEFAULT_DNS, store.lastUpdateCall?.third?.dnsServers)
    }

    @Test
    fun `init — ошибка миграции — errorMessage установлен`() = runTest {
        val throwingStore = object : FakeWarpStore() {
            override suspend fun migrateIfNeeded() = error("migration boom")
        }
        val vm = WarpEngineSettingsViewModel(throwingStore, FakeAutoConfig(), FakeFileImporter())
        advanceUntilIdle()
        assertEquals("migration boom", vm.uiState.value.errorMessage)
    }

    @Test
    fun `onGenerate передаёт прогресс через progressText`() = runTest {
        auto.result = Result.success(SAMPLE)
        auto.progressToEmit = "3/78"
        vm.onGenerate()
        advanceUntilIdle()
        assertNull(vm.uiState.value.progressText, "progressText сбрасывается после успеха")
    }

    private open class FakeWarpStore : WarpConfigSlotStore {
        private val slotsFlow = MutableStateFlow<List<WarpConfigSlot>>(emptyList())
        val setActiveCalls = mutableListOf<String>()
        var lastUpdateCall: Triple<String, String, WarpConfig>? = null
        private var idCounter = 0

        fun slotCount() = slotsFlow.value.size

        override fun slots(): Flow<List<WarpConfigSlot>> = slotsFlow

        override fun activeConfig() = slotsFlow.map { list ->
            list.firstOrNull { it.isActive }?.config
        }

        override suspend fun addSlot(name: String, config: WarpConfig): String {
            val id = "fake-${idCounter++}"
            val makeActive = slotsFlow.value.isEmpty()
            slotsFlow.value = slotsFlow.value + WarpConfigSlot(
                id = id, name = name, config = config, isActive = makeActive,
            )
            return id
        }

        override suspend fun setActive(id: String) {
            setActiveCalls.add(id)
            slotsFlow.value = slotsFlow.value.map { it.copy(isActive = it.id == id) }
        }

        override suspend fun rename(id: String, name: String) {
            slotsFlow.value = slotsFlow.value.map { if (it.id == id) it.copy(name = name) else it }
        }

        override suspend fun updateSlot(id: String, name: String, config: WarpConfig) {
            lastUpdateCall = Triple(id, name, config)
            slotsFlow.value = slotsFlow.value.map {
                if (it.id == id) it.copy(name = name, config = config) else it
            }
        }

        override suspend fun delete(id: String) {
            slotsFlow.value = slotsFlow.value.filter { it.id != id }
        }

        override suspend fun clear() {
            slotsFlow.value = emptyList()
        }

        override suspend fun replaceAll(slots: List<WarpConfigSlot>) {
            slotsFlow.value = slots
        }
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
        var progressToEmit: String? = null
        override suspend fun register(onProgress: ((String) -> Unit)?): Result<WarpConfig> {
            callCount++
            beforeReturn()
            progressToEmit?.let { onProgress?.invoke(it) }
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
