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
    fun `should urlTest stub remain explicit failure until wired to routed probe`() {
        val root = locateRepoRoot()
        val service = File(
            root,
            "singbox-process/src/main/java/ru/ozero/singboxprocess/SingboxEngineService.kt",
        )
        val engine = File(
            root,
            "engine-singbox/src/main/java/ru/ozero/enginesingbox/SingboxEngine.kt",
        )
        assertTrue(service.isFile, "SingboxEngineService.kt must exist")
        assertTrue(engine.isFile, "SingboxEngine.kt must exist")
        val serviceContent = service.readText()
        val engineContent = engine.readText()

        assertTrue(
            serviceContent.contains("urlTest"),
            "SingboxEngineService must implement urlTest",
        )
        assertTrue(
            serviceContent.contains("override fun urlTest(profileId: Long): Long = -1"),
            "SingboxEngineService.urlTest stub must return -1 as an explicit failure until it is wired to routed probe",
        )
        assertTrue(
            !engineContent.contains("urlTest(") &&
                engineContent.contains("routedProbe.probeLatencyMs"),
            "SingboxEngine.probe must use routed HTTP probe and must not treat urlTest=-1 stub as success",
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
