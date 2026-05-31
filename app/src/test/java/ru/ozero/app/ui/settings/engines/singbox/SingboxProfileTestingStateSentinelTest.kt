package ru.ozero.app.ui.settings.engines.singbox

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class SingboxProfileTestingStateSentinelTest {

    @Test
    fun `settings screen renders per profile testing state`() {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "settings.gradle.kts").exists() }
        val viewModelSource = File(
            root,
            "app/src/main/java/ru/ozero/app/ui/settings/engines/singbox/SingboxEngineSettingsViewModel.kt",
        ).readText(Charsets.UTF_8)
        val screenSource = File(
            root,
            "app/src/main/java/ru/ozero/app/ui/settings/engines/singbox/SingboxEngineSettingsScreen.kt",
        ).readText(Charsets.UTF_8)

        assertTrue(
            viewModelSource.contains("testingProfileIds") &&
                viewModelSource.contains("onProfileTestingChanged"),
            "ViewModel must expose per-profile testing state, not only group-level ping state",
        )
        assertTrue(
            screenSource.contains("isTesting = profile.id in testingProfileIds") &&
                screenSource.contains("CircularProgressIndicator"),
            "Each profile row must show that the specific server is being tested",
        )
    }
}
