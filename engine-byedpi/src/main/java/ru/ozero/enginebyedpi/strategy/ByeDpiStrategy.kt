package ru.ozero.enginebyedpi.strategy

/**
 * E16.3: одна candidate-стратегия для ByeDPI. Сериализуется в CLI-args строку
 * для `EngineConfig.ByeDpi(args = strategy.toArgs())`.
 *
 * Параметрический пространство (см. PLAN v4 раздел 0.3):
 * - desyncMethod: split / disorder / fake / mod_http
 * - splitAt: позиция фрагментации TLS-record (1 / 2 / 4 / 8 / 16 / 32 / 64)
 * - fakeTtl: TTL для fake-пакета (1 / 2 / 4 / 8) — только если desync=fake
 * - oobByte: байт OOB-пакета (только если desync=oob)
 *
 * Combinatorial матрица ≤ 50 комбинаций (см. [ByeDpiStrategyMatrix]).
 */
data class ByeDpiStrategy(
    val desyncMethod: DesyncMethod,
    val splitAt: Int,
    val fakeTtl: Int? = null,
    val oobByte: Int? = null,
) {
    fun toArgs(): String = buildString {
        append("--desync ")
        append(desyncMethod.cli)
        append(":")
        append(splitAt)
        if (desyncMethod == DesyncMethod.FAKE && fakeTtl != null) {
            append(" --fake-ttl ").append(fakeTtl)
        }
        if (desyncMethod == DesyncMethod.OOB && oobByte != null) {
            append(" --oob-byte ").append(oobByte)
        }
    }
}

enum class DesyncMethod(val cli: String) {
    SPLIT("split"),
    DISORDER("disorder"),
    FAKE("fake"),
    OOB("oob"),
}
