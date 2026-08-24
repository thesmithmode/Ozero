package ru.ozero.app.singbox

import java.io.File
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingboxLibboxDownloadContractTest {
    @Test
    fun `ci retries verified libbox downloads through one helper`() {
        val root = locateRepoRoot()
        val workflow = File(root, ".github/workflows/ci.yml").readText()
        val helper = File(root, ".github/scripts/download-libbox-aar.sh").readText()

        assertEquals(11, "download-libbox-aar.sh".toRegex().findAll(workflow).count())
        assertFalse(workflow.contains("gh release download singbox-1.13.12"))
        assertTrue(helper.contains("for attempt in 1 2 3 4 5"))
        assertTrue(helper.contains("rm -f \"\$destination\""))
        assertTrue(helper.contains("[ -s \"\$destination\" ]"))
        assertTrue(helper.contains("failed to download libbox.aar after 5 attempts"))
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
