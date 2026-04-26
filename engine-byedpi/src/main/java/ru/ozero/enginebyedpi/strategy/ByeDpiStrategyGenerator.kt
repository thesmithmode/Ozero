package ru.ozero.enginebyedpi.strategy

import android.util.Log

/**
 * E16.3: пробует кандидатов из [ByeDpiStrategyMatrix] до first-pass.
 *
 * Контракт: вызывающий передаёт `probe(strategy)` suspend-функцию которая
 * стартует ByeDPI с этой стратегией, делает HTTPS запрос на target URL
 * (`youtube.com` / `discord.com` / `chatgpt.com`) через локальный SOCKS5,
 * возвращает true если ответ пришёл за timeout. Generator agnostic
 * к реализации probe — поэтому unit-тестируется без реального движка.
 *
 * Caching responsibilities — на вызывающем (DataStore через
 * `bydpi_winning_config` ключ). Generator чистый — runs матрицу и возвращает
 * winner.
 */
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

    /**
     * Дефолтная стратегия когда [findWinning] не нашёл (network outage / probe
     * fails). Используем самую безопасную — split:1 — чтобы движок хоть как-то
     * работал, пользователь увидит UI и сможет сам диагностировать.
     */
    fun defaultStrategy(): ByeDpiStrategy = ByeDpiStrategy(DesyncMethod.SPLIT, splitAt = 1)

    private companion object {
        const val TAG = "ByeDpiStrategyGen"
    }
}
