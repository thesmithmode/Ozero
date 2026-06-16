package ru.ozero.enginewarp

import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.reflect.InvocationTargetException
import kotlin.test.assertFailsWith
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
    fun `import valid conf returns ImportedWarpConfig`() {
        val stream = ByteArrayInputStream(validConf.toByteArray())
        val result = importer.import(stream)
        assertTrue(result.isSuccess)
        val imported = result.getOrThrow()
        assertEquals("xmPeOeSIU2UTjYFCSzw5Gc+Ks1uxZhanU6iQZKAyFpQ=", imported.config.privateKey)
        assertEquals("engage.cloudflareclient.com:4500", imported.config.peerEndpoint)
        assertEquals(validConf, imported.rawIni)
    }

    @Test
    fun `import invalid stream returns failure with read error`() {
        val stream = object : InputStream() {
            override fun read(): Int = throw IOException("stream broken")
        }
        val result = importer.import(stream)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("stream broken") == true)
    }

    @Test
    fun `import rethrows VirtualMachineError-path exception from stream read`() {
        val stream = object : InputStream() {
            override fun read(): Int = throw OutOfMemoryError("vm boom")
        }
        assertFailsWith<OutOfMemoryError> { importer.import(stream) }
    }

    @Test
    fun `import rethrows ThreadDeath from stream read`() {
        val stream = object : InputStream() {
            override fun read(): Int = throw ThreadDeath()
        }
        assertFailsWith<ThreadDeath> { importer.import(stream) }
    }

    @Test
    fun `import rethrows LinkageError from stream read`() {
        val stream = object : InputStream() {
            override fun read(): Int = throw java.lang.NoClassDefFoundError("missing")
        }
        assertFailsWith<NoClassDefFoundError> { importer.import(stream) }
    }

    @Test
    fun `import valid config keeps source metadata in rawIni`() {
        val withSource = validConf + "\n# source: ci-profile\n# name: unit-test\n"
        val imported = importer.import(ByteArrayInputStream(withSource.toByteArray())).getOrThrow()
        assertEquals("engage.cloudflareclient.com:4500", imported.config.peerEndpoint)
        assertTrue(imported.rawIni.contains("source: ci-profile"))
        assertTrue(imported.rawIni.contains("name: unit-test"))
    }

    @Test
    fun `import empty stream returns failure`() {
        val stream = ByteArrayInputStream(ByteArray(0))
        val result = importer.import(stream)
        assertTrue(result.isFailure)
    }

    @Test
    fun `import whitespace stream returns failure`() {
        val stream = ByteArrayInputStream("   \n\t  ".toByteArray())
        val result = importer.import(stream)
        assertTrue(result.isFailure)
    }

    @Test
    fun `import conf without private key returns parser failure`() {
        val bad = validConf.lines().filterNot { it.trim().startsWith("PrivateKey") }.joinToString("\n")
        val result = importer.import(ByteArrayInputStream(bad.toByteArray()))
        assertTrue(result.isFailure)
    }

    @Test
    fun `import extracts AWG params`() {
        val stream = ByteArrayInputStream(validConf.toByteArray())
        val imported = importer.import(stream).getOrThrow()
        assertEquals(5, imported.config.awgParams.junkPacketCount)
        assertEquals(1L, imported.config.awgParams.initPacketMagicHeader)
    }

    @Test
    fun `import keeps extra I1 line in rawIni`() {
        val confWithI1 = validConf + "\nI1 = <b 0xdeadbeef1234>"
        val result = importer.import(ByteArrayInputStream(confWithI1.toByteArray()))
        assertTrue(result.isSuccess)
        val imported = result.getOrThrow()
        assertTrue(imported.rawIni.contains("I1 = <b 0xdeadbeef1234>"))
    }

    @Test
    fun `import UTF-8 conf succeeds`() {
        val stream = ByteArrayInputStream(validConf.toByteArray(Charsets.UTF_8))
        assertTrue(importer.import(stream).isSuccess)
    }

    @Test
    fun `import large conf over 64KB is rejected`() {
        val padding = "# " + "x".repeat(70_000) + "\n"
        val tooLarge = padding + validConf
        val result = importer.import(ByteArrayInputStream(tooLarge.toByteArray()))
        assertTrue(result.isFailure)
    }

    @Test
    fun `readBounded accepts exactly limit size`() {
        val max = ByteArray(65_536) { 'x'.code.toByte() }
        val text = readBounded(ByteArrayInputStream(max), 65_536L)
        assertEquals(max.size, text.toByteArray().size)
    }

    @Test
    fun `readBounded rejects payload above limit size`() {
        val over = ByteArray(65_537) { 'x'.code.toByte() }
        val thrown = assertFailsWith<InvocationTargetException> {
            readBounded(ByteArrayInputStream(over), 65_536L)
        }
        assertTrue(thrown.cause is IOException)
    }

    private fun readBounded(stream: ByteArrayInputStream, maxBytes: Long): String {
        val companion = WarpConfFileImporter::class.java
            .getDeclaredField("Companion")
            .apply { isAccessible = true }
            .get(null)
        val method = companion.javaClass.getDeclaredMethod(
            "readBounded",
            InputStream::class.java,
            Long::class.javaPrimitiveType,
        ).apply { isAccessible = true }
        return method.invoke(companion, stream, maxBytes) as String
    }
}
