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
    fun `availableBytes равно balanceBytes`() {
        val state = UrnetworkBalanceState(
            snapshot = sample(balance = 1_000L, pending = 100L, used = 200L),
            isLoading = false,
            lastError = null,
        )
        assertEquals(1_100L, state.availableBytes)
    }

    @Test
    fun `availableBytes 0 если balanceBytes отрицательный`() {
        val state = UrnetworkBalanceState(
            snapshot = sample(balance = -50L),
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
    fun `availableBytes включает reliabilityBonusBytes когда meanReliabilityWeight больше 0`() {
        val state = UrnetworkBalanceState(
            snapshot = sample(balance = 1_000L, pending = 0L, used = 0L),
            isLoading = false,
            lastError = null,
            meanReliabilityWeight = 0.1,
        )
        val expectedBonusBytes = (0.1 * 100.0 * 1024.0 * 1024.0 * 1024.0).toLong()
        assertEquals(1_000L + expectedBonusBytes, state.availableBytes)
        assertEquals(expectedBonusBytes, state.reliabilityBonusBytes)
        assertEquals(1_000L, state.baseBalanceBytes)
    }

    @Test
    fun `reliabilityBonusBytes ограничен 100 ГиБ cap`() {
        val state = UrnetworkBalanceState(
            snapshot = sample(),
            isLoading = false,
            lastError = null,
            meanReliabilityWeight = 5.0,
        )
        val cap = (100.0 * 1024.0 * 1024.0 * 1024.0).toLong()
        assertEquals(cap, state.reliabilityBonusBytes)
    }

    @Test
    fun `reliabilityBonusBytes равен 0 когда weight равен 0`() {
        val state = UrnetworkBalanceState(
            snapshot = sample(),
            isLoading = false,
            lastError = null,
            meanReliabilityWeight = 0.0,
        )
        assertEquals(0L, state.reliabilityBonusBytes)
    }

    @Test
    fun `прогресс бар total включает bonus — sentinel против регрессии`() {
        val cardSrc = locateSource(
            "app/src/main/java/ru/ozero/app/ui/urnetwork/UrnetworkBalanceCard.kt",
        )
        val text = cardSrc.readText(Charsets.UTF_8)
        kotlin.test.assertTrue(
            !text.contains("availableBytes = displayBalance"),
            "UrnetworkBalanceCard.kt передаёт raw balanceBytes в прогресс-бар, " +
                "вместо state.availableBytes (= baseBalance + reliabilityBonus). " +
                "Прогресс-бар должен соответствовать полю Доступно.",
        )
        kotlin.test.assertTrue(
            text.contains("availableBytes = available"),
            "UrnetworkBalanceCard.kt должен передавать state.availableBytes в прогресс-бар.",
        )
    }

    private fun locateSource(rel: String): java.io.File {
        var dir = java.io.File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(5) {
            val candidate = java.io.File(dir, rel)
            if (candidate.isFile) return candidate
            dir = dir.parentFile ?: return@repeat
        }
        error("$rel не найден от ${System.getProperty("user.dir")}")
    }

    @Test
    fun `availableBytes регрессия — должен суммировать bonus из weight 0_103 как ~10_3 GiB`() {
        val state = UrnetworkBalanceState(
            snapshot = sample(balance = 33_810_000_000L, pending = 0L, used = 0L),
            isLoading = false,
            lastError = null,
            meanReliabilityWeight = 0.103,
        )
        val bonusGib = state.reliabilityBonusBytes / (1024.0 * 1024.0 * 1024.0)
        kotlin.test.assertTrue(bonusGib in 10.0..10.6, "bonus ~10.3 GiB but got $bonusGib")
        val availableGb = state.availableBytes / 1_000_000_000.0
        kotlin.test.assertTrue(availableGb > 33.8, "available должно быть > 33.8 GB, было $availableGb")
    }

    @Test
    fun `refresh успех сохраняет meanReliabilityWeight из bridge`() = runTest {
        val bridge = FakeBridge(snapshot = sample(), reliability = 0.75)
        val repo = RealUrnetworkBalanceRepository(bridge)
        repo.refresh()
        assertEquals(0.75, repo.state.value.meanReliabilityWeight)
    }

    @Test
    fun `refresh успех сохраняет totalReferrals из bridge`() = runTest {
        val bridge = FakeBridge(snapshot = sample(), referrals = 3L)
        val repo = RealUrnetworkBalanceRepository(bridge)
        repo.refresh()
        assertEquals(3L, repo.state.value.totalReferrals)
    }

    @Test
    fun `reliability null при refresh сохраняет предыдущее значение`() = runTest {
        val bridge = FakeBridge(snapshot = sample(), reliability = 0.5)
        val repo = RealUrnetworkBalanceRepository(bridge)
        repo.refresh()
        assertEquals(0.5, repo.state.value.meanReliabilityWeight)
        bridge.reliability = null
        repo.refresh()
        assertEquals(0.5, repo.state.value.meanReliabilityWeight)
    }

    @Test
    fun `referrals null при refresh сохраняет предыдущее значение`() = runTest {
        val bridge = FakeBridge(snapshot = sample(), referrals = 2L)
        val repo = RealUrnetworkBalanceRepository(bridge)
        repo.refresh()
        assertEquals(2L, repo.state.value.totalReferrals)
        bridge.referrals = null
        repo.refresh()
        assertEquals(2L, repo.state.value.totalReferrals)
    }

    @Test
    fun `refresh ошибка balance сохраняет reliability и referrals`() = runTest {
        val bridge = FakeBridge(snapshot = sample(), reliability = 0.8, referrals = 5L)
        val repo = RealUrnetworkBalanceRepository(bridge)
        repo.refresh()
        bridge.throwOnNext = RuntimeException("fail")
        bridge.reliability = 0.9
        bridge.referrals = 6L
        repo.refresh()
        val s = repo.state.value
        assertNotNull(s.lastError)
        assertEquals(0.9, s.meanReliabilityWeight)
        assertEquals(6L, s.totalReferrals)
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

    private open class FakeBridge(
        var snapshot: SubscriptionBalanceSnapshot?,
        var reliability: Double? = null,
        var referrals: Long? = null,
    ) : UrnetworkSdkBridge {
        var callCount = 0
        var throwOnNext: Throwable? = null

        override suspend fun start(
            walletAddress: String,
            apiUrl: String,
            connectUrl: String,
            byClientJwt: String,
        ) = UrnetworkSdkBridge.StartResult.Success

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

        override suspend fun fetchNetworkReliability(): Double? = reliability

        override suspend fun fetchReferralCount(): Long? = referrals
    }
}
