package ru.ozero.app.singbox

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class SingboxRuntimeReleaseSentinelTest {

    @Test
    fun `should SingboxRuntime stop clear both v2ray and tun2ray`() {
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
            stopBlock.contains("tun2ray = null"),
            "SingboxRuntime.stop must null out tun2ray — sentinel [feedback_go_runtime_guard_release]",
        )
        assertTrue(
            stopBlock.contains("v2ray = null"),
            "SingboxRuntime.stop must null out v2ray — sentinel [feedback_go_runtime_guard_release]",
        )
        assertTrue(
            stopBlock.contains("tun2ray?.close()") || stopBlock.contains(".close()"),
            "SingboxRuntime.stop must call tun2ray.close() before clearing",
        )
        assertTrue(
            stopBlock.contains("v2ray?.stop()") || stopBlock.contains(".stop()"),
            "SingboxRuntime.stop must call v2ray.stop() before clearing",
        )
    }

    @Test
    fun `should SingboxRuntime stop release before v2ray`() {
        val root = locateRepoRoot()
        val content = File(
            root,
            "singbox-process/src/main/java/ru/ozero/singboxprocess/SingboxRuntime.kt",
        ).readText()

        val stopBlock = content.substringAfter("fun stop()")
            .substringBefore("fun ")

        val tun2rayClosePos = stopBlock.indexOf("tun2ray")
        val v2rayStopPos = stopBlock.indexOf("v2ray?.stop()")
        assertTrue(
            tun2rayClosePos < v2rayStopPos || v2rayStopPos < 0,
            "tun2ray must be closed before v2ray.stop() — TUN must stop receiving before closing underlying core",
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
