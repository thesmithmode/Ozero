package ru.ozero.commonvpn

import android.net.TrafficStats
import android.os.Process

object UidTrafficStats {

    data class Snapshot(val rxBytes: Long, val txBytes: Long)

    fun read(uid: Int = Process.myUid()): Snapshot? {
        val rx = TrafficStats.getUidRxBytes(uid)
        val tx = TrafficStats.getUidTxBytes(uid)
        if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) {
            return null
        }
        if (rx < 0 || tx < 0) return null
        return Snapshot(rxBytes = rx, txBytes = tx)
    }
}
