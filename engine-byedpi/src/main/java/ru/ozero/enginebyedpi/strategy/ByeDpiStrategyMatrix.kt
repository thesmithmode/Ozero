package ru.ozero.enginebyedpi.strategy

/**
 * E16.3: генерирует упорядоченный список candidate-стратегий для probe.
 *
 * Порядок имеет значение: первыми идут проверенные комбинации (split:1
 * default ByeDPI), потом более экзотичные. First-pass winning = используется.
 * Полный matrix ограничен ~30 комбинациями чтобы probe-pass занимал
 * разумное время (~30-60 сек на средней сети при 2 сек/probe).
 */
object ByeDpiStrategyMatrix {

    private val SPLIT_POSITIONS = listOf(1, 2, 4, 8, 16, 32, 64)
    private val FAKE_TTLS = listOf(1, 2, 4, 8)
    private val OOB_BYTES = listOf(0x00, 0xFF, 0xA0)

    fun generate(): List<ByeDpiStrategy> = buildList {
        // SPLIT — проверенные временем (default ByeDPI), идут первыми
        SPLIT_POSITIONS.forEach { add(ByeDpiStrategy(DesyncMethod.SPLIT, it)) }

        // DISORDER — для случаев когда SPLIT детектится через TCP-segments reorder
        SPLIT_POSITIONS.forEach { add(ByeDpiStrategy(DesyncMethod.DISORDER, it)) }

        // FAKE — fake-payload с TTL ниже чем до DPI, обходит SNI-detection
        for (split in listOf(1, 4, 16)) {
            for (ttl in FAKE_TTLS) {
                add(ByeDpiStrategy(DesyncMethod.FAKE, splitAt = split, fakeTtl = ttl))
            }
        }

        // OOB — out-of-band byte injection, последняя надежда (агрессивная стратегия)
        for (oob in OOB_BYTES) {
            add(ByeDpiStrategy(DesyncMethod.OOB, splitAt = 1, oobByte = oob))
        }
    }
}
