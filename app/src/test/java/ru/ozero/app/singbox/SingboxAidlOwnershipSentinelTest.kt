package ru.ozero.app.singbox

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingboxAidlOwnershipSentinelTest {

    @Test
    fun `AIDL exposes only capabilities owned by isolated runtime`() {
        val root = locateRepoRoot()
        val aidl = File(
            root,
            "engine-singbox/src/main/aidl/ru/ozero/enginesingbox/ISingboxEngineProcess.aidl",
        ).readText()
        val routedProbe = File(
            root,
            "app/src/main/java/ru/ozero/app/ui/settings/engines/singbox/SingboxProbeService.kt",
        ).readText()
        val splitRouting = File(
            root,
            "common-vpn/src/main/java/ru/ozero/commonvpn/split/TunBuilderConfigurator.kt",
        ).readText()

        assertFalse(aidl.contains("registerStatusCallback"))
        assertFalse(aidl.contains("urlTest"))
        assertFalse(aidl.contains("setPerAppPackages"))
        assertTrue(routedProbe.contains("SingboxHttp204RoutedProbe"))
        assertTrue(splitRouting.contains("addAllowedApplication"))
        assertTrue(splitRouting.contains("addDisallowedApplication"))
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
