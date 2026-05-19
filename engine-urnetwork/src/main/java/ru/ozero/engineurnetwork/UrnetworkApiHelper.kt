package ru.ozero.engineurnetwork

import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.Sdk
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
