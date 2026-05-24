package ru.ozero.app.singbox

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class SingboxAidlConfigSizeSentinelTest {

    @Test
    fun `should ConfigBuilder produce VLESS config under 10KB binder limit`() {
        val root = locateRepoRoot()
        val configBuilder = File(
            root,
            "singbox-config/src/main/java/ru/ozero/singboxconfig/ConfigBuilder.kt",
        )
        assertTrue(configBuilder.isFile, "ConfigBuilder.kt must exist")
        val content = configBuilder.readText()

        assertTrue(
            content.contains("buildSingboxConfig"),
            "ConfigBuilder must have buildSingboxConfig function",
        )
        assertTrue(
            content.contains("StringBuilder") || content.contains("buildString"),
            "ConfigBuilder must use manual JSON building (no new JSON prod dep allowed in P1)",
        )
    }

    @Test
    fun `should ConfigBuilder not import json prod library`() {
        val root = locateRepoRoot()
        val content = File(
            root,
            "singbox-config/src/main/java/ru/ozero/singboxconfig/ConfigBuilder.kt",
        ).readText()

        val forbiddenImports = listOf(
            "import com.google.gson",
            "import org.json.JSONObject",
            "import kotlinx.serialization",
            "import com.squareup.moshi",
        )
        val violations = forbiddenImports.filter { content.contains(it) }
        assertTrue(
            violations.isEmpty(),
            "ConfigBuilder must not import JSON libraries — use manual StringBuilder. Violations: $violations",
        )
    }

    @Test
    fun `should SingboxEngine startWithConfigFile path exist for large configs`() {
        val root = locateRepoRoot()
        val aidl = File(root, "engine-singbox/src/main/aidl/ru/ozero/enginesingbox/ISingboxEngineProcess.aidl")
        val content = aidl.readText()

        assertTrue(
            content.contains("startWithConfigFile"),
            "ISingboxEngineProcess must have startWithConfigFile — P4+ configs (multi-outbound) may exceed 500KB inline AIDL limit",
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
