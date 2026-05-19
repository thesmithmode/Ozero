package ru.ozero.app.ui.settings.engines

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.app.urnetwork.UrnetworkBalanceRepository
import ru.ozero.app.urnetwork.UrnetworkBalanceState
import ru.ozero.engineurnetwork.UrnetworkProvideControlMode
import ru.ozero.engineurnetwork.UrnetworkProvideNetworkMode
import ru.ozero.engineurnetwork.UrnetworkSdkBridge
import ru.ozero.engineurnetwork.UrnetworkWindowType
import ru.ozero.engineurnetwork.allowDirect
import ru.ozero.engineurnetwork.fixedIpSize
import ru.ozero.engineurnetwork.provideControlMode
import ru.ozero.engineurnetwork.provideNetworkMode
import ru.ozero.engineurnetwork.setAllowDirect
import ru.ozero.engineurnetwork.setFixedIpSize
import ru.ozero.engineurnetwork.windowType
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    private fun vm(
        bridge: FakeUrnetworkBridge = FakeUrnetworkBridge(),
        store: ru.ozero.engineurnetwork.UrnetworkConfigStore = fakeUrnetworkConfigStore(),
        tunnel: ru.ozero.commonvpn.TunnelController = idleTunnel(),
        balanceRepo: UrnetworkBalanceRepository = fakeBalanceRepo(),
    ) = UrnetworkEngineSettingsViewModel(bridge, store, tunnel, balanceRepo)

    @Test
    fun `selectWindowType сохраняет в configStore и применяет profile через bridge`() = runTest {
        val bridge = FakeUrnetworkBridge()
        val store = fakeUrnetworkConfigStore()
        val v = vm(bridge = bridge, store = store, tunnel = activeTunnel())
        v.selectWindowType(UrnetworkWindowType.SPEED)
        advanceUntilIdle()
        assertEquals(UrnetworkWindowType.SPEED, store.windowType().first())
        assertEquals(UrnetworkWindowType.SPEED, bridge.lastAppliedWindowType)
    }

    @Test
    fun `selectWindowType AUTO сохраняет fixedIpSize — пользователь сам решает`() = runTest {
        val bridge = FakeUrnetworkBridge()
        val store = fakeUrnetworkConfigStore()
        store.setFixedIpSize(true)
        val v = vm(bridge = bridge, store = store, tunnel = activeTunnel())
        v.selectWindowType(UrnetworkWindowType.AUTO)
        advanceUntilIdle()
        assertEquals(true, store.fixedIpSize().first())
    }

    @Test
    fun `toggleFixedIpSize сохраняет в configStore и применяет profile`() = runTest {
        val bridge = FakeUrnetworkBridge()
        val store = fakeUrnetworkConfigStore()
        val v = vm(bridge = bridge, store = store, tunnel = activeTunnel())
        v.toggleFixedIpSize(true)
        advanceUntilIdle()
        assertEquals(true, store.fixedIpSize().first())
        assertEquals(true, bridge.lastAppliedFixedIp)
    }

    @Test
    fun `toggleAllowDirect false сохраняет в configStore и применяет profile`() = runTest {
        val bridge = FakeUrnetworkBridge()
        val store = fakeUrnetworkConfigStore()
        val v = vm(bridge = bridge, store = store, tunnel = activeTunnel())
        v.toggleAllowDirect(false)
        advanceUntilIdle()
        assertEquals(false, store.allowDirect().first())
        assertEquals(false, bridge.lastAppliedAllowDirect)
    }

    @Test
    fun `toggleAllowDirect true возвращает direct connections`() = runTest {
        val bridge = FakeUrnetworkBridge()
        val store = fakeUrnetworkConfigStore()
        store.setAllowDirect(false)
        val v = vm(bridge = bridge, store = store, tunnel = activeTunnel())
        v.toggleAllowDirect(true)
        advanceUntilIdle()
        assertEquals(true, store.allowDirect().first())
        assertEquals(true, bridge.lastAppliedAllowDirect)
    }

    @Test
    fun `toggleAllowDirect не вызывает bridge при idle engine`() = runTest {
        val bridge = FakeUrnetworkBridge()
        val store = fakeUrnetworkConfigStore()
        val v = vm(bridge = bridge, store = store)
        v.toggleAllowDirect(false)
        advanceUntilIdle()
        assertEquals(false, store.allowDirect().first())
        assertEquals(null, bridge.lastAppliedAllowDirect, "bridge не должен трогаться при idle engine")
    }

    @Test
    fun `allowDirect StateFlow отражает значение из configStore`() = runTest {
        val store = fakeUrnetworkConfigStore()
        store.setAllowDirect(false)
        val v = vm(store = store)
        advanceUntilIdle()
        assertEquals(false, v.allowDirect.value)
    }

    @Test
    fun `selectProvideControlMode сохраняет в configStore и применяет к bridge при active engine`() = runTest {
        val bridge = FakeUrnetworkBridge()
        val store = fakeUrnetworkConfigStore()
        val v = vm(bridge = bridge, store = store, tunnel = activeTunnel())
        v.selectProvideControlMode(UrnetworkProvideControlMode.AUTO)
        advanceUntilIdle()
        assertEquals(UrnetworkProvideControlMode.AUTO, store.provideControlMode().first())
        assertEquals(UrnetworkProvideControlMode.AUTO, bridge.lastProvideControlMode)
    }

    @Test
    fun `selectProvideControlMode не вызывает bridge при idle engine — только persist в store`() = runTest {
        val bridge = FakeUrnetworkBridge()
        val store = fakeUrnetworkConfigStore()
        val v = vm(bridge = bridge, store = store)
        v.selectProvideControlMode(UrnetworkProvideControlMode.AUTO)
        advanceUntilIdle()
        assertEquals(UrnetworkProvideControlMode.AUTO, store.provideControlMode().first())
        assertNull(bridge.lastProvideControlMode, "bridge не должен трогаться при idle engine")
    }

    @Test
    fun `selectProvideNetworkMode сохраняет в configStore и применяет к bridge при active engine`() = runTest {
        val bridge = FakeUrnetworkBridge()
        val store = fakeUrnetworkConfigStore()
        val v = vm(bridge = bridge, store = store, tunnel = activeTunnel())
        v.selectProvideNetworkMode(UrnetworkProvideNetworkMode.ALL)
        advanceUntilIdle()
        assertEquals(UrnetworkProvideNetworkMode.ALL, store.provideNetworkMode().first())
        assertEquals(UrnetworkProvideNetworkMode.ALL, bridge.lastProvideNetworkMode)
    }

    @Test
    fun `selectProvideNetworkMode не вызывает bridge при idle engine — только persist в store`() = runTest {
        val bridge = FakeUrnetworkBridge()
        val store = fakeUrnetworkConfigStore()
        val v = vm(bridge = bridge, store = store)
        v.selectProvideNetworkMode(UrnetworkProvideNetworkMode.ALL)
        advanceUntilIdle()
        assertEquals(UrnetworkProvideNetworkMode.ALL, store.provideNetworkMode().first())
        assertNull(bridge.lastProvideNetworkMode, "bridge не должен трогаться при idle engine")
    }

    @Test
    fun `selectWindowType не вызывает bridge applyPerformanceProfile когда engine не активен`() = runTest {
        val bridge = FakeUrnetworkBridge()
        val store = fakeUrnetworkConfigStore()
        val v = vm(bridge = bridge, store = store)
        v.selectWindowType(UrnetworkWindowType.SPEED)
        advanceUntilIdle()
        assertEquals(UrnetworkWindowType.SPEED, store.windowType().first())
        assertNull(bridge.lastAppliedWindowType)
    }

    @Test
    fun `windowType StateFlow отражает начальное значение из configStore`() = runTest {
        val v = vm()
        advanceUntilIdle()
        assertEquals(UrnetworkWindowType.AUTO, v.windowType.value)
    }

    @Test
    fun `sentinel — CheckIpRow открывает ur_io_ip через LocalUriHandler`() {
        val source = java.io.File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/app/ui/settings/engines/UrnetworkEngineSettingsScreen.kt",
        ).readText()
        val checkIpBlock = source
            .substringAfter("private fun CheckIpRow()")
            .substringBefore("\nprivate fun ")
        assertTrue(checkIpBlock.contains("openUri"), "CheckIpRow обязан вызывать openUri")
        assertTrue(checkIpBlock.contains("https://ur.io/ip"), "CheckIpRow обязан передавать https://ur.io/ip")
        assertTrue(checkIpBlock.contains("LocalUriHandler"), "CheckIpRow обязан использовать LocalUriHandler")
    }

    @Test
    fun `balanceState стартует с INITIAL`() = runTest {
        val v = vm()
        val collector = backgroundScope.launch { v.balanceState.collect { } }
        advanceTimeBy(50L)
        runCurrent()
        assertEquals(UrnetworkBalanceState.INITIAL, v.balanceState.value)
        collector.cancel()
    }

    @Test
    fun `balanceState отражает значение из balanceRepository`() = runTest {
        val snapshot = UrnetworkSdkBridge.SubscriptionBalanceSnapshot(
            balanceBytes = 1_000_000L,
            usedBytes = 200_000L,
            pendingBytes = 0L,
            startBalanceBytes = 1_000_000L,
            plan = "free",
            store = null,
        )
        val repo = FakeBalanceRepository(
            UrnetworkBalanceState(snapshot = snapshot, isLoading = false, lastError = null),
        )
        val v = vm(balanceRepo = repo)
        val collector = backgroundScope.launch { v.balanceState.collect { } }
        advanceTimeBy(50L)
        runCurrent()
        assertEquals(snapshot, v.balanceState.value.snapshot)
        collector.cancel()
    }

    @Test
    fun `balanceState вызывает refresh через 30s — sentinel auto-poll`() = runTest {
        val repo = FakeBalanceRepository()
        val v = vm(balanceRepo = repo)
        val collector = backgroundScope.launch { v.balanceState.collect { } }
        advanceTimeBy(100L)
        runCurrent()
        val initialRefreshes = repo.refreshCallCount.get()
        advanceTimeBy(30_000L)
        runCurrent()
        assertTrue(
            repo.refreshCallCount.get() > initialRefreshes,
            "через 30s должен быть повторный refresh",
        )
        collector.cancel()
    }
}

private class FakeBalanceRepository(
    initial: UrnetworkBalanceState = UrnetworkBalanceState.INITIAL,
) : UrnetworkBalanceRepository {
    private val _state = MutableStateFlow(initial)
    override val state: StateFlow<UrnetworkBalanceState> = _state.asStateFlow()
    val refreshCallCount = AtomicInteger(0)
    override suspend fun refresh() {
        refreshCallCount.incrementAndGet()
    }
}

private fun fakeBalanceRepo() = FakeBalanceRepository()
