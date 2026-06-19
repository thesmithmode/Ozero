package ru.ozero.app

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class MtgExtractNativeLibsSentinelTest {

    @Test
    fun `ozero android application convention plugin extracts native libs`() {
        val plugin = locateConventionPlugin()
        val source = plugin.readText()
        assertTrue(
            source.contains("jniLibs.useLegacyPackaging = true"),
            "ozero.android.application.gradle.kts должен содержать " +
                "jniLibs.useLegacyPackaging = true. " +
                "subprocess-движки (masterdns: libmdnsvpn.so) запускаются через ProcessBuilder — " +
                "без extractNativeLibs=true .so не извлекается на диск → binary.exists() = false.",
        )
    }

    @Test
    fun `app manifest requests native lib extraction`() {
        val manifest = File(locateRepoRoot(), "app/src/main/AndroidManifest.xml").readText()
        assertTrue(
            manifest.contains("android:extractNativeLibs=\"true\""),
            "AndroidManifest должен явно включать extractNativeLibs для subprocess native binary.",
        )
    }

    private fun locateConventionPlugin(): File {
        val repoRoot = locateRepoRoot()
        val file = File(repoRoot, "buildSrc/src/main/kotlin/ozero.android.application.gradle.kts")
        check(file.isFile) { "ozero.android.application.gradle.kts не найден по пути ${file.absolutePath}" }
        return file
    }

    private fun locateRepoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(5) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile ?: return@repeat
        }
        error("repo root (settings.gradle.kts) не найден")
    }
}
