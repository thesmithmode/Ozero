package ru.ozero.app.urnetwork

import com.bringyour.sdk.LocationsViewController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.engineurnetwork.UrnetworkSdkBridge
import ru.ozero.engineurnetwork.UrnetworkSdkBridge.SubscriptionBalanceSnapshot
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class UrnetworkBalanceRepositoryTest {

    @Test
    fun `state INITIAL до refresh`() = runTest {
        val repo = RealUrnetworkBalanceRepository(FakeBridge(snapshot = sample()))
        assertEquals(UrnetworkBalanceState.INITIAL, repo.state.value)
    }

    @Test
    fun `refresh успех обновляет snapshot и сбрасывает isLoading`() = runTest {
        val bridge = FakeBridge(snapshot = sample(balance = 1_000L, used = 200L))
        val repo = RealUrnetworkBalanceRepository(bridge)
        repo.refresh()
        val s = repo.state.value
        assertEquals(1_000L, s.snapshot?.balanceBytes)
        assertEquals(200L, s.snapshot?.usedBytes)
        assertEquals(false, s.isLoading)
        assertNull(s.lastError)
    }

    @Test
    fun `refresh ошибка записывает lastError и не теряет previous snapshot`() = runTest {
        val bridge = FakeBridge(snapshot = sample(balance = 500L))
        val repo = RealUrnetworkBalanceRepository(bridge)
        repo.refresh()
        bridge.throwOnNext = RuntimeException("boom")
        repo.refresh()
        val s = repo.state.value
        assertEquals(500L, s.snapshot?.balanceBytes)
        assertEquals("boom", s.lastError)
        assertEquals(false, s.isLoading)
    }

    @Test
    fun `refresh ошибка без message использует имя класса`() = runTest {
        val bridge = FakeBridge(snapshot = sample())
        bridge.throwOnNext = object : RuntimeException() {}
        val repo = RealUrnetworkBalanceRepository(bridge)
        repo.refresh()
        assertNotNull(repo.state.value.lastError)
    }

    @Test
    fun `refresh null snapshot сохраняется как null без error`() = runTest {
        val bridge = FakeBridge(snapshot = null)
        val repo = RealUrnetworkBalanceRepository(bridge)
        repo.refresh()
        val s = repo.state.value
        assertNull(s.snapshot)
        assertNull(s.lastError)
    }

    @Test
    fun `availableBytes balance минус pending минус used`() {
        val state = UrnetworkBalanceState(
            snapshot = sample(balance = 1_000L, pending = 100L, used = 200L),
            isLoading = false,
            lastError = null,
        )
        assertEquals(700L, state.availableBytes)
    }

    @Test
    fun `availableBytes отрицательное coerced в 0`() {
        val state = UrnetworkBalanceState(
            snapshot = sample(balance = 100L, pending = 50L, used = 100L),
            isLoading = false,
            lastError = null,
        )
        assertEquals(0L, state.availableBytes)
    }

    @Test
    fun `availableBytes null snapshot вернёт 0`() {
        assertEquals(0L, UrnetworkBalanceState.INITIAL.availableBytes)
    }

    @Test
    fun `concurrent refresh serialized через mutex`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val sequence = mutableListOf<String>()
        val bridge = object : FakeBridge(snapshot = sample()) {
            var n = 0
            override suspend fun fetchSubscriptionBalance(): SubscriptionBalanceSnapshot? {
                val idx = ++n
                sequence.add("start-$idx")
                if (idx == 1) {
                    gate.await()
                }
                sequence.add("end-$idx")
                return snapshot
            }
        }
        val repo = RealUrnetworkBalanceRepository(bridge)
        val j1 = launch { repo.refresh() }
        val j2 = launch { repo.refresh() }
        runCurrent()
        assertEquals(listOf("start-1"), sequence)
        gate.complete(Unit)
        j1.join()
        j2.join()
        assertEquals(listOf("start-1", "end-1", "start-2", "end-2"), sequence)
    }

    @Test
    fun `refresh выставляет isLoading true в процессе fetch`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val bridge = object : FakeBridge(snapshot = sample()) {
            override suspend fun fetchSubscriptionBalance(): SubscriptionBalanceSnapshot? {
                gate.await()
                return snapshot
            }
        }
        val repo = RealUrnetworkBalanceRepository(bridge)
        val deferred = async { repo.refresh() }
        runCurrent()
        assertEquals(true, repo.state.value.isLoading)
        gate.complete(Unit)
        deferred.await()
        assertEquals(false, repo.state.value.isLoading)
    }

    @Test
    fun `lastError очищается на следующем успешном refresh`() = runTest {
        val bridge = FakeBridge(snapshot = sample())
        bridge.throwOnNext = RuntimeException("first-fail")
        val repo = RealUrnetworkBalanceRepository(bridge)
        repo.refresh()
        assertEquals("first-fail", repo.state.value.lastError)
        repo.refresh()
        assertNull(repo.state.value.lastError)
        assertNotNull(repo.state.value.snapshot)
    }

    private fun sample(
        balance: Long = 1_000_000L,
        pending: Long = 0L,
        startBalance: Long = 1_000_000L,
        used: Long = 0L,
        plan: String? = "Free",
        store: String? = null,
    ) = SubscriptionBalanceSnapshot(
        balanceBytes = balance,
        pendingBytes = pending,
        startBalanceBytes = startBalance,
        usedBytes = used,
        plan = plan,
        store = store,
    )

    private open class FakeBridge(var snapshot: SubscriptionBalanceSnapshot?) : UrnetworkSdkBridge {
        var callCount = 0
        var throwOnNext: Throwable? = null

        override suspend fun start(walletAddress: String, apiUrl: String, connectUrl: String, byClientJwt: String) =
            UrnetworkSdkBridge.StartResult.Success
        override suspend fun stop() = Unit
        override fun isRunning() = false
        override suspend fun attachTun(tunFd: Int) = UrnetworkSdkBridge.AttachResult.Success
        override fun connectTo(location: UrnetworkSdkBridge.LocationToken) = Unit
        override fun connectBestAvailable() = Unit
        override fun selectedLocation(): UrnetworkSdkBridge.LocationToken? = null
        override fun openLocationsViewController(): LocationsViewController? = null
        override fun setProvidePaused(paused: Boolean) = Unit
        override fun isProvidePaused() = false
        override fun peerCount() = 0
        override fun unpaidByteCount(): Long = 0L
        override fun fetchTransferStats() = Unit
        override suspend fun fetchSubscriptionBalance(): SubscriptionBalanceSnapshot? {
            callCount++
            val err = throwOnNext
            throwOnNext = null
            err?.let { throw it }
            return snapshot
        }
    }
}
