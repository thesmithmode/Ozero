package ru.ozero.commonvpn

data class TunnelStats(
    val txPackets: Long,
    val txBytes: Long,
    val rxPackets: Long,
    val rxBytes: Long,
    val timestampMs: Long,
    val bpsIn: Double = 0.0,
    val bpsOut: Double = 0.0,
    val sessionStartMs: Long = 0L,
)
