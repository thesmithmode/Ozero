package ru.ozero.commonvpn

class StatsStagnationMonitor(
    private val thresholdMs: Long = STAGNATION_THRESHOLD_MS,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private var lastTxBytes: Long = 0L
    private var lastRxBytes: Long = 0L
    private var lastChangeMs: Long = nowMs()

    fun reset() {
        lastTxBytes = 0L
        lastRxBytes = 0L
        lastChangeMs = nowMs()
    }

    fun observe(txBytes: Long, rxBytes: Long): Boolean {
        val now = nowMs()
        if (txBytes != lastTxBytes || rxBytes != lastRxBytes) {
            lastTxBytes = txBytes
            lastRxBytes = rxBytes
            lastChangeMs = now
            return false
        }
        return (now - lastChangeMs) >= thresholdMs
    }

    companion object {
        const val STAGNATION_THRESHOLD_MS: Long = 30_000L
    }
}
