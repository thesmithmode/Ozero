package ru.ozero.enginewarp

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class WarpConfParserAwgExtendedTest {

    private val baseConf = """
        [Interface]
        PrivateKey = xmPeOeSIU2UTjYFCSzw5Gc+Ks1uxZhanU6iQZKAyFpQ=
        Address = 172.16.0.2/32
        DNS = 1.1.1.1, 1.0.0.1
        MTU = 1280
        Jc = 7
        Jmin = 50
        Jmax = 150
        S1 = 31
        S2 = 47
        S3 = 19
        S4 = 23
        H1 = 100
        H2 = 200
        H3 = 300
        H4 = 400
        I1 = 13
        I2 = 17
        I3 = 41
        I4 = 43
        I5 = 11

        [Peer]
        PublicKey = bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=
        AllowedIPs = 0.0.0.0/0, ::/0
        Endpoint = engage.cloudflareclient.com:4500
        PersistentKeepalive = 25
    """.trimIndent()

    @Test
    fun `парсер сохраняет все AWG поля включая i3 i4 s3 s4 i1 i2 i5`() {
        val cfg = WarpConfParser.parse(baseConf).getOrThrow()
        val awg = cfg.awgParams
        assertEquals(7, awg.junkPacketCount)
        assertEquals(50, awg.junkPacketMinSize)
        assertEquals(150, awg.junkPacketMaxSize)
        assertEquals(31, awg.initPacketJunkSize)
        assertEquals(47, awg.responsePacketJunkSize)
        assertEquals(19, awg.underloadPacketJunkSize, "S3 должен парситься")
        assertEquals(23, awg.payloadPacketJunkSize, "S4 должен парситься")
        assertEquals(100L, awg.initPacketMagicHeader)
        assertEquals(200L, awg.responsePacketMagicHeader)
        assertEquals(300L, awg.cookieReplyMagicHeader)
        assertEquals(400L, awg.transportMagicHeader)
        assertEquals(13, awg.payloadPacketSizeCount1, "I1 должен парситься")
        assertEquals(17, awg.payloadPacketSizeCount2, "I2 должен парситься")
        assertEquals(41, awg.specialJunk3, "I3 должен парситься")
        assertEquals(43, awg.specialJunk4, "I4 должен парситься")
        assertEquals(11, awg.payloadPacketSizeCount3, "I5 должен парситься")
    }

    @Test
    fun `парсер использует defaults когда AWG поля отсутствуют`() {
        val conf = """
            [Interface]
            PrivateKey = xmPeOeSIU2UTjYFCSzw5Gc+Ks1uxZhanU6iQZKAyFpQ=
            Address = 172.16.0.2/32

            [Peer]
            PublicKey = bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=
            Endpoint = 162.159.192.1:2408
        """.trimIndent()
        val awg = WarpConfParser.parse(conf).getOrThrow().awgParams
        assertEquals(AwgParams.DEFAULT_I3, awg.specialJunk3)
        assertEquals(AwgParams.DEFAULT_I4, awg.specialJunk4)
    }

    @Test
    fun `builder печатает I3 I4 если они не дефолтные`() {
        val cfg = WarpConfParser.parse(baseConf).getOrThrow()
        val ini = WarpIniBuilder.build(cfg)
        kotlin.test.assertTrue(ini.contains("I3 = 41"), "I3 должен быть в построенном INI")
        kotlin.test.assertTrue(ini.contains("I4 = 43"), "I4 должен быть в построенном INI")
    }

    @Test
    fun `builder опускает I3 I4 когда они равны нулю - дефолтному значению`() {
        val confWithDefaultI3I4 = baseConf.replace("I3 = 41", "I3 = 0").replace("I4 = 43", "I4 = 0")
        val cfg = WarpConfParser.parse(confWithDefaultI3I4).getOrThrow()
        val ini = WarpIniBuilder.build(cfg)
        kotlin.test.assertFalse(ini.contains("I3 = "), "I3 не должен печататься при значении 0")
        kotlin.test.assertFalse(ini.contains("I4 = "), "I4 не должен печататься при значении 0")
    }

    @Test
    fun `парсер сохраняет I1 hex blob payload (AWG v2 special junk)`() {
        val conf = """
            [Interface]
            PrivateKey = xmPeOeSIU2UTjYFCSzw5Gc+Ks1uxZhanU6iQZKAyFpQ=
            Address = 172.16.0.2/32
            S1 = 0
            S2 = 0
            Jc = 4
            Jmin = 40
            Jmax = 70
            H1 = 1
            H2 = 2
            H3 = 3
            H4 = 4
            I1 = <b 0xc100abcdef>

            [Peer]
            PublicKey = bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=
            AllowedIPs = 0.0.0.0/0, ::/0
            Endpoint = 162.159.195.1:500
        """.trimIndent()
        val awg = WarpConfParser.parse(conf).getOrThrow().awgParams
        assertEquals("c100abcdef", awg.payloadHexI1)
        assertEquals(AwgParams.DEFAULT_I1, awg.payloadPacketSizeCount1, "Int fallback при hex blob")
    }

    @Test
    fun `builder печатает I1 hex blob в формате b 0x при наличии payloadHexI1`() {
        val cfg = WarpConfig(
            privateKey = "x",
            peerPublicKey = "y",
            peerEndpoint = "1.2.3.4:500",
            interfaceAddressV4 = "172.16.0.2/32",
            interfaceAddressV6 = "",
            awgParams = AwgParams(payloadHexI1 = "c100abcdef"),
        )
        val ini = WarpIniBuilder.build(cfg)
        kotlin.test.assertTrue(ini.contains("I1 = <b 0xc100abcdef>"), "I1 hex должен идти как <b 0xHEX>")
        kotlin.test.assertFalse(ini.contains("I1 = ${AwgParams.DEFAULT_I1}"), "Int fallback не должен дублироваться")
    }

    @Test
    fun `round-trip hex blob — parse build parse сохраняет payloadHexI1`() {
        val conf = """
            [Interface]
            PrivateKey = xmPeOeSIU2UTjYFCSzw5Gc+Ks1uxZhanU6iQZKAyFpQ=
            Address = 172.16.0.2/32
            I1 = <b 0xdeadbeef>

            [Peer]
            PublicKey = bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=
            Endpoint = 1.2.3.4:500
        """.trimIndent()
        val first = WarpConfParser.parse(conf).getOrThrow()
        val rebuilt = WarpIniBuilder.build(first)
        val second = WarpConfParser.parse(rebuilt).getOrThrow()
        assertEquals("deadbeef", second.awgParams.payloadHexI1)
    }

    @Test
    fun `парсер отвергает мусорный hex (не четная длина или non-hex символы) и fallback на DEFAULT`() {
        val conf = """
            [Interface]
            PrivateKey = xmPeOeSIU2UTjYFCSzw5Gc+Ks1uxZhanU6iQZKAyFpQ=
            Address = 172.16.0.2/32
            I1 = <b 0xZZZ>

            [Peer]
            PublicKey = bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=
            Endpoint = 1.2.3.4:500
        """.trimIndent()
        val awg = WarpConfParser.parse(conf).getOrThrow().awgParams
        kotlin.test.assertNull(awg.payloadHexI1, "невалидный hex → null")
        assertEquals(AwgParams.DEFAULT_I1, awg.payloadPacketSizeCount1)
    }

    @Test
    fun `builder опускает S3 S4 I1 I2 I5 когда равны нулю — не инжектирует defaults в mirror конфиги`() {
        val confNoS3S4 = """
            [Interface]
            PrivateKey = xmPeOeSIU2UTjYFCSzw5Gc+Ks1uxZhanU6iQZKAyFpQ=
            Address = 172.16.0.2/32
            Jc = 5
            Jmin = 100
            Jmax = 200
            S1 = 0
            S2 = 0
            H1 = 1
            H2 = 2
            H3 = 3
            H4 = 4

            [Peer]
            PublicKey = bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=
            Endpoint = 162.159.195.1:500
        """.trimIndent()
        val cfg = WarpConfParser.parse(confNoS3S4).getOrThrow()
        val ini = WarpIniBuilder.build(cfg)
        kotlin.test.assertFalse(
            ini.contains("S3 = "),
            "S3=0 не должен записываться — amneziawg-go default=0 при отсутствии поля",
        )
        kotlin.test.assertFalse(
            ini.contains("S4 = "),
            "S4=0 не должен записываться — amneziawg-go default=0 при отсутствии поля",
        )
        kotlin.test.assertFalse(ini.contains("I1 = "), "I1=0 не должен записываться")
        kotlin.test.assertFalse(ini.contains("I2 = "), "I2=0 не должен записываться")
        kotlin.test.assertFalse(ini.contains("I5 = "), "I5=0 не должен записываться")
    }

    @Test
    fun `round-trip parse build parse сохраняет все AWG поля`() {
        val original = WarpConfParser.parse(baseConf).getOrThrow().awgParams
        val ini = WarpIniBuilder.build(WarpConfParser.parse(baseConf).getOrThrow())
        val roundTrip = WarpConfParser.parse(ini).getOrThrow().awgParams
        assertEquals(original.junkPacketCount, roundTrip.junkPacketCount)
        assertEquals(original.junkPacketMinSize, roundTrip.junkPacketMinSize)
        assertEquals(original.junkPacketMaxSize, roundTrip.junkPacketMaxSize)
        assertEquals(original.initPacketJunkSize, roundTrip.initPacketJunkSize)
        assertEquals(original.responsePacketJunkSize, roundTrip.responsePacketJunkSize)
        assertEquals(original.underloadPacketJunkSize, roundTrip.underloadPacketJunkSize)
        assertEquals(original.payloadPacketJunkSize, roundTrip.payloadPacketJunkSize)
        assertEquals(original.initPacketMagicHeader, roundTrip.initPacketMagicHeader)
        assertEquals(original.responsePacketMagicHeader, roundTrip.responsePacketMagicHeader)
        assertEquals(original.cookieReplyMagicHeader, roundTrip.cookieReplyMagicHeader)
        assertEquals(original.transportMagicHeader, roundTrip.transportMagicHeader)
        assertEquals(original.payloadPacketSizeCount1, roundTrip.payloadPacketSizeCount1)
        assertEquals(original.payloadPacketSizeCount2, roundTrip.payloadPacketSizeCount2)
        assertEquals(original.payloadPacketSizeCount3, roundTrip.payloadPacketSizeCount3)
        assertEquals(original.specialJunk3, roundTrip.specialJunk3)
        assertEquals(original.specialJunk4, roundTrip.specialJunk4)
    }
}
