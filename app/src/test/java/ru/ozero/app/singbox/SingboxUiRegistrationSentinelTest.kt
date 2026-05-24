package ru.ozero.app.singbox

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class SingboxUiRegistrationSentinelTest {

    @Test
    fun `should TopScreen have SingboxSettings entry`() {
        val root = locateRepoRoot()
        val topScreen = File(root, "app/src/main/java/ru/ozero/app/ui/TopScreen.kt")
        assertTrue(topScreen.isFile, "TopScreen.kt must exist")
        val content = topScreen.readText()

        assertTrue(
            content.contains("SingboxSettings"),
            "TopScreen must have SingboxSettings enum entry — navigation destination for singbox settings screen",
        )
    }

    @Test
    fun `should RootNavigation map SINGBOX engineId to SingboxSettings`() {
        val root = locateRepoRoot()
        val nav = File(root, "app/src/main/java/ru/ozero/app/ui/RootNavigation.kt")
        assertTrue(nav.isFile, "RootNavigation.kt must exist")
        val content = nav.readText()

        assertTrue(
            content.contains("SINGBOX"),
            "RootNavigation must handle SINGBOX engineId",
        )
        assertTrue(
            content.contains("SingboxSettings"),
            "RootNavigation engineParamsTarget must route SINGBOX -> SingboxSettings",
        )
    }

    @Test
    fun `should RootNavigation register composable for SingboxSettings`() {
        val root = locateRepoRoot()
        val content = File(root, "app/src/main/java/ru/ozero/app/ui/RootNavigation.kt").readText()

        assertTrue(
            content.contains("SingboxEngineSettingsScreen"),
            "RootNavigation must composable-register SingboxEngineSettingsScreen",
        )
    }

    @Test
    fun `should SettingsScreen have onOpenSingboxSettings callback`() {
        val root = locateRepoRoot()
        val settings = File(root, "app/src/main/java/ru/ozero/app/ui/settings/SettingsScreen.kt")
        assertTrue(settings.isFile, "SettingsScreen.kt must exist")
        val content = settings.readText()

        assertTrue(
            content.contains("onOpenSingbox") || content.contains("singbox"),
            "SettingsScreen must wire onOpenSingboxSettings — without it tapping Sing-box row does nothing",
        )
    }

    @Test
    fun `should singbox settings strings exist in all 4 locales`() {
        val root = locateRepoRoot()
        val locales = listOf("values", "values-en", "values-es", "values-pt")
        val requiredKey = "settings_singbox_title"
        val missing = mutableListOf<String>()

        for (locale in locales) {
            val stringsFile = File(root, "app/src/main/res/$locale/strings_singbox.xml")
            if (!stringsFile.isFile || !stringsFile.readText().contains(requiredKey)) {
                missing.add(locale)
            }
        }

        assertTrue(
            missing.isEmpty(),
            "Sing-box strings missing in locales: $missing — translate.md requires ru/en/es/pt",
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
