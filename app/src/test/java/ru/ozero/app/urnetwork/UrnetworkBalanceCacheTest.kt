package ru.ozero.app.urnetwork

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.engineurnetwork.UrnetworkSdkBridge
import ru.ozero.engineurnetwork.UrnetworkSdkBridge.SubscriptionBalanceSnapshot
import com.bringyour.sdk.LocationsViewController
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class UrnetworkBalanceCacheTest {

    @Test
    fun `optimistic — initial state из cache если есть snapshot`() {
        val cached = SubscriptionBalanceSnapshot(
            balanceBytes = 5_000L,
            pendingBytes = 100L,
            startBalanceBytes = 10_000L,
            usedBytes = 4_900L,
            plan = "Free",
            store = null,
        )
        val cache = InMemoryCache().apply { save(cached, 0.42, 7L) }
        val repo = RealUrnetworkBalanceRepository(FakeBridge(snapshot = null), cache = cache)
        val s = repo.state.value
        assertEquals(5_000L, s.snapshot?.balanceBytes)
        assertEquals(0.42, s.meanReliabilityWeight)
        assertEquals(7L, s.totalReferrals)
    }

    @Test
    fun `optimistic — без cache initial остаётся INITIAL`() {
        val repo = RealUrnetworkBalanceRepository(FakeBridge(snapshot = null), cache = InMemoryCache())
        assertEquals(UrnetworkBalanceState.INITIAL, repo.state.value)
    }

    @Test
    fun `cache — успешный refresh пишет snapshot в cache`() = runTest {
        val cache = InMemoryCache()
        val fresh = SubscriptionBalanceSnapshot(2L, 0L, 10L, 8L, null, null)
        val repo = RealUrnetworkBalanceRepository(FakeBridge(snapshot = fresh), cache = cache)
        repo.refresh()
        assertEquals(2L, cache.load()?.snapshot?.balanceBytes)
    }

    @Test
    fun `cache — null snapshot не перезаписывает существующий cache`() = runTest {
        val seeded = SubscriptionBalanceSnapshot(1L, 0L, 1L, 0L, null, null)
        val cache = InMemoryCache().apply { save(seeded, 0.0, 0L) }
        val repo = RealUrnetworkBalanceRepository(FakeBridge(snapshot = null), cache = cache)
        repo.refresh()
        assertEquals(1L, cache.load()?.snapshot?.balanceBytes)
    }

    private class InMemoryCache : UrnetworkBalanceCache {
        private var cached: CachedBalance? = null
        override fun load(): CachedBalance? = cached
        override fun save(
            snapshot: SubscriptionBalanceSnapshot,
            meanReliabilityWeight: Double,
            totalReferrals: Long,
        ) {
            cached = CachedBalance(snapshot, meanReliabilityWeight, totalReferrals)
        }
        override fun clear() {
            cached = null
        }
    }

    private class FakeBridge(val snapshot: SubscriptionBalanceSnapshot?) : UrnetworkSdkBridge {
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
        override suspend fun fetchSubscriptionBalance(): SubscriptionBalanceSnapshot? = snapshot
    }
}
