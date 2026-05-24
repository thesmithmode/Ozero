package ru.ozero.app.singbox

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class SingboxAidlUrlTestMethodSentinelTest {

    @Test
    fun `should urlTest accept long profileId not just url`() {
        val root = locateRepoRoot()
        val aidl = File(root, "engine-singbox/src/main/aidl/ru/ozero/enginesingbox/ISingboxEngineProcess.aidl")
        assertTrue(aidl.isFile, "ISingboxEngineProcess.aidl must exist")
        val content = aidl.readText()

        assertTrue(
            content.contains("urlTest"),
            "ISingboxEngineProcess must have urlTest method",
        )
        assertTrue(
            content.contains("long profileId"),
            "urlTest must accept 'long profileId' — required for P5 per-profile latency. " +
                "urlTest(String url) alone would measure wrong outbound. " +
                "profileId selects which outbound tag to test in multi-outbound config.",
        )
    }

    @Test
    fun `should SingboxEngineService implement urlTest returning -1 stub`() {
        val root = locateRepoRoot()
        val service = File(
            root,
            "singbox-process/src/main/java/ru/ozero/singboxprocess/SingboxEngineService.kt",
        )
        assertTrue(service.isFile, "SingboxEngineService.kt must exist")
        val content = service.readText()

        assertTrue(
            content.contains("urlTest"),
            "SingboxEngineService must implement urlTest",
        )
        assertTrue(
            content.contains("-1"),
            "SingboxEngineService.urlTest stub must return -1 (P1 stub, full impl in P5)",
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
