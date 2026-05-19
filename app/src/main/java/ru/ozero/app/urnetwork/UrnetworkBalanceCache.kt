package ru.ozero.app.urnetwork

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import ru.ozero.engineurnetwork.UrnetworkSdkBridge.SubscriptionBalanceSnapshot

interface UrnetworkBalanceCache {
    fun load(): CachedBalance?
    fun save(snapshot: SubscriptionBalanceSnapshot, meanReliabilityWeight: Double, totalReferrals: Long)
    fun clear()
}

data class CachedBalance(
    val snapshot: SubscriptionBalanceSnapshot,
    val meanReliabilityWeight: Double,
    val totalReferrals: Long,
)

class RealUrnetworkBalanceCache(context: Context) : UrnetworkBalanceCache {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): CachedBalance? {
        val raw = prefs.getString(KEY_SNAPSHOT, null) ?: return null
        return runCatching {
            val obj = JSONObject(raw)
            CachedBalance(
                snapshot = SubscriptionBalanceSnapshot(
                    balanceBytes = obj.getLong("balanceBytes"),
                    pendingBytes = obj.getLong("pendingBytes"),
                    startBalanceBytes = obj.getLong("startBalanceBytes"),
                    usedBytes = obj.getLong("usedBytes"),
                    plan = obj.optString("plan").takeIf { it.isNotEmpty() },
                    store = obj.optString("store").takeIf { it.isNotEmpty() },
                ),
                meanReliabilityWeight = obj.optDouble("meanReliabilityWeight", 0.0),
                totalReferrals = obj.optLong("totalReferrals", 0L),
            )
        }.getOrNull()
    }

    override fun save(
        snapshot: SubscriptionBalanceSnapshot,
        meanReliabilityWeight: Double,
        totalReferrals: Long,
    ) {
        val obj = JSONObject().apply {
            put("balanceBytes", snapshot.balanceBytes)
            put("pendingBytes", snapshot.pendingBytes)
            put("startBalanceBytes", snapshot.startBalanceBytes)
            put("usedBytes", snapshot.usedBytes)
            put("plan", snapshot.plan.orEmpty())
            put("store", snapshot.store.orEmpty())
            put("meanReliabilityWeight", meanReliabilityWeight)
            put("totalReferrals", totalReferrals)
        }
        prefs.edit().putString(KEY_SNAPSHOT, obj.toString()).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY_SNAPSHOT).apply()
    }

    private companion object {
        const val PREFS_NAME = "urnetwork_balance_cache"
        const val KEY_SNAPSHOT = "snapshot_v1"
    }
}
