package ru.ozero.enginewarp

data class AwgPreset(
    val id: String,
    val name: String,
    val params: AwgParams,
)

object AwgPresets {

    val DEFAULT_WARP = AwgPreset(
        id = "default_warp",
        name = "WARP стандарт",
        params = AwgParams(
            junkPacketCount = 5,
            junkPacketMinSize = 100,
            junkPacketMaxSize = 200,
            initPacketJunkSize = 0,
            responsePacketJunkSize = 0,
            initPacketMagicHeader = 1,
            responsePacketMagicHeader = 2,
            cookieReplyMagicHeader = 3,
            transportMagicHeader = 4,
        ),
    )

    val NO_OBFUSCATION = AwgPreset(
        id = "off",
        name = "Без обфускации",
        params = AwgParams(
            junkPacketCount = 0,
            junkPacketMinSize = 0,
            junkPacketMaxSize = 0,
            initPacketJunkSize = 0,
            responsePacketJunkSize = 0,
            initPacketMagicHeader = 1,
            responsePacketMagicHeader = 2,
            cookieReplyMagicHeader = 3,
            transportMagicHeader = 4,
        ),
    )

    val LIGHT = AwgPreset(
        id = "light",
        name = "Лёгкая",
        params = AwgParams(
            junkPacketCount = 3,
            junkPacketMinSize = 50,
            junkPacketMaxSize = 100,
            initPacketJunkSize = 0,
            responsePacketJunkSize = 0,
            initPacketMagicHeader = 1,
            responsePacketMagicHeader = 2,
            cookieReplyMagicHeader = 3,
            transportMagicHeader = 4,
        ),
    )

    val TSPU = AwgPreset(
        id = "tspu",
        name = "ТСПУ",
        params = AwgParams(
            junkPacketCount = 4,
            junkPacketMinSize = 40,
            junkPacketMaxSize = 70,
            initPacketJunkSize = 50,
            responsePacketJunkSize = 100,
            initPacketMagicHeader = 1,
            responsePacketMagicHeader = 2,
            cookieReplyMagicHeader = 3,
            transportMagicHeader = 4,
        ),
    )

    val AGGRESSIVE = AwgPreset(
        id = "aggressive",
        name = "Агрессивная",
        params = AwgParams(
            junkPacketCount = 10,
            junkPacketMinSize = 200,
            junkPacketMaxSize = 400,
            initPacketJunkSize = 100,
            responsePacketJunkSize = 200,
            initPacketMagicHeader = 1,
            responsePacketMagicHeader = 2,
            cookieReplyMagicHeader = 3,
            transportMagicHeader = 4,
        ),
    )

    val I1_V1 = AwgPreset(
        id = "i1_v1",
        name = "I1 вариант 1",
        params = AwgParams(
            junkPacketCount = 5,
            junkPacketMinSize = 100,
            junkPacketMaxSize = 200,
            payloadPacketSizeCount1 = 28,
            payloadPacketSizeCount2 = 29,
            payloadPacketSizeCount3 = 10,
        ),
    )

    val I1_V2 = AwgPreset(
        id = "i1_v2",
        name = "I1 вариант 2",
        params = AwgParams(
            junkPacketCount = 5,
            junkPacketMinSize = 100,
            junkPacketMaxSize = 200,
            payloadPacketSizeCount1 = 50,
            payloadPacketSizeCount2 = 60,
            payloadPacketSizeCount3 = 20,
        ),
    )

    val I1_V3 = AwgPreset(
        id = "i1_v3",
        name = "I1 вариант 3",
        params = AwgParams(
            junkPacketCount = 4,
            junkPacketMinSize = 40,
            junkPacketMaxSize = 70,
            payloadPacketSizeCount1 = 28,
            payloadPacketSizeCount2 = 29,
            payloadPacketSizeCount3 = 10,
        ),
    )

    val I1_V4 = AwgPreset(
        id = "i1_v4",
        name = "I1 вариант 4 (2 конфига)",
        params = AwgParams(
            junkPacketCount = 4,
            junkPacketMinSize = 40,
            junkPacketMaxSize = 70,
            payloadPacketSizeCount1 = 50,
            payloadPacketSizeCount2 = 60,
            payloadPacketSizeCount3 = 10,
        ),
    )

    val I1_OFF = AwgPreset(
        id = "i1_off",
        name = "Отключить I1",
        params = AwgParams(
            payloadPacketSizeCount1 = 0,
            payloadPacketSizeCount2 = 0,
            payloadPacketSizeCount3 = 0,
        ),
    )

    val CLICKABLE: List<AwgPreset> = listOf(
        DEFAULT_WARP, NO_OBFUSCATION, LIGHT, TSPU, AGGRESSIVE,
    )

    val ALL: List<AwgPreset> = listOf(
        DEFAULT_WARP, NO_OBFUSCATION, LIGHT, TSPU, AGGRESSIVE,
        I1_V1, I1_V2, I1_V3, I1_V4, I1_OFF,
    )
}
