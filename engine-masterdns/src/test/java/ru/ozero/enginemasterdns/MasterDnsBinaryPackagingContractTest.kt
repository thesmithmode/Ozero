package ru.ozero.enginemasterdns

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class MasterDnsBinaryPackagingContractTest {

    @Test
    fun `engine module downloads mdnsvpn subprocess binary`() {
        val buildFile = File(repoRoot(), "engine-masterdns/build.gradle.kts").readText()

        assertTrue(buildFile.contains("id(\"ozero.binaries\")"))
        assertTrue(buildFile.contains("artifact(\"libmdnsvpn-arm64-v8a.so\")"))
    }

    @Test
    fun `binary lock packages mdnsvpn as extracted native executable`() {
        val lock = File(repoRoot(), "build-tools/binaries.lock.yaml").readText()
        val block = lock.substringAfter("name: libmdnsvpn-arm64-v8a.so", missingDelimiterValue = "")
            .substringBefore("\n  - name:", missingDelimiterValue = "")

        assertTrue(block.isNotBlank())
        assertTrue(block.contains("engine: masterdns"))
        assertTrue(block.contains("abi: arm64-v8a"))
        assertTrue(block.contains("destination: jniLibs"))
        assertTrue(block.contains("target_filename: libmdnsvpn.so"))
        assertTrue(block.contains("source_commit: 27c7e11ce9eb51d7db36b34188502e524a3184db"))
    }

    private fun repoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(5) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile ?: return@repeat
        }
        error("repo root not found")
    }
}
