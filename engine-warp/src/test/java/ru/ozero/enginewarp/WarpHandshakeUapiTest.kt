package ru.ozero.enginewarp

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WarpHandshakeUapiTest {

    @Test
    fun `check returns false when uapi socket is absent`(@TempDir tmp: File) {
        assertEquals(false, WarpHandshakeUapi.check(tmp.absolutePath, "ozero-warp"))
    }

    @Test
    fun `findUapiSocket предпочитает preferred имя если exists`(@TempDir tmp: File) {
        val sockets = File(tmp, "sockets").apply { mkdirs() }
        File(sockets, "ozero-warp.sock").createNewFile()
        File(sockets, "tun0.sock").createNewFile()
        val found = WarpHandshakeUapi.findUapiSocket(tmp.absolutePath, "ozero-warp")
        assertEquals("ozero-warp.sock", found?.name)
    }

    @Test
    fun `findUapiSocket fallback на newest по lastModified когда preferred нет`(
        @TempDir tmp: File,
    ) {
        val sockets = File(tmp, "sockets").apply { mkdirs() }
        val stale = File(sockets, "tun5.sock").also { it.createNewFile() }
        stale.setLastModified(1_000L)
        val newer = File(sockets, "tun0.sock").also { it.createNewFile() }
        newer.setLastModified(2_000L)
        val newest = File(sockets, "tun3.sock").also { it.createNewFile() }
        newest.setLastModified(9_000L)
        val found = WarpHandshakeUapi.findUapiSocket(tmp.absolutePath, "ozero-warp")
        assertEquals("tun3.sock", found?.name)
    }

    @Test
    fun `findUapiSocket null когда sockets пуст и legacy отсутствует`(@TempDir tmp: File) {
        File(tmp, "sockets").mkdirs()
        assertNull(WarpHandshakeUapi.findUapiSocket(tmp.absolutePath, "ozero-warp"))
    }

    @Test
    fun `findUapiSocket null when sockets directory and legacy socket are absent`(@TempDir tmp: File) {
        assertNull(WarpHandshakeUapi.findUapiSocket(tmp.absolutePath, "ozero-warp"))
    }

    @Test
    fun `findUapiSocket ignores non sock files when choosing newest fallback`(@TempDir tmp: File) {
        val sockets = File(tmp, "sockets").apply { mkdirs() }
        File(sockets, "newest.txt").also {
            it.createNewFile()
            it.setLastModified(99_000L)
        }
        val socket = File(sockets, "tun1.sock").also {
            it.createNewFile()
            it.setLastModified(1_000L)
        }

        val found = WarpHandshakeUapi.findUapiSocket(tmp.absolutePath, "ozero-warp")

        assertEquals(socket.absolutePath, found?.absolutePath)
    }

    @Test
    fun `findUapiSocket prefers sockets directory over legacy even when legacy is newer`(@TempDir tmp: File) {
        val sockets = File(tmp, "sockets").apply { mkdirs() }
        val fallback = File(sockets, "tun1.sock").also {
            it.createNewFile()
            it.setLastModified(1_000L)
        }
        File(tmp, "ozero-warp.sock").also {
            it.createNewFile()
            it.setLastModified(99_000L)
        }

        val found = WarpHandshakeUapi.findUapiSocket(tmp.absolutePath, "ozero-warp")

        assertEquals(fallback.absolutePath, found?.absolutePath)
    }

    @Test
    fun `findUapiSocket fallback на legacy uapiPath name sock`(@TempDir tmp: File) {
        File(tmp, "ozero-warp.sock").createNewFile()
        val found = WarpHandshakeUapi.findUapiSocket(tmp.absolutePath, "ozero-warp")
        assertEquals("ozero-warp.sock", found?.name)
        assertEquals(tmp.absolutePath, found?.parentFile?.absolutePath)
    }

    @Test
    fun `findUapiSocket регрессия — НЕ должен возвращать firstOrNull lex order`(
        @TempDir tmp: File,
    ) {
        val sockets = File(tmp, "sockets").apply { mkdirs() }
        val staleLex = File(sockets, "tun0.sock").also { it.createNewFile() }
        staleLex.setLastModified(1_000L)
        val newest = File(sockets, "tun9.sock").also { it.createNewFile() }
        newest.setLastModified(99_000L)
        val found = WarpHandshakeUapi.findUapiSocket(tmp.absolutePath, "ozero-warp")
        assertEquals(
            "tun9.sock",
            found?.name,
            "lex-first tun0.sock — stale leftover. findUapiSocket обязан брать newest по mtime.",
        )
    }

    @Test
    fun `sentinel RealWarpSdkBridge чистит sockets перед awgTurnOn`() {
        val src = locateSource("engine-warp/src/main/java/ru/ozero/enginewarp/RealWarpSdkBridge.kt")
        val text = src.readText(Charsets.UTF_8)
        kotlin.test.assertTrue(
            text.contains("socketsDir") && text.contains("extension == \"sock\"") && text.contains("allSock=true"),
            "RealWarpSdkBridge должен чистить все uapiPath/sockets/*.sock перед awgTurnOn " +
                "(stale tunN.sock ломает findUapiSocket fallback)",
        )
    }

    @Test
    fun `sentinel findUapiSocket использует maxByOrNull mtime — НЕ firstOrNull`() {
        val src = locateSource("engine-warp/src/main/java/ru/ozero/enginewarp/WarpHandshakeUapi.kt")
        val text = src.readText(Charsets.UTF_8)
        kotlin.test.assertTrue(
            !text.contains("firstOrNull"),
            "WarpHandshakeUapi.findUapiSocket не должен использовать firstOrNull — " +
                "это lex-order, выбирает stale leftover. Use maxByOrNull { lastModified() }.",
        )
        kotlin.test.assertTrue(
            text.contains("maxByOrNull") && text.contains("lastModified"),
            "WarpHandshakeUapi.findUapiSocket должен брать newest socket по mtime.",
        )
    }

    private fun locateSource(rel: String): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(5) {
            val candidate = File(dir, rel)
            if (candidate.isFile) return candidate
            dir = dir.parentFile ?: return@repeat
        }
        error("$rel не найден от ${System.getProperty("user.dir")}")
    }
}
