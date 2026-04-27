package ru.ozero.enginebyedpi.strategy

import android.util.Log

class ByeDpiStrategyGenerator(
    private val matrix: List<ByeDpiStrategy> = ByeDpiStrategyMatrix.generate(),
) {

    suspend fun findWinning(probe: suspend (ByeDpiStrategy) -> Boolean): ByeDpiStrategy? {
        for ((i, candidate) in matrix.withIndex()) {
            Log.i(TAG, "probe ${i + 1}/${matrix.size}: ${candidate.toArgs()}")
            val ok = runCatching { probe(candidate) }.getOrDefault(false)
            if (ok) {
                Log.i(TAG, "winning strategy found: ${candidate.toArgs()}")
                return candidate
            }
        }
        Log.w(TAG, "no winning strategy after ${matrix.size} probes")
        return null
    }

    fun defaultStrategy(): ByeDpiStrategy = ByeDpiStrategy(DesyncMethod.SPLIT, splitAt = 1)

    private companion object {
        const val TAG = "ByeDpiStrategyGen"
    }
}
