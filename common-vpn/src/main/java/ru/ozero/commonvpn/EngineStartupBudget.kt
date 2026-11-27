package ru.ozero.commonvpn

import ru.ozero.enginescore.EngineId

object EngineStartupBudget {
    fun restartTimeoutMs(engine: EngineId?): Long = when (engine) {
        EngineId.OPERA -> 100_000L
        EngineId.MASQUE -> 90_000L
        EngineId.TGWSS -> 210_000L
        EngineId.GFVPN -> 540_000L
        else -> 0L
    }

    fun switchingTimeoutMs(engine: EngineId?): Long =
        restartTimeoutMs(engine).let { if (it == 0L) 0L else it + 20_000L }
}
