package ru.ozero.singboxprocess

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingboxEngineServiceTest {

    private val source = File(
        locateRepoRoot(),
        "singbox-process/src/main/java/ru/ozero/singboxprocess/SingboxEngineService.kt",
    ).readText()

    @Test
    fun `stop waits for runtime shutdown before returning`() {
        val stopBlock = source.substringAfter("override fun stop()")
            .substringBefore("override fun stopAndWait")
        assertTrue(stopBlock.contains("stopAndWait(DEFAULT_STOP_TIMEOUT_MS)"))

        val stopAndWaitBlock = source.substringAfter("private fun stopRuntimeAndWait")
            .substringBefore("override fun getStats()")
        assertTrue(stopAndWaitBlock.contains("withTimeoutOrNull"))
        assertTrue(stopAndWaitBlock.contains("SingboxRuntime.stop()"))
        assertTrue(stopAndWaitBlock.contains("getOrDefault(false)"))
    }

    @Test
    fun `runtime exposes Android trust anchors to libbox`() {
        val runtimeSource = File(
            locateRepoRoot(),
            "singbox-process/src/main/java/ru/ozero/singboxprocess/SingboxRuntime.kt",
        ).readText()

        assertTrue(runtimeSource.contains("TrustManagerFactory.getDefaultAlgorithm()"))
        assertTrue(runtimeSource.contains("acceptedIssuers"))
        assertTrue(runtimeSource.contains("android.util.Base64"))
        assertFalse(runtimeSource.contains("java.util.Base64"))
        assertTrue(
            runtimeSource.contains("systemCertificates(): StringIterator = stringIterator(systemCertificatePem)"),
        )
        val systemCertificatesBlock = runtimeSource.substringAfter("override fun systemCertificates()")
            .substringBefore("override fun clearDNSCache()")
        assertFalse(systemCertificatesBlock.contains("override fun hasNext(): Boolean = false"))
    }

    @Test
    fun `stats do not fake active connections from runtime flag`() {
        val statsBlock = source.substringAfter("override fun getStats()")
            .substringBefore("override fun registerStatusCallback")
        assertTrue(statsBlock.contains("SingboxStats()"))
        assertFalse(statsBlock.contains("activeConnections = if"))
        assertFalse(statsBlock.contains("SingboxRuntime.isRunning()) 1"))
    }

    @Test
    fun `destroy path uses acknowledged stop`() {
        val destroyBlock = source.substringAfter("override fun onDestroy()")
            .substringBefore("companion object")
        assertTrue(destroyBlock.contains("binder.stopAndWait(DEFAULT_STOP_TIMEOUT_MS)"))
        assertTrue(destroyBlock.contains("serviceScope.cancel()"))
    }

    private fun locateRepoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(5) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile ?: dir
        }
        return File(System.getProperty("user.dir") ?: ".").absoluteFile
    }
}
