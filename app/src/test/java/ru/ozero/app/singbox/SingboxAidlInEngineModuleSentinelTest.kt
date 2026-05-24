package ru.ozero.app.singbox

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingboxAidlInEngineModuleSentinelTest {

    @Test
    fun `should AIDL files live in engine-singbox not engines-core`() {
        val root = locateRepoRoot()
        val aidlDir = File(root, "engine-singbox/src/main/aidl/ru/ozero/enginesingbox")
        assertTrue(aidlDir.isDirectory, "AIDL dir engine-singbox/src/main/aidl/ru/ozero/enginesingbox/ must exist")

        val aidlFiles = aidlDir.listFiles { f -> f.extension == "aidl" } ?: emptyArray()
        assertTrue(
            aidlFiles.size >= 4,
            "Must have at least 4 AIDL files, found ${aidlFiles.size}",
        )
    }

    @Test
    fun `should no AIDL files in engines-core`() {
        val root = locateRepoRoot()
        val enginesCore = File(root, "engines-core/src/main/aidl")
        if (!enginesCore.isDirectory) return
        val aidlFiles = enginesCore.walkTopDown().filter { it.extension == "aidl" }.toList()
        assertTrue(
            aidlFiles.isEmpty(),
            "engines-core must NOT contain AIDL files (aidl=true scope pollution). Found:\n" +
                aidlFiles.joinToString("\n") { it.relativeTo(root).path },
        )
    }

    @Test
    fun `should engines-core build gradle not enable aidl`() {
        val root = locateRepoRoot()
        val buildFile = File(root, "engines-core/build.gradle.kts")
        if (!buildFile.isFile) return
        val content = buildFile.readText()
        assertFalse(
            content.contains("aidl = true"),
            "engines-core/build.gradle.kts must NOT have aidl = true — AIDL belongs only in engine-singbox",
        )
    }

    @Test
    fun `should engine-singbox build gradle enable aidl`() {
        val root = locateRepoRoot()
        val buildFile = File(root, "engine-singbox/build.gradle.kts")
        assertTrue(buildFile.isFile, "engine-singbox/build.gradle.kts must exist")
        val content = buildFile.readText()
        assertTrue(
            content.contains("aidl = true"),
            "engine-singbox/build.gradle.kts must have buildFeatures { aidl = true }",
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
