package ru.ozero.app.singbox

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class SingboxManifestProcessSentinelTest {

    @Test
    fun `should app manifest declare SingboxEngineService in engine_singbox process`() {
        val root = locateRepoRoot()
        val manifest = File(root, "app/src/main/AndroidManifest.xml")
        assertTrue(manifest.isFile, "app/src/main/AndroidManifest.xml must exist")
        val content = manifest.readText()

        assertTrue(
            content.contains("SingboxEngineService"),
            "AndroidManifest.xml must declare SingboxEngineService",
        )
        assertTrue(
            content.contains(":engine_singbox"),
            "SingboxEngineService must run in :engine_singbox process",
        )
        assertTrue(
            content.contains("android:exported=\"false\""),
            "SingboxEngineService must not be exported",
        )
    }

    @Test
    fun `should SingboxEngineService be declared in app manifest not in singbox-process manifest`() {
        val root = locateRepoRoot()
        val appManifest = File(root, "app/src/main/AndroidManifest.xml")
        val processManifest = File(root, "singbox-process/src/main/AndroidManifest.xml")

        assertTrue(appManifest.readText().contains("SingboxEngineService"))

        if (processManifest.isFile) {
            val processContent = processManifest.readText()
            val noSingboxService = !processContent.contains("SingboxEngineService")
            val emptyOrServiceless = processContent.trim().let { t ->
                t == "<?xml version=\"1.0\" encoding=\"utf-8\"?><manifest/>" ||
                    t.contains("<manifest/>") ||
                    !t.contains("<service")
            }
            assertTrue(
                noSingboxService || emptyOrServiceless,
                "SingboxEngineService should only be in app manifest, not singbox-process",
            )
        }
    }

    @Test
    fun `should engine_singbox process name match guard in OzeroApp`() {
        val root = locateRepoRoot()
        val ozeroApp = File(root, "app/src/main/java/ru/ozero/app/OzeroApp.kt")
        assertTrue(ozeroApp.isFile, "OzeroApp.kt must exist")
        val content = ozeroApp.readText()

        assertTrue(
            content.contains(":engine_singbox"),
            "OzeroApp.kt process name guard must match ':engine_singbox' — " +
                "must equal android:process attribute in AndroidManifest.xml",
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
