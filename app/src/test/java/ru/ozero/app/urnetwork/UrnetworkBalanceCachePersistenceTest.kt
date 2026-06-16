package ru.ozero.app.urnetwork

import org.json.JSONObject
import org.junit.jupiter.api.Test
import ru.ozero.engineurnetwork.UrnetworkSdkBridge.SubscriptionBalanceSnapshot
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UrnetworkBalanceCachePersistenceTest {

    private fun cache(prefs: InMemorySharedPreferences = InMemorySharedPreferences()) =
        RealUrnetworkBalanceCache(mockUrnetworkContext(prefs))

    @Test
    fun `load returns null when snapshot key is absent`() {
        assertNull(cache().load())
    }

    @Test
    fun `save and load round trip preserve every field`() {
        val prefs = InMemorySharedPreferences()
        val cached = SubscriptionBalanceSnapshot(
            balanceBytes = 5_000L,
            pendingBytes = 100L,
            startBalanceBytes = 10_000L,
            usedBytes = 4_900L,
            plan = "Free",
            store = "Play",
        )

        val balanceCache = cache(prefs)
        balanceCache.save(cached, meanReliabilityWeight = 0.42, totalReferrals = 7L)

        val loaded = assertNotNull(balanceCache.load())
        assertEquals(5_000L, loaded.snapshot.balanceBytes)
        assertEquals(100L, loaded.snapshot.pendingBytes)
        assertEquals(10_000L, loaded.snapshot.startBalanceBytes)
        assertEquals(4_900L, loaded.snapshot.usedBytes)
        assertEquals("Free", loaded.snapshot.plan)
        assertEquals("Play", loaded.snapshot.store)
        assertEquals(0.42, loaded.meanReliabilityWeight)
        assertEquals(7L, loaded.totalReferrals)
    }

    @Test
    fun `load tolerates legacy payload missing optional fields and defaults them to zero`() {
        val prefs = InMemorySharedPreferences().apply {
            putRaw(
                "snapshot_v1",
                JSONObject(
                    mapOf(
                        "balanceBytes" to 123L,
                        "pendingBytes" to 45L,
                        "startBalanceBytes" to 200L,
                        "usedBytes" to 77L,
                        "plan" to "Legacy",
                        "store" to "Android",
                    ),
                ).toString(),
            )
        }

        val loaded = assertNotNull(cache(prefs).load())
        assertEquals(123L, loaded.snapshot.balanceBytes)
        assertEquals("Legacy", loaded.snapshot.plan)
        assertEquals("Android", loaded.snapshot.store)
        assertEquals(0.0, loaded.meanReliabilityWeight)
        assertEquals(0L, loaded.totalReferrals)
    }

    @Test
    fun `save with empty plan and store reloads them as null`() {
        val prefs = InMemorySharedPreferences()
        val cached = SubscriptionBalanceSnapshot(
            balanceBytes = 1L,
            pendingBytes = 2L,
            startBalanceBytes = 3L,
            usedBytes = 4L,
            plan = null,
            store = null,
        )

        val balanceCache = cache(prefs)
        balanceCache.save(cached, meanReliabilityWeight = 0.0, totalReferrals = 0L)

        val loaded = assertNotNull(balanceCache.load())
        assertNull(loaded.snapshot.plan)
        assertNull(loaded.snapshot.store)
    }

    @Test
    fun `load returns null for malformed JSON payload`() {
        val prefs = InMemorySharedPreferences().apply {
            putRaw("snapshot_v1", "{not-json")
        }

        assertNull(cache(prefs).load())
    }

    @Test
    fun `clear removes persisted snapshot`() {
        val prefs = InMemorySharedPreferences()
        val balanceCache = cache(prefs)
        balanceCache.save(
            SubscriptionBalanceSnapshot(1L, 2L, 3L, 4L, "P", "S"),
            meanReliabilityWeight = 0.3,
            totalReferrals = 9L,
        )

        balanceCache.clear()

        assertNull(balanceCache.load())
    }
}
