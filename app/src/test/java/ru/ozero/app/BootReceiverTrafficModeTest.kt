package ru.ozero.app

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class BootReceiverTrafficModeTest {

    @Test
    fun `boot autostart skips VpnService prepare in proxy mode`() {
        val source = File(locateRepoRoot(), "app/src/main/java/ru/ozero/app/BootReceiver.kt").readText()

        assertTrue(source.contains("@AndroidEntryPoint"))
        assertTrue(source.contains("lateinit var settingsDataStore"))
        assertTrue(source.contains("currentTrafficMode() == TrafficMode.TUN && VpnService.prepare(context) != null"))
    }

    private fun locateRepoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(5) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile ?: return@repeat
        }
        error("repo root not found from ${System.getProperty("user.dir")}")
    }
}
