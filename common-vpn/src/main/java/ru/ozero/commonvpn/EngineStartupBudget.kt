package ru.ozero.commonvpn

import ru.ozero.enginescore.EngineId

object EngineStartupBudget {
    const val DEFAULT_CHAIN_TIMEOUT_MS = 30_000L
    private const val SWITCH_OVERHEAD_MS = 20_000L

    private class ExtendedBudget(val chainMs: Long, val postStartMs: Long) {
        val totalMs: Long get() = chainMs + postStartMs
    }

    private fun extended(engine: EngineId?): ExtendedBudget? = when (engine) {
        // Allow both bootstrap attempts and the final routed readiness probe.
        EngineId.OPERA -> ExtendedBudget(75_000L, 25_000L)
        EngineId.MASQUE -> ExtendedBudget(60_000L, 30_000L)
        // Cold registration, proxy startup and the WSS readiness probe have separate budgets.
        EngineId.TGWSS -> ExtendedBudget(180_000L, 30_000L)
        // Manual verification can consume both SDK startup and post-attachment readiness windows.
        EngineId.GFVPN -> ExtendedBudget(310_000L, 230_000L)
        else -> null
    }

    fun chainTimeoutMs(engine: EngineId): Long = extended(engine)?.chainMs ?: DEFAULT_CHAIN_TIMEOUT_MS

    fun restartTimeoutMs(engine: EngineId?): Long? = extended(engine)?.totalMs

    fun switchingTimeoutMs(engine: EngineId?): Long? = restartTimeoutMs(engine)?.plus(SWITCH_OVERHEAD_MS)
}
