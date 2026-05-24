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
import ru.ozero.app.R
import ru.ozero.enginewarp.ImportedWarpConfig
import ru.ozero.enginewarp.RegisteredWarpConfig
import ru.ozero.enginewarp.WarpAutoConfig
import ru.ozero.enginewarp.WarpConfig
import ru.ozero.enginewarp.WarpConfigDuplicateException
import ru.ozero.enginewarp.WarpConfigSlot
import ru.ozero.enginewarp.DoHProvider
import ru.ozero.enginewarp.WarpConfigSlotStore
import ru.ozero.enginewarp.WarpEndpointProber
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
        vm = WarpEngineSettingsViewModel(store, auto, importer, FakeWarpEndpointProber())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init подхватывает слоты из store`() = runTest {
        store.addSlot("Test", SAMPLE)
        val freshVm = WarpEngineSettingsViewModel(store, FakeAutoConfig(), FakeFileImporter(), FakeWarpEndpointProber())
        advanceUntilIdle()
        assertEquals(1, freshVm.uiState.value.slots.size)
        assertEquals("Test", freshVm.uiState.value.slots[0].name)
    }

    @Test
    fun `init с пустым store — auto-trigger register ровно один раз`() = runTest {
        auto.setConfig(SAMPLE)
        advanceUntilIdle()
        assertEquals(1, auto.callCount, "При пустом store auto-register срабатывает (Bug #3)")
    }

    @Test
    fun `init с непустым store — auto-trigger НЕ срабатывает`() = runTest {
        store.addSlot("Existing", SAMPLE)
        val freshAuto = FakeAutoConfig()
        val freshVm = WarpEngineSettingsViewModel(store, freshAuto, FakeFileImporter(), FakeWarpEndpointProber())
        advanceUntilIdle()
        assertEquals(0, freshAuto.callCount, "Непустой store не должен триггерить auto-register")
        assertEquals(1, freshVm.uiState.value.slots.size)
    }

    @Test
    fun `auto-trigger respects cooldown — НЕ срабатывает пока active`() = runTest {
        val freshStore = FakeWarpStore()
        val freshAuto = FakeAutoConfig().apply { cooldownMs = 30_000L }
        WarpEngineSettingsViewModel(freshStore, freshAuto, FakeFileImporter(), FakeWarpEndpointProber())
        advanceUntilIdle()
        assertEquals(0, freshAuto.callCount, "Активный cooldown блокирует auto-trigger")
    }

    @Test
    fun `auto-trigger один раз — после fail повторное empty НЕ регистрит снова`() = runTest {
        auto.result = Result.failure<RegisteredWarpConfig>(IllegalStateException("network down"))
        advanceUntilIdle()
        assertEquals(1, auto.callCount, "Первый auto-trigger сработал и упал")
        val id = store.addSlot("Manual", SAMPLE)
        advanceUntilIdle()
        store.delete(id)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.slots.isEmpty())
        assertEquals(1, auto.callCount, "Повторное empty не должно повторять register")
    }

    @Test
    fun `migration fail — auto-trigger НЕ срабатывает`() = runTest {
        val throwingStore = object : FakeWarpStore() {
            override suspend fun migrateIfNeeded() = error("migration boom")
        }
        val freshAuto = FakeAutoConfig()
        val freshVm = WarpEngineSettingsViewModel(throwingStore, freshAuto, FakeFileImporter(), FakeWarpEndpointProber())
        advanceUntilIdle()
        assertEquals(0, freshAuto.callCount, "Migration fail блокирует auto-trigger")
        assertEquals("migration boom", freshVm.uiState.value.errorMessage)
    }

    @Test
    fun `auto-trigger при isRegistering=true (manual onGenerate уже идёт) — НЕ дублирует`() = runTest {
        auto.delayMs = 60_000L
        auto.setConfig(SAMPLE)
        vm.onGenerate()
        runCurrent()
        assertTrue(vm.uiState.value.isRegistering)
        advanceUntilIdle()
        assertEquals(1, auto.callCount, "isRegistering=true должно блокировать auto-trigger")
    }

    @Test
    fun `activeSlotId обновляется когда слот становится активным`() = runTest {
        val id = store.addSlot("Slot1", SAMPLE)
        advanceUntilIdle()
        assertEquals(id, vm.uiState.value.activeSlotId)
    }

    @Test
    fun `onGenerate success добавляет ровно один слот`() = runTest {
        auto.setConfig(SAMPLE)
        vm.onGenerate()
        advanceUntilIdle()
        assertEquals(1, store.slotCount())
    }

    @Test
    fun `onGenerate failure ставит errorMessageRes-hint и не добавляет слот`() = runTest {
        auto.result = Result.failure<RegisteredWarpConfig>(IllegalStateException("network down"))
        vm.onGenerate()
        advanceUntilIdle()
        val s = vm.uiState.value
        assertFalse(s.isRegistering)
        assertNull(s.errorMessage)
        assertEquals(ru.ozero.app.R.string.warp_register_error_hint, s.errorMessageRes)
        assertEquals(0, store.slotCount())
    }

    @Test
    fun `onGenerate failure без message — тоже errorMessageRes-hint`() = runTest {
        auto.result = Result.failure<RegisteredWarpConfig>(RuntimeException())
        vm.onGenerate()
        advanceUntilIdle()
        assertNull(vm.uiState.value.errorMessage)
        assertEquals(ru.ozero.app.R.string.warp_register_error_hint, vm.uiState.value.errorMessageRes)
    }

    @Test
    fun `onGenerate во время isRegistering=true игнорируется`() = runTest {
        auto.setConfig(SAMPLE)
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
        auto.setConfig(SAMPLE)
        vm.onGenerate()
        advanceUntilIdle()
        assertTrue(observed, "isRegistering обязан быть true пока auto.register выполняется")
    }

    @Test
    fun `onCancelGenerate отменяет register и сбрасывает isRegistering`() = runTest {
        auto.delayMs = 60_000L
        auto.setConfig(SAMPLE)
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
    fun `onImportFile success добавляет слот и увеличивает importSuccessCount`() = runTest {
        importer.setConfig(SAMPLE)
        vm.onImportFile(ByteArrayInputStream(ByteArray(0)))
        advanceUntilIdle()
        assertEquals(1, store.slotCount())
        assertNull(vm.uiState.value.errorMessage)
        assertEquals(1, vm.uiState.value.importSuccessCount)
    }

    @Test
    fun `onImportFile success называет слот Ozero-1`() = runTest {
        importer.setConfig(SAMPLE)
        vm.onImportFile(ByteArrayInputStream(ByteArray(0)))
        advanceUntilIdle()
        assertEquals("Ozero-1", vm.uiState.value.slots.first().name)
    }

    @Test
    fun `onImportFile второй слот называется Ozero-2`() = runTest {
        importer.setConfig(SAMPLE)
        vm.onImportFile(ByteArrayInputStream(ByteArray(0)))
        advanceUntilIdle()
        importer.setConfig(SAMPLE.copy(privateKey = "second-private-key"))
        vm.onImportFile(ByteArrayInputStream(ByteArray(0)))
        advanceUntilIdle()
        val names = vm.uiState.value.slots.map { it.name }.sorted()
        assertEquals(listOf("Ozero-1", "Ozero-2"), names)
    }

    @Test
    fun `onImportFile failure ставит errorMessage и не добавляет слот`() = runTest {
        importer.result = Result.failure<ImportedWarpConfig>(IOException("bad file"))
        vm.onImportFile(ByteArrayInputStream(ByteArray(0)))
        advanceUntilIdle()
        assertEquals("bad file", vm.uiState.value.errorMessage)
        assertEquals(0, store.slotCount())
    }

    @Test
    fun `onImportFile failure без message — fallback import failed`() = runTest {
        importer.result = Result.failure<ImportedWarpConfig>(RuntimeException())
        vm.onImportFile(ByteArrayInputStream(ByteArray(0)))
        advanceUntilIdle()
        assertEquals("import failed", vm.uiState.value.errorMessage)
    }

    @Test
    fun `onImportFile успешный дважды разными конфигами — importSuccessCount равен двум`() = runTest {
        importer.setConfig(SAMPLE)
        vm.onImportFile(ByteArrayInputStream(ByteArray(0)))
        advanceUntilIdle()
        importer.setConfig(SAMPLE.copy(privateKey = "second-private-key"))
        vm.onImportFile(ByteArrayInputStream(ByteArray(0)))
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.importSuccessCount)
    }

    @Test
    fun `onImportFile дубликата возвращает errorMessageRes warp_duplicate_hint`() = runTest {
        importer.setConfig(SAMPLE)
        vm.onImportFile(ByteArrayInputStream(ByteArray(0)))
        advanceUntilIdle()
        vm.onImportFile(ByteArrayInputStream(ByteArray(0)))
        advanceUntilIdle()
        assertEquals(1, store.slotCount(), "дубликат не должен добавляться")
        assertEquals(1, vm.uiState.value.importSuccessCount)
        assertEquals(R.string.warp_duplicate_hint, vm.uiState.value.errorMessageRes)
        assertNull(vm.uiState.value.errorMessage)
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
    fun `onSaveEdit с пустым privateKey — не сохраняет и ставит errorMessage`() = runTest {
        val id = store.addSlot("S", SAMPLE)
        advanceUntilIdle()
        vm.onStartEdit(id)
        vm.onEditDraftChange(vm.uiState.value.editDraft!!.copy(privateKey = ""))
        vm.onSaveEdit()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.editDraft, "draft должен оставаться открытым при ошибке валидации")
        assertNotNull(vm.uiState.value.errorMessageRes)
        assertNull(store.lastUpdateCall, "updateSlot не должен вызываться при невалидных данных")
    }

    @Test
    fun `onSaveEdit с пустым endpoint — не сохраняет`() = runTest {
        val id = store.addSlot("S", SAMPLE)
        advanceUntilIdle()
        vm.onStartEdit(id)
        vm.onEditDraftChange(vm.uiState.value.editDraft!!.copy(endpoint = "   "))
        vm.onSaveEdit()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.editDraft)
        assertNull(store.lastUpdateCall)
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
    fun `onSaveEdit сохраняет rawIniOverride из слота — не обнуляет`() = runTest {
        val rawIni = "[Interface]\nPrivateKey = priv\n\n[Peer]\nPublicKey = peer\n"
        val id = store.addSlot("S", SAMPLE, rawIni)
        advanceUntilIdle()
        vm.onStartEdit(id)
        vm.onSaveEdit()
        advanceUntilIdle()
        assertEquals(rawIni, store.lastUpdateRawIni, "rawIniOverride должен сохраняться при редактировании")
    }

    @Test
    fun `onSaveEdit для слота без rawIniOverride передаёт null`() = runTest {
        val id = store.addSlot("S", SAMPLE, null)
        advanceUntilIdle()
        vm.onStartEdit(id)
        vm.onSaveEdit()
        advanceUntilIdle()
        assertNull(store.lastUpdateRawIni)
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
    fun `onGenerate сбрасывает progressMirror после успеха`() = runTest {
        auto.setConfig(SAMPLE)
        auto.progressToEmit = "3/78"
        vm.onGenerate()
        advanceUntilIdle()
        assertNull(vm.uiState.value.progressMirror, "progressMirror сбрасывается после успеха")
    }

    @Test
    fun `onStartEdit открывает draft с doHProvider из слота`() = runTest {
        val id = store.addSlot("S", SAMPLE.copy(doHProvider = DoHProvider.GOOGLE_8888))
        advanceUntilIdle()
        vm.onStartEdit(id)
        assertEquals(DoHProvider.GOOGLE_8888, vm.uiState.value.editDraft?.doHProvider)
    }

    @Test
    fun `onSetDoHProvider обновляет doHProvider в draft`() = runTest {
        val id = store.addSlot("S", SAMPLE)
        advanceUntilIdle()
        vm.onStartEdit(id)
        vm.onSetDoHProvider(DoHProvider.CLOUDFLARE_1111)
        assertEquals(DoHProvider.CLOUDFLARE_1111, vm.uiState.value.editDraft?.doHProvider)
    }

    @Test
    fun `onSetDoHProvider без открытого draft — ничего не меняет`() = runTest {
        advanceUntilIdle()
        vm.onSetDoHProvider(DoHProvider.CLOUDFLARE_1111)
        assertNull(vm.uiState.value.editDraft)
    }

    @Test
    fun `onSaveEdit сохраняет doHProvider в WarpConfig`() = runTest {
        val id = store.addSlot("S", SAMPLE)
        advanceUntilIdle()
        vm.onStartEdit(id)
        vm.onSetDoHProvider(DoHProvider.ADGUARD)
        vm.onSaveEdit()
        advanceUntilIdle()
        assertEquals(DoHProvider.ADGUARD, store.lastUpdateCall?.third?.doHProvider)
    }

    @Test
    fun `selectedDoHProvider отражает doHProvider из draft`() = runTest {
        val id = store.addSlot("S", SAMPLE.copy(doHProvider = DoHProvider.GOOGLE_8844))
        advanceUntilIdle()
        vm.onStartEdit(id)
        advanceUntilIdle()
        assertEquals(DoHProvider.GOOGLE_8844, vm.selectedDoHProvider.value)
    }

    private open class FakeWarpStore : WarpConfigSlotStore {
        private val slotsFlow = MutableStateFlow<List<WarpConfigSlot>>(emptyList())
        val setActiveCalls = mutableListOf<String>()
        var lastUpdateCall: Triple<String, String, WarpConfig>? = null
        var lastUpdateRawIni: String? = null
        private var idCounter = 0

        fun slotCount() = slotsFlow.value.size

        override fun slots(): Flow<List<WarpConfigSlot>> = slotsFlow

        override fun activeSlot() = slotsFlow.map { list -> list.firstOrNull { it.isActive } }

        override fun activeConfig() = slotsFlow.map { list ->
            list.firstOrNull { it.isActive }?.config
        }

        var lastAddedRawIni: String? = null

        override suspend fun addSlot(name: String, config: WarpConfig, rawIni: String?, endpointList: List<String>): String {
            val fingerprint = listOf(config.privateKey, config.peerPublicKey, config.peerEndpoint)
            val duplicate = slotsFlow.value.firstOrNull {
                listOf(it.config.privateKey, it.config.peerPublicKey, it.config.peerEndpoint) == fingerprint
            }
            if (duplicate != null) {
                throw WarpConfigDuplicateException(duplicate.id, duplicate.name)
            }
            val id = "fake-${idCounter++}"
            val makeActive = slotsFlow.value.isEmpty()
            lastAddedRawIni = rawIni
            slotsFlow.value = slotsFlow.value + WarpConfigSlot(
                id = id,
                name = name,
                config = config,
                isActive = makeActive,
                rawIniOverride = rawIni,
                endpointList = endpointList,
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

        override suspend fun updateSlot(id: String, name: String, config: WarpConfig, rawIni: String?, endpointList: List<String>) {
            lastUpdateCall = Triple(id, name, config)
            lastUpdateRawIni = rawIni
            slotsFlow.value = slotsFlow.value.map {
                if (it.id == id) it.copy(name = name, config = config, rawIniOverride = rawIni, endpointList = endpointList) else it
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
        var result: Result<ImportedWarpConfig> = Result.failure(IllegalStateException("not-stubbed"))
        fun setConfig(cfg: WarpConfig, rawIni: String = "[Interface]\n[Peer]\n") {
            result = Result.success(ImportedWarpConfig(cfg, rawIni))
        }
        override fun import(stream: InputStream): Result<ImportedWarpConfig> = result
    }

    private class FakeWarpEndpointProber : WarpEndpointProber() {
        override suspend fun probe(endpoints: List<String>): List<ProbeResult> = emptyList()
    }

    private class FakeAutoConfig : WarpAutoConfig {
        var result: Result<RegisteredWarpConfig> = Result.failure(IllegalStateException("not-stubbed"))
        var callCount: Int = 0
        var beforeReturn: () -> Unit = {}
        var delayMs: Long = 0L
        var progressToEmit: String? = null
        var cooldownMs: Long = 0L
        fun setConfig(cfg: WarpConfig, rawIni: String = "[Interface]\n[Peer]\n") {
            result = Result.success(RegisteredWarpConfig(cfg, rawIni))
        }
        override suspend fun register(onProgress: ((String) -> Unit)?): Result<RegisteredWarpConfig> {
            callCount++
            beforeReturn()
            progressToEmit?.let { onProgress?.invoke(it) }
            if (delayMs > 0) delay(delayMs)
            return result
        }
        override fun remainingCooldownMs(): Long = cooldownMs
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
