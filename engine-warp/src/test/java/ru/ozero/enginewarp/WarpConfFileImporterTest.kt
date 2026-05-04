package ru.ozero.enginewarp

import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WarpConfFileImporterTest {

    private val importer = WarpConfFileImporter()

    private val validConf = """
        [Interface]
        PrivateKey = xmPeOeSIU2UTjYFCSzw5Gc+Ks1uxZhanU6iQZKAyFpQ=
        Address = 172.16.0.2, 2606:4700:110:8439:7020:74f6:eca1:6dab
        DNS = 1.1.1.1, 2606:4700:4700::1111
        MTU = 1280
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
        AllowedIPs = 0.0.0.0/0, ::/0
        Endpoint = engage.cloudflareclient.com:4500
        PersistentKeepalive = 25
    """.trimIndent()

    @Test
    fun `import валидного conf файла возвращает WarpConfig`() {
        val stream = ByteArrayInputStream(validConf.toByteArray())
        val result = importer.import(stream)
        assertTrue(result.isSuccess)
        val cfg = result.getOrThrow()
        assertEquals("xmPeOeSIU2UTjYFCSzw5Gc+Ks1uxZhanU6iQZKAyFpQ=", cfg.privateKey)
        assertEquals("engage.cloudflareclient.com:4500", cfg.peerEndpoint)
    }

    @Test
    fun `import пустого потока возвращает failure с сообщением`() {
        val stream = ByteArrayInputStream(ByteArray(0))
        val result = importer.import(stream)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("пустой") == true)
    }

    @Test
    fun `import пробельного содержимого возвращает failure`() {
        val stream = ByteArrayInputStream("   \n\t  ".toByteArray())
        val result = importer.import(stream)
        assertTrue(result.isFailure)
    }

    @Test
    fun `import файла без PrivateKey возвращает failure`() {
        val bad = validConf.lines().filterNot { it.trim().startsWith("PrivateKey") }.joinToString("\n")
        val result = importer.import(ByteArrayInputStream(bad.toByteArray()))
        assertTrue(result.isFailure)
    }

    @Test
    fun `import парсит AWG параметры из файла`() {
        val stream = ByteArrayInputStream(validConf.toByteArray())
        val cfg = importer.import(stream).getOrThrow()
        assertEquals(5, cfg.awgParams.junkPacketCount)
        assertEquals(1L, cfg.awgParams.initPacketMagicHeader)
    }

    @Test
    fun `import игнорирует поле I1 из реального WARP conf`() {
        val confWithI1 = validConf + "\nI1 = <b 0xdeadbeef1234>"
        val result = importer.import(ByteArrayInputStream(confWithI1.toByteArray()))
        assertTrue(result.isSuccess)
    }

    @Test
    fun `import UTF-8 файла корректно`() {
        val stream = ByteArrayInputStream(validConf.toByteArray(Charsets.UTF_8))
        assertTrue(importer.import(stream).isSuccess)
    }
}
