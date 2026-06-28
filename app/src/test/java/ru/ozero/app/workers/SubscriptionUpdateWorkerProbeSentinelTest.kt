package ru.ozero.app.workers

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse

class SubscriptionUpdateWorkerProbeSentinelTest {

    @Test
    fun `subscription auto update must not run background singbox probes`() {
        val source = File(locateRepoRoot(), "app/src/main/java/ru/ozero/app/workers/SubscriptionUpdateWorker.kt")
            .readText()

        assertFalse(
            source.contains("probeAndAutoSelect"),
            "subscription auto-update must not probe refreshed profiles in background",
        )
        assertFalse(
            source.contains("getAutoCandidatesByGroupId"),
            "subscription auto-update must not load probe candidates from refreshed provider data",
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
