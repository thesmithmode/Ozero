package ru.ozero.app.singbox

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingboxSettingsScreenTimeoutSentinelTest {

    @Test
    fun `main singbox settings screen does not render probe timeout field`() {
        val text = locateFile(
            "app/src/main/java/ru/ozero/app/ui/settings/engines/singbox/SingboxEngineSettingsScreen.kt",
        ).readText()
        val contentBlock = text
            .substringAfter("private fun SingboxSettingsContent(")
            .substringBefore("\n@Composable\nprivate fun SingboxChainSection")

        assertFalse(contentBlock.contains("singbox_probe_timeout_label"))
        assertFalse(contentBlock.contains("onProbeTimeoutSecondsChange"))
    }

    @Test
    fun `advanced singbox settings screen keeps probe timeout field`() {
        val text = locateFile(
            "app/src/main/java/ru/ozero/app/ui/settings/engines/singbox/SingboxAdvancedSettingsScreen.kt",
        ).readText()

        assertTrue(text.contains("ProbeTimeoutSection"))
        assertTrue(text.contains("singbox_probe_timeout_label"))
    }

    private fun locateFile(relativePath: String): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(5) {
            if (File(dir, "settings.gradle.kts").isFile) return File(dir, relativePath)
            dir = dir.parentFile ?: return@repeat
        }
        error("repo root not found")
    }
}
