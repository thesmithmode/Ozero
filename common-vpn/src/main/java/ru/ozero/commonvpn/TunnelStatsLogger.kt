package ru.ozero.commonvpn

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.ozero.enginescore.PersistentLoggers
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private data class TunnelStatsReadResult(val rxBytes: Long, val txBytes: Long, val source: String) {
    fun shouldRebaseFrom(
        baseline: TunnelStatsReadResult?,
        previousBySource: TunnelStatsReadResult?,
    ): Boolean = baseline == null || hasNewSource(baseline) || hasLowerCounterThan(previousBySource)

    private fun hasNewSource(baseline: TunnelStatsReadResult): Boolean = source != baseline.source

    private fun hasLowerCounterThan(previousBySource: TunnelStatsReadResult?): Boolean =
        previousBySource != null && (rxBytes < previousBySource.rxBytes || txBytes < previousBySource.txBytes)
}

class TunnelStatsLogger(
    private val scope: CoroutineScope,
    private val tunnelController: TunnelController,
    private val notificationFactory: OzeroNotificationFactory,
    private val tunIfaceNameRef: AtomicReference<String?>,
    private val stopSignal: AtomicBoolean,
    private val statsJobRef: AtomicReference<Job?>,
    private val engineExtras: () -> String,
) {

    fun start() {
        statsJobRef.getAndSet(null)?.cancel()
        val job = scope.launch {
            var prevTx = 0L
            var prevRx = 0L
            var tickCount = 0
            var sessionBaseline: TunnelStatsReadResult? = null
            val previousReadsBySource = mutableMapOf<String, TunnelStatsReadResult>()
            var carriedRxBytes = 0L
            var carriedTxBytes = 0L
            try {
                while (true) {
                    delay(STATS_SAMPLE_INTERVAL_MS)
                    if (stopSignal.get()) return@launch
                    val iface = tunIfaceNameRef.get()
                    val read = if (iface != null) {
                        TunInterfaceStats.readTunStats(iface)?.let {
                            TunnelStatsReadResult(it.rxBytes, it.txBytes, "iface=$iface")
                        }
                    } else {
                        null
                    } ?: UidTrafficStats.read()?.let {
                        TunnelStatsReadResult(it.rxBytes, it.txBytes, "uid")
                    }
                    if (read == null) {
                        if (tickCount % STATS_LOG_EVERY == 0) {
                            PersistentLoggers.debug(TAG, "TunnelStats: ни iface, ни uid stats недоступны")
                        }
                        tickCount++
                        continue
                    }
                    val rxBytes = read.rxBytes
                    val txBytes = read.txBytes
                    val source = read.source
                    val previousBySource = previousReadsBySource[source]
                    val baseline = sessionBaseline
                    val effectiveBaseline = if (read.shouldRebaseFrom(baseline, previousBySource)) {
                        val currentStats = tunnelController.stats.value
                        carriedRxBytes = currentStats?.rxBytes ?: 0L
                        carriedTxBytes = currentStats?.txBytes ?: 0L
                        read.also { sessionBaseline = it }
                    } else {
                        requireNotNull(baseline)
                    }
                    val normalizedRxBytes = carriedRxBytes + rxBytes - effectiveBaseline.rxBytes
                    val normalizedTxBytes = carriedTxBytes + txBytes - effectiveBaseline.txBytes
                    val snapshot = TunnelStats(
                        txPackets = 0L,
                        txBytes = normalizedTxBytes,
                        rxPackets = 0L,
                        rxBytes = normalizedRxBytes,
                        timestampMs = System.currentTimeMillis(),
                    )
                    tunnelController.updateStats(snapshot)
                    previousReadsBySource[source] = read
                    tickCount++
                    if (tickCount % STATS_NOTIFY_EVERY == 0 && !stopSignal.get()) {
                        tunnelController.stats.value?.let { stats ->
                            notificationFactory.notifyStats(
                                NotificationStatsFormatter.format(stats, engineExtras()),
                            )
                        }
                    }
                    if (tickCount % STATS_LOG_EVERY == 0) {
                        val dTx = txBytes - prevTx
                        val dRx = rxBytes - prevRx
                        Log.i(
                            TAG,
                            "TunnelStats[$source] tx=${BytesFormatter.humanReadable(txBytes)} " +
                                "rx=${BytesFormatter.humanReadable(rxBytes)} " +
                                "Δtx=${BytesFormatter.humanReadable(dTx)} Δrx=${BytesFormatter.humanReadable(dRx)}",
                        )
                        prevTx = txBytes
                        prevRx = rxBytes
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                PersistentLoggers.warn(TAG, "stats logger threw: ${t.message}")
            }
        }
        statsJobRef.set(job)
    }

    fun cancel() {
        statsJobRef.getAndSet(null)?.cancel()
    }

    companion object {
        private const val TAG = "TunnelStatsLogger"
        const val STATS_SAMPLE_INTERVAL_MS = 1_000L
        const val STATS_LOG_EVERY = 30
        const val STATS_NOTIFY_EVERY = 1
    }
}
