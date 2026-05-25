package ru.ozero.app.singbox

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingboxLibLoadSentinelTest {

    @Test
    fun `should Libsingboxgojni load box not gojni`() {
        val root = locateRepoRoot()
        val libFile = File(root, "singbox-core/src/main/java/ru/ozero/singboxcore/Libsingboxgojni.kt")
        assertTrue(libFile.isFile, "Libsingboxgojni.kt must exist")
        val content = libFile.readText()

        val msg = "Libsingboxgojni must load 'box' (upstream libbox.so) — " +
            "'gojni' would clash with URnetwork's libgojni.so causing SIGABRT"
        assertTrue(content.contains("System.loadLibrary(\"box\")"), msg)
        assertFalse(
            content.contains("System.loadLibrary(\"gojni\")"),
            "Libsingboxgojni must NOT load 'gojni' — this conflicts with URnetwork process",
        )
    }

    @Test
    fun `should loadOnce use volatile double-check pattern`() {
        val root = locateRepoRoot()
        val content = File(root, "singbox-core/src/main/java/ru/ozero/singboxcore/Libsingboxgojni.kt").readText()

        assertTrue(
            content.contains("loadAttempted"),
            "loadOnce must use loadAttempted volatile flag for double-check",
        )
        assertTrue(
            content.contains("synchronized"),
            "loadOnce must use synchronized block for thread-safety",
        )
    }

    @Test
    fun `should SingboxEngineService call loadOnce in onCreate`() {
        val root = locateRepoRoot()
        val serviceFile = File(
            root,
            "singbox-process/src/main/java/ru/ozero/singboxprocess/SingboxEngineService.kt",
        )
        assertTrue(serviceFile.isFile, "SingboxEngineService.kt must exist")
        val content = serviceFile.readText()

        assertTrue(
            content.contains("Libsingboxgojni.loadOnce()"),
            "SingboxEngineService.onCreate must call Libsingboxgojni.loadOnce() — " +
                "library must be loaded in :engine_singbox process before any JNI calls",
        )
        val onCreateBlock = content.substringAfter("override fun onCreate()").substringBefore("override fun ")
        assertTrue(
            onCreateBlock.contains("loadOnce()"),
            "loadOnce() must be in onCreate, not deferred to first API call",
        )
    }

    @Test
    fun `should singboxgojni not loaded in OzeroApp main process unconditionally`() {
        val root = locateRepoRoot()
        val ozeroApp = File(root, "app/src/main/java/ru/ozero/app/OzeroApp.kt").readText()

        assertFalse(
            ozeroApp.contains("loadLibrary(\"singboxgojni\")"),
            "singboxgojni must NEVER load in main process — it is a Go runtime for :engine_singbox only",
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
