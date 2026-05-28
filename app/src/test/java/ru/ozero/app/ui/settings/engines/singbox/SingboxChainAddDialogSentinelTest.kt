package ru.ozero.app.ui.settings.engines.singbox

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class SingboxChainAddDialogSentinelTest {

    @Test
    fun `chain add picker uses lazy bounded list`() {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "settings.gradle.kts").exists() }
        val source = File(
            root,
            "app/src/main/java/ru/ozero/app/ui/settings/engines/singbox/SingboxEngineSettingsScreen.kt",
        ).readText(Charsets.UTF_8)

        assertTrue(
            source.contains("showChainAddDialog") &&
                source.contains("LazyColumn") &&
                source.contains("heightIn(max = 360.dp)"),
            "Chain add must not compose every subscription profile in a popup on tap; large lists caused UI ANR",
        )
    }
}
