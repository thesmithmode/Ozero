package ru.ozero.engineurnetwork

import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.SubscriptionBalanceCallback
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import ru.ozero.enginescore.PersistentLoggers
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

internal class UrnetworkApiHelper(
    private val deviceRef: AtomicReference<DeviceLocal?>,
    private val running: AtomicBoolean,
) {
    private val subscriptionBalanceRef =
        AtomicReference<UrnetworkSdkBridge.SubscriptionBalanceSnapshot?>(null)

    suspend fun fetchSubscriptionBalance(): UrnetworkSdkBridge.SubscriptionBalanceSnapshot? {
        if (!running.get()) return subscriptionBalanceRef.get()
        val device = deviceRef.get() ?: return null
        val api = runCatching { device.api }.getOrNull() ?: run {
            PersistentLoggers.warn(TAG, "device.api is null — cannot fetch subscription balance")
            return null
        }
        val cached = subscriptionBalanceRef.get()
        val snapshot = withTimeoutOrNull(API_TIMEOUT_MS) {
            suspendCancellableCoroutine<UrnetworkSdkBridge.SubscriptionBalanceSnapshot?> { cont ->
                val resumed = AtomicBoolean(false)
                val callback = SubscriptionBalanceCallback { result, err ->
                    if (!resumed.compareAndSet(false, true)) return@SubscriptionBalanceCallback
                    if (err != null || result == null) {
                        PersistentLoggers.warn(TAG, "subscriptionBalance err=${err?.message}")
                        cont.resume(null)
                        return@SubscriptionBalanceCallback
                    }
                    val balance = runCatching { result.balanceByteCount }.getOrDefault(0L)
                    val pending = runCatching { result.openTransferByteCount }.getOrDefault(0L)
                    val startBalance = runCatching { result.startBalanceByteCount }.getOrDefault(0L)
                    val sub = runCatching { result.currentSubscription }.getOrNull()
                    val plan = runCatching { sub?.plan }.getOrNull()
                    val store = runCatching { sub?.store }.getOrNull()
                    val used = startBalance - balance - pending
                    cont.resume(
                        UrnetworkSdkBridge.SubscriptionBalanceSnapshot(
                            balanceBytes = balance,
                            pendingBytes = pending,
                            startBalanceBytes = startBalance,
                            usedBytes = used,
                            plan = plan,
                            store = store,
                        ),
                    )
                }
                runCatching { api.subscriptionBalance(callback) }.onFailure { t ->
                    if (resumed.compareAndSet(false, true)) {
                        PersistentLoggers.warn(TAG, "subscriptionBalance threw: ${t.message}")
                        cont.resume(null)
                    }
                }
            }
        }
        return when {
            snapshot != null -> {
                subscriptionBalanceRef.set(snapshot)
                snapshot
            }
            else -> {
                PersistentLoggers.warn(TAG, "subscriptionBalance timeout/null — using cached=${cached != null}")
                cached
            }
        }
    }

    suspend fun fetchAccountPoints(): UrnetworkSdkBridge.AccountPointsSnapshot? {
        if (!running.get()) return null
        val device = deviceRef.get() ?: return null
        val api = runCatching { device.api }.getOrNull() ?: run {
            PersistentLoggers.warn(TAG, "device.api is null — cannot fetch account points")
            return null
        }
        return withTimeoutOrNull(API_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val resumed = AtomicBoolean(false)
                runCatching {
                    api.getAccountPoints { result, err ->
                        if (!resumed.compareAndSet(false, true)) return@getAccountPoints
                        if (err != null || result == null) {
                            PersistentLoggers.warn(TAG, "getAccountPoints err=${err?.message}")
                            cont.resume(null)
                            return@getAccountPoints
                        }
                        val n = runCatching { result.accountPoints?.len() ?: 0L }.getOrDefault(0L)
                        var total = 0.0
                        var payout = 0.0
                        var referral = 0.0
                        var reliability = 0.0
                        var multiplier = 0.0
                        for (i in 0 until n) {
                            val point = runCatching { result.accountPoints.get(i) }.getOrNull() ?: continue
                            val value = runCatching { Sdk.nanoPointsToPoints(point.pointValue) }.getOrDefault(0.0)
                            total += value
                            when (runCatching { point.event }.getOrNull()?.uppercase()) {
                                "PAYOUT" -> payout += value
                                "PAYOUT_LINKED_ACCOUNT" -> referral += value
                                "PAYOUT_RELIABILITY" -> reliability += value
                                "PAYOUT_MULTIPLIER" -> multiplier += value
                            }
                        }
                        cont.resume(
                            UrnetworkSdkBridge.AccountPointsSnapshot(
                                totalPoints = total,
                                payoutPoints = payout,
                                referralPoints = referral,
                                reliabilityPoints = reliability,
                                multiplierPoints = multiplier,
                            ),
                        )
                    }
                }.onFailure { t ->
                    if (resumed.compareAndSet(false, true)) {
                        PersistentLoggers.warn(TAG, "getAccountPoints threw: ${t.message}")
                        cont.resume(null)
                    }
                }
            }
        }
    }

    suspend fun fetchNetworkReliability(): Double? {
        if (!running.get()) return null
        val device = deviceRef.get() ?: return null
        val api = runCatching { device.api }.getOrNull() ?: run {
            PersistentLoggers.warn(TAG, "device.api is null — cannot fetch network reliability")
            return null
        }
        return withTimeoutOrNull(API_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val resumed = AtomicBoolean(false)
                runCatching {
                    api.getNetworkReliability { result, err ->
                        if (!resumed.compareAndSet(false, true)) return@getNetworkReliability
                        if (err != null || result == null) {
                            PersistentLoggers.warn(TAG, "getNetworkReliability err=${err?.message}")
                            cont.resume(null)
                            return@getNetworkReliability
                        }
                        cont.resume(runCatching { result.reliabilityWindow?.meanReliabilityWeight }.getOrNull())
                    }
                }.onFailure { t ->
                    if (resumed.compareAndSet(false, true)) {
                        PersistentLoggers.warn(TAG, "getNetworkReliability threw: ${t.message}")
                        cont.resume(null)
                    }
                }
            }
        }
    }

    suspend fun fetchReferralCount(): Long? {
        if (!running.get()) return null
        val device = deviceRef.get() ?: return null
        val api = runCatching { device.api }.getOrNull() ?: run {
            PersistentLoggers.warn(TAG, "device.api is null — cannot fetch referral count")
            return null
        }
        return withTimeoutOrNull(API_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val resumed = AtomicBoolean(false)
                runCatching {
                    api.getNetworkReferralCode { result, err ->
                        if (!resumed.compareAndSet(false, true)) return@getNetworkReferralCode
                        if (err != null || result == null) {
                            PersistentLoggers.warn(TAG, "getNetworkReferralCode err=${err?.message}")
                            cont.resume(null)
                            return@getNetworkReferralCode
                        }
                        cont.resume(runCatching { result.totalReferrals }.getOrNull())
                    }
                }.onFailure { t ->
                    if (resumed.compareAndSet(false, true)) {
                        PersistentLoggers.warn(TAG, "getNetworkReferralCode threw: ${t.message}")
                        cont.resume(null)
                    }
                }
            }
        }
    }

    private companion object {
        const val TAG = "UrnetworkApiHelper"
        const val API_TIMEOUT_MS = 10_000L
    }
}
