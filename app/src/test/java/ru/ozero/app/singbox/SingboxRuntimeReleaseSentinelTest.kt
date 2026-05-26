package ru.ozero.app.singbox

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingboxRuntimeReleaseSentinelTest {

    @Test
    fun `sentinel startOrReloadService must pass non-null OverrideOptions`() {
        val root = locateRepoRoot()
        val content = File(
            root,
            "singbox-process/src/main/java/ru/ozero/singboxprocess/SingboxRuntime.kt",
        ).readText()

        val startBlock = content.substringAfter("fun start(")
            .substringBefore("fun stop(")

        assertTrue(
            startBlock.contains("OverrideOptions()"),
            "startOrReloadService must receive OverrideOptions(), not null — " +
                "Go code dereferences options.AutoRedirect without nil check → SIGABRT",
        )
        assertFalse(
            startBlock.contains("startOrReloadService(singboxJsonConfig, null)"),
            "startOrReloadService(config, null) causes nil pointer dereference in Go → SIGABRT",
        )
    }


    @Test
    fun `should SingboxRuntime stop clear commandServer and lastStatus`() {
        val root = locateRepoRoot()
        val runtime = File(
            root,
            "singbox-process/src/main/java/ru/ozero/singboxprocess/SingboxRuntime.kt",
        )
        assertTrue(runtime.isFile, "SingboxRuntime.kt must exist")
        val content = runtime.readText()

        val stopBlock = content.substringAfter("fun stop()")
            .substringBefore("fun ")

        assertTrue(
            stopBlock.contains("commandServer = null"),
            "SingboxRuntime.stop must null out commandServer — release Go runtime resources",
        )
        assertTrue(
            stopBlock.contains("lastStatus = null"),
            "SingboxRuntime.stop must null out lastStatus — stale status after stop is misleading",
        )
        assertTrue(
            stopBlock.contains("closeService()") || stopBlock.contains(".close()"),
            "SingboxRuntime.stop must call closeService/close before clearing",
        )
    }

    @Test
    fun `should SingboxRuntime stop closeService before close`() {
        val root = locateRepoRoot()
        val content = File(
            root,
            "singbox-process/src/main/java/ru/ozero/singboxprocess/SingboxRuntime.kt",
        ).readText()

        val stopBlock = content.substringAfter("fun stop()")
            .substringBefore("fun ")

        val closeServicePos = stopBlock.indexOf("closeService()")
        val closePos = stopBlock.indexOf("server.close()")
        assertTrue(
            closeServicePos >= 0 && closePos >= 0 && closeServicePos < closePos,
            "closeService must be called before close — stop TUN traffic before shutting down core",
        )
    }

    @Test
    fun `should SingboxRuntime start guard against double start`() {
        val root = locateRepoRoot()
        val content = File(
            root,
            "singbox-process/src/main/java/ru/ozero/singboxprocess/SingboxRuntime.kt",
        ).readText()

        val startBlock = content.substringAfter("fun start(")
            .substringBefore("fun stop(")

        assertTrue(
            startBlock.contains("check(") || startBlock.contains("require(") || startBlock.contains("already running"),
            "SingboxRuntime.start must guard against double-start — calling start twice leaks Go goroutines",
        )
    }

    @Test
    fun `should SingboxRuntime use Mutex not synchronized`() {
        val root = locateRepoRoot()
        val content = File(
            root,
            "singbox-process/src/main/java/ru/ozero/singboxprocess/SingboxRuntime.kt",
        ).readText()

        assertTrue(
            content.contains("Mutex") || content.contains("mutex"),
            "SingboxRuntime must use coroutine Mutex for suspend-safe locking — Java synchronized blocks block threads",
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
