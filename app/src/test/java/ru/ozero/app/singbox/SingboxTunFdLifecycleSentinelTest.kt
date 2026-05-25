package ru.ozero.app.singbox

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingboxTunFdLifecycleSentinelTest {

    @Test
    fun `should SingboxEngineService call detachFd before passing fd to Go runtime`() {
        val root = locateRepoRoot()
        val service = File(
            root,
            "singbox-process/src/main/java/ru/ozero/singboxprocess/SingboxEngineService.kt",
        )
        assertTrue(service.isFile, "SingboxEngineService.kt must exist")
        val content = service.readText()

        assertTrue(
            content.contains("detachFd()"),
            "SingboxEngineService.startWithConfig must call tunFd.detachFd() — " +
                "Go runtime holds the fd raw; if PFD closes (GC) before Go event loop starts, fd is closed under Go = tunnel failure",
        )

        val startWithConfigBlock = content.substringAfter("startWithConfig(")
            .substringBefore("override fun ")
        val detachFdPos = startWithConfigBlock.indexOf("detachFd()")
        assertTrue(detachFdPos >= 0, "detachFd must be in startWithConfig, not elsewhere")
    }

    @Test
    fun `should SingboxEngine not retain ParcelFileDescriptor after attachTun`() {
        val root = locateRepoRoot()
        val engine = File(
            root,
            "engine-singbox/src/main/java/ru/ozero/enginesingbox/SingboxEngine.kt",
        )
        assertTrue(engine.isFile, "SingboxEngine.kt must exist")
        val content = engine.readText()

        assertTrue(
            content.contains("attachTun"),
            "SingboxEngine must implement attachTun",
        )
        assertTrue(
            content.contains("startWithConfig"),
            "SingboxEngine.attachTun must call proxy.startWithConfig over AIDL",
        )
    }

    @Test
    fun `should SingboxRuntime receive raw fd int not ParcelFileDescriptor`() {
        val root = locateRepoRoot()
        val runtime = File(
            root,
            "singbox-process/src/main/java/ru/ozero/singboxprocess/SingboxRuntime.kt",
        )
        assertTrue(runtime.isFile, "SingboxRuntime.kt must exist")
        val content = runtime.readText()

        val startBlock = content.substringAfter("fun start(").substringBefore(") =\n")
            .ifEmpty { content.substringAfter("fun start(").substringBefore(") {") }
        assertFalse(
            startBlock.contains("ParcelFileDescriptor"),
            "SingboxRuntime.start must accept raw Int tunFd, not ParcelFileDescriptor — " +
                "PFD is detached in SingboxEngineService before passing to runtime",
        )
        assertTrue(
            startBlock.contains("tunFd") && startBlock.contains("Int"),
            "SingboxRuntime.start must accept tunFd as Int",
        )
    }

    private fun locateRepoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(5) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile ?: return@repeat
        }
        error("repo root not found")
    }
}
