package ru.ozero.enginewarp

data class AwgParams(
    val junkPacketCount: Int = DEFAULT_JC,
    val junkPacketMinSize: Int = DEFAULT_JMIN,
    val junkPacketMaxSize: Int = DEFAULT_JMAX,
    val initPacketJunkSize: Int = DEFAULT_S1,
    val responsePacketJunkSize: Int = DEFAULT_S2,
    val initPacketMagicHeader: Long = DEFAULT_H1,
    val responsePacketMagicHeader: Long = DEFAULT_H2,
    val cookieReplyMagicHeader: Long = DEFAULT_H3,
    val transportMagicHeader: Long = DEFAULT_H4,
) {
    companion object {
        const val DEFAULT_JC = 5
        const val DEFAULT_JMIN = 100
        const val DEFAULT_JMAX = 200
        const val DEFAULT_S1 = 0
        const val DEFAULT_S2 = 0
        const val DEFAULT_H1 = 1L
        const val DEFAULT_H2 = 2L
        const val DEFAULT_H3 = 3L
        const val DEFAULT_H4 = 4L
    }
}
