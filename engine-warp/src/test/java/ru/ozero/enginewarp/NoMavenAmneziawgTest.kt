package ru.ozero.enginewarp

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.io.File

class NoMavenAmneziawgTest {

    @Test
    fun engineWarpBuildScriptDoesNotReferenceMavenAmneziawg() {
        val candidates = listOf(File("build.gradle.kts"), File("engine-warp/build.gradle.kts"))
        val file = candidates.firstOrNull { it.exists() }
            ?: error("engine-warp/build.gradle.kts not found, looked at: ${candidates.map { it.absolutePath }}")
        val text = file.readText()
        assertFalse(
            text.contains("amneziawg-android") || text.contains("zaneschepke"),
            "engine-warp/build.gradle.kts must not reference maven amneziawg-android (crashes on raw INI with I1 blob); " +
                "use checked-in libam-go.so from PORTAL_WG_v1.4.3 instead. Found in:\n$text",
        )
    }

    @Test
    fun versionsCatalogDoesNotReferenceMavenAmneziawg() {
        val candidates = listOf(File("../gradle/libs.versions.toml"), File("gradle/libs.versions.toml"))
        val file = candidates.firstOrNull { it.exists() }
            ?: error("gradle/libs.versions.toml not found, looked at: ${candidates.map { it.absolutePath }}")
        val text = file.readText()
        assertFalse(
            text.contains("amneziawg-android") || text.contains("zaneschepke") || text.contains("amneziawgAndroid"),
            "gradle/libs.versions.toml must not reference amneziawg-android maven dep",
        )
    }
}
