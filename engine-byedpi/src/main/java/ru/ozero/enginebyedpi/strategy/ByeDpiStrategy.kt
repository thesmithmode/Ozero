package ru.ozero.enginebyedpi.strategy

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
