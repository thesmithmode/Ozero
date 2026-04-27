package ru.ozero.enginebyedpi.strategy

object ByeDpiStrategyMatrix {

    private val SPLIT_POSITIONS = listOf(1, 2, 4, 8, 16, 32, 64)
    private val FAKE_TTLS = listOf(1, 2, 4, 8)
    private val OOB_BYTES = listOf(0x00, 0xFF, 0xA0)

    fun generate(): List<ByeDpiStrategy> = buildList {
                SPLIT_POSITIONS.forEach { add(ByeDpiStrategy(DesyncMethod.SPLIT, it)) }

                SPLIT_POSITIONS.forEach { add(ByeDpiStrategy(DesyncMethod.DISORDER, it)) }

                for (split in listOf(1, 4, 16)) {
            for (ttl in FAKE_TTLS) {
                add(ByeDpiStrategy(DesyncMethod.FAKE, splitAt = split, fakeTtl = ttl))
            }
        }

                for (oob in OOB_BYTES) {
            add(ByeDpiStrategy(DesyncMethod.OOB, splitAt = 1, oobByte = oob))
        }
    }
}
