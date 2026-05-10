package ru.ozero.enginewarp

data class AwgParams(
    val junkPacketCount: Int = DEFAULT_JC,
    val junkPacketMinSize: Int = DEFAULT_JMIN,
    val junkPacketMaxSize: Int = DEFAULT_JMAX,
    val initPacketJunkSize: Int = DEFAULT_S1,
    val responsePacketJunkSize: Int = DEFAULT_S2,
    val underloadPacketJunkSize: Int = DEFAULT_S3,
    val payloadPacketJunkSize: Int = DEFAULT_S4,
    val initPacketMagicHeader: Long = DEFAULT_H1,
    val responsePacketMagicHeader: Long = DEFAULT_H2,
    val cookieReplyMagicHeader: Long = DEFAULT_H3,
    val transportMagicHeader: Long = DEFAULT_H4,
    val payloadPacketSizeCount1: Int = DEFAULT_I1,
    val payloadPacketSizeCount2: Int = DEFAULT_I2,
    val payloadPacketSizeCount3: Int = DEFAULT_I5,
) {
    init {
        require(junkPacketMinSize <= junkPacketMaxSize) {
            "Jmin ($junkPacketMinSize) must be <= Jmax ($junkPacketMaxSize)"
        }
        require(junkPacketCount in JC_RANGE) { "Jc out of range: $junkPacketCount" }
        require(junkPacketMinSize in SIZE_RANGE) { "Jmin out of range: $junkPacketMinSize" }
        require(junkPacketMaxSize in SIZE_RANGE) { "Jmax out of range: $junkPacketMaxSize" }
        require(initPacketJunkSize in SIZE_RANGE) { "S1 out of range: $initPacketJunkSize" }
        require(responsePacketJunkSize in SIZE_RANGE) { "S2 out of range: $responsePacketJunkSize" }
        val headers = listOf(
            initPacketMagicHeader,
            responsePacketMagicHeader,
            cookieReplyMagicHeader,
            transportMagicHeader,
        )
        require(headers.all { it in HEADER_RANGE }) {
            "Magic headers must be in $HEADER_RANGE: $headers"
        }
        require(headers.toSet().size == headers.size) {
            "Magic headers must be unique: $headers"
        }
    }

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

        val JC_RANGE = 0..128
        val SIZE_RANGE = 0..1500
        val HEADER_RANGE = 1L..0xFFFFFFFFL

        const val DEFAULT_S3 = 19
        const val DEFAULT_S4 = 20
        const val DEFAULT_I1 = 28
        const val DEFAULT_I2 = 29
        const val DEFAULT_I5 = 10

        val VANILLA = AwgParams(
            junkPacketCount = 0,
            junkPacketMinSize = 0,
            junkPacketMaxSize = 0,
            initPacketJunkSize = 0,
            responsePacketJunkSize = 0,
            underloadPacketJunkSize = 0,
            payloadPacketJunkSize = 0,
            initPacketMagicHeader = 1L,
            responsePacketMagicHeader = 2L,
            cookieReplyMagicHeader = 3L,
            transportMagicHeader = 4L,
            payloadPacketSizeCount1 = 0,
            payloadPacketSizeCount2 = 0,
            payloadPacketSizeCount3 = 0,
        )
    }
}
