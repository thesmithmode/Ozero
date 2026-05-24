package ru.ozero.app.singbox

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class SingboxAidlPerAppStubSentinelTest {

    @Test
    fun `should setPerAppPackages be declared as oneway in AIDL`() {
        val root = locateRepoRoot()
        val aidl = File(root, "engine-singbox/src/main/aidl/ru/ozero/enginesingbox/ISingboxEngineProcess.aidl")
        assertTrue(aidl.isFile, "ISingboxEngineProcess.aidl must exist")
        val content = aidl.readText()

        assertTrue(
            content.contains("setPerAppPackages"),
            "ISingboxEngineProcess must declare setPerAppPackages",
        )
        val setPerAppLine = content.lines().first { it.contains("setPerAppPackages") }
        assertTrue(
            setPerAppLine.contains("oneway"),
            "setPerAppPackages must be declared as 'oneway' — P1 stub, returns nothing, " +
                "oneway prevents client blocking on unimplemented P6 feature",
        )
    }

    @Test
    fun `should SingboxEngineService setPerAppPackages log warning not throw`() {
        val root = locateRepoRoot()
        val service = File(
            root,
            "singbox-process/src/main/java/ru/ozero/singboxprocess/SingboxEngineService.kt",
        )
        assertTrue(service.isFile, "SingboxEngineService.kt must exist")
        val content = service.readText()

        assertTrue(
            content.contains("setPerAppPackages"),
            "SingboxEngineService must implement setPerAppPackages",
        )
        assertTrue(
            content.contains("per-app routing not yet implemented"),
            "setPerAppPackages stub must log 'per-app routing not yet implemented' — " +
                "oneway ignores return but log ensures observability of P1 stub calls",
        )
    }

    @Test
    fun `should setPerAppPackages accept String array and boolean`() {
        val root = locateRepoRoot()
        val aidl = File(root, "engine-singbox/src/main/aidl/ru/ozero/enginesingbox/ISingboxEngineProcess.aidl")
        val content = aidl.readText()
        val line = content.lines().firstOrNull { it.contains("setPerAppPackages") } ?: ""

        assertTrue(line.contains("String[]") || line.contains("String["), "setPerAppPackages must accept String[]")
        assertTrue(line.contains("boolean"), "setPerAppPackages must accept boolean isAllowList")
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
