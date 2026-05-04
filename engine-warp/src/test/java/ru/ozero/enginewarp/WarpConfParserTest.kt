package ru.ozero.enginewarp

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WarpConfParserTest {

    private val validConf = """
        [Interface]
        PrivateKey = xmPeOeSIU2UTjYFCSzw5Gc+Ks1uxZhanU6iQZKAyFpQ=
        Address = 172.16.0.2, 2606:4700:110:8439:7020:74f6:eca1:6dab
        DNS = 111.88.96.50, 111.88.96.51, 2a00:ab00:1233:26::50, 2a00:ab00:1233:26::51
        MTU = 1280
        S1 = 0
        S2 = 0
        Jc = 5
        Jmin = 100
        Jmax = 200
        H1 = 1
        H2 = 2
        H3 = 3
        H4 = 4

        [Peer]
        PublicKey = bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=
        AllowedIPs = 0.0.0.0/0, ::/0
        Endpoint = engage.cloudflareclient.com:4500
        PersistentKeepalive = 25
    """.trimIndent()

    @Test
    fun `parse валидного conf возвращает WarpConfig с правильными полями`() {
        val result = WarpConfParser.parse(validConf)
        assertTrue(result.isSuccess)
        val cfg = result.getOrThrow()
        assertEquals("xmPeOeSIU2UTjYFCSzw5Gc+Ks1uxZhanU6iQZKAyFpQ=", cfg.privateKey)
        assertEquals("bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=", cfg.peerPublicKey)
        assertEquals("engage.cloudflareclient.com:4500", cfg.peerEndpoint)
        assertEquals("172.16.0.2/32", cfg.interfaceAddressV4)
        assertEquals("2606:4700:110:8439:7020:74f6:eca1:6dab/128", cfg.interfaceAddressV6)
        assertEquals(1280, cfg.mtu)
        assertEquals(25, cfg.keepaliveSeconds)
    }

    @Test
    fun `parse парсит все AWG параметры из .conf файла`() {
        val result = WarpConfParser.parse(validConf)
        val p = result.getOrThrow().awgParams
        assertEquals(5, p.junkPacketCount)
        assertEquals(100, p.junkPacketMinSize)
        assertEquals(200, p.junkPacketMaxSize)
        assertEquals(0, p.initPacketJunkSize)
        assertEquals(0, p.responsePacketJunkSize)
        assertEquals(1L, p.initPacketMagicHeader)
        assertEquals(2L, p.responsePacketMagicHeader)
        assertEquals(3L, p.cookieReplyMagicHeader)
        assertEquals(4L, p.transportMagicHeader)
    }

    @Test
    fun `parse парсит 4 DNS сервера`() {
        val result = WarpConfParser.parse(validConf)
        assertEquals(
            listOf("111.88.96.50", "111.88.96.51", "2a00:ab00:1233:26::50", "2a00:ab00:1233:26::51"),
            result.getOrThrow().dnsServers,
        )
    }

    @Test
    fun `parse игнорирует неизвестные поля типа I1`() {
        val confWithI1 = validConf.replace(
            "H4 = 4",
            "H4 = 4\nI1 = <b 0xdeadbeef>",
        )
        val result = WarpConfParser.parse(confWithI1)
        assertTrue(result.isSuccess, "I1 поле должно игнорироваться")
    }

    @Test
    fun `parse без PrivateKey возвращает failure`() {
        val conf = validConf.lines().filterNot { it.trim().startsWith("PrivateKey") }.joinToString("\n")
        val result = WarpConfParser.parse(conf)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("PrivateKey") == true)
    }

    @Test
    fun `parse без PublicKey peer возвращает failure`() {
        val conf = validConf.lines().filterNot { it.trim().startsWith("PublicKey") }.joinToString("\n")
        val result = WarpConfParser.parse(conf)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("PublicKey") == true)
    }

    @Test
    fun `parse без Endpoint возвращает failure`() {
        val conf = validConf.lines().filterNot { it.trim().startsWith("Endpoint") }.joinToString("\n")
        val result = WarpConfParser.parse(conf)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Endpoint") == true)
    }

    @Test
    fun `parse без Address возвращает failure`() {
        val conf = validConf.lines().filterNot { it.trim().startsWith("Address") }.joinToString("\n")
        val result = WarpConfParser.parse(conf)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Address") == true)
    }

    @Test
    fun `parse без MTU использует DEFAULT_MTU`() {
        val conf = validConf.lines().filterNot { it.trim().startsWith("MTU") }.joinToString("\n")
        val result = WarpConfParser.parse(conf)
        assertEquals(WarpConfig.DEFAULT_MTU, result.getOrThrow().mtu)
    }

    @Test
    fun `parse без DNS использует DEFAULT_DNS`() {
        val conf = validConf.lines().filterNot { it.trim().startsWith("DNS") }.joinToString("\n")
        val result = WarpConfParser.parse(conf)
        assertEquals(WarpConfig.DEFAULT_DNS, result.getOrThrow().dnsServers)
    }

    @Test
    fun `parse без PersistentKeepalive использует DEFAULT_KEEPALIVE`() {
        val conf = validConf.lines()
            .filterNot { it.trim().startsWith("PersistentKeepalive") }
            .joinToString("\n")
        val result = WarpConfParser.parse(conf)
        assertEquals(WarpConfig.DEFAULT_KEEPALIVE, result.getOrThrow().keepaliveSeconds)
    }

    @Test
    fun `parse игнорирует строки с комментарием`() {
        val confWithComments = validConf.replace(
            "Jc = 5",
            "Jc = 5 # это комментарий",
        )
        val result = WarpConfParser.parse(confWithComments)
        assertEquals(5, result.getOrThrow().awgParams.junkPacketCount)
    }

    @Test
    fun `parse адрес без маски — добавляет CIDR суффикс`() {
        val confNoMask = validConf.replace(
            "Address = 172.16.0.2, 2606:4700:110:8439:7020:74f6:eca1:6dab",
            "Address = 172.16.0.2, 2606:4700::1",
        )
        val cfg = WarpConfParser.parse(confNoMask).getOrThrow()
        assertEquals("172.16.0.2/32", cfg.interfaceAddressV4)
        assertEquals("2606:4700::1/128", cfg.interfaceAddressV6)
    }

    @Test
    fun `parse адрес с маской — не дублирует маску`() {
        val cfg = WarpConfParser.parse(validConf).getOrThrow()
        assertTrue(cfg.interfaceAddressV4.count { it == '/' } == 1, "Только одна маска")
    }

    @Test
    fun `parse publicKey и accountLicense пустые (proxy-режим)`() {
        val cfg = WarpConfParser.parse(validConf).getOrThrow()
        assertEquals("", cfg.publicKey)
        assertEquals("", cfg.accountLicense)
    }
}
