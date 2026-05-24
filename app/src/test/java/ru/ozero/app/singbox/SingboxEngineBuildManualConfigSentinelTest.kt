package ru.ozero.app.singbox

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class SingboxEngineBuildManualConfigSentinelTest {

    @Test
    fun `should SingboxEngine override buildManualConfig`() {
        val content = engineFile().readText()
        assertTrue(
            content.contains("override fun buildManualConfig"),
            "SingboxEngine must override buildManualConfig — without it StartSequenceCoordinator " +
                "gets null and the engine can never start in manual mode",
        )
    }

    @Test
    fun `should buildManualConfig return EngineConfig Singbox`() {
        val content = engineFile().readText()
        assertTrue(
            content.contains("EngineConfig.Singbox"),
            "buildManualConfig must return EngineConfig.Singbox so ChainOrchestrator can build the VPN config",
        )
    }

    @Test
    fun `should SingboxEngine use DataStore BEAN_KEY for blob`() {
        val content = engineFile().readText()
        assertTrue(
            content.contains("BEAN_KEY") || content.contains("byteArrayPreferencesKey"),
            "SingboxEngine must read blob from DataStore with BEAN_KEY — same key as the settings ViewModel writes",
        )
    }

    @Test
    fun `should SingboxEngine inject SingboxPrefs DataStore`() {
        val content = engineFile().readText()
        assertTrue(
            content.contains("SingboxPrefs") && content.contains("DataStore"),
            "SingboxEngine must inject @SingboxPrefs DataStore<Preferences> to read the active bean blob",
        )
    }

    @Test
    fun `should SingboxPrefs qualifier live in engine-singbox module`() {
        val root = locateRepoRoot()
        val qualifierFile = File(
            root,
            "engine-singbox/src/main/java/ru/ozero/enginesingbox/SingboxPrefs.kt",
        )
        assertTrue(
            qualifierFile.isFile,
            "SingboxPrefs qualifier must be in engine-singbox module so SingboxEngine can reference it " +
                "without a circular dep on :app",
        )
    }

    private fun engineFile(): File {
        val root = locateRepoRoot()
        return File(
            root,
            "engine-singbox/src/main/java/ru/ozero/enginesingbox/SingboxEngine.kt",
        ).also { assertTrue(it.isFile, "SingboxEngine.kt must exist") }
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
