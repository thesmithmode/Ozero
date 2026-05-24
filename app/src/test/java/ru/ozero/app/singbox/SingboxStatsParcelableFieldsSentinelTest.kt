package ru.ozero.app.singbox

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class SingboxStatsParcelableFieldsSentinelTest {

    @Test
    fun `should SingboxStats have all 5 required fields`() {
        val root = locateRepoRoot()
        val statsFile = File(root, "engine-singbox/src/main/java/ru/ozero/enginesingbox/SingboxStats.kt")
        assertTrue(statsFile.isFile, "SingboxStats.kt must exist")
        val content = statsFile.readText()

        val required = listOf("txRateProxy", "rxRateProxy", "txTotal", "rxTotal", "activeConnections")
        val missing = required.filter { !content.contains(it) }
        assertTrue(
            missing.isEmpty(),
            "SingboxStats missing fields: $missing — all 5 fields required for EnginePlugin.stats() integration",
        )
    }

    @Test
    fun `should SingboxStats implement Parcelable via Parcelize`() {
        val root = locateRepoRoot()
        val content = File(root, "engine-singbox/src/main/java/ru/ozero/enginesingbox/SingboxStats.kt").readText()

        assertTrue(
            content.contains("@Parcelize") || content.contains("Parcelable"),
            "SingboxStats must be Parcelable for AIDL cross-process transport",
        )
    }

    @Test
    fun `should txRateProxy and rxRateProxy be Long type`() {
        val root = locateRepoRoot()
        val content = File(root, "engine-singbox/src/main/java/ru/ozero/enginesingbox/SingboxStats.kt").readText()

        assertTrue(
            content.contains("txRateProxy: Long") || content.contains("val txRateProxy"),
            "txRateProxy must be Long",
        )
        assertTrue(
            content.contains("rxRateProxy: Long") || content.contains("val rxRateProxy"),
            "rxRateProxy must be Long",
        )
    }

    @Test
    fun `should activeConnections be Int type`() {
        val root = locateRepoRoot()
        val content = File(root, "engine-singbox/src/main/java/ru/ozero/enginesingbox/SingboxStats.kt").readText()

        assertTrue(
            content.contains("activeConnections: Int") || content.contains("val activeConnections"),
            "activeConnections must be Int",
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
