package binaries

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class GenerateTorChecksumsTaskTest {

    @TempDir
    lateinit var tmp: Path

    private fun writeLock(): Path {
        val lock = tmp.resolve("binaries.lock.yaml")
        Files.writeString(
            lock,
            """
            tag: tor-test
            generated_at: 2026-04-26T00:00:00Z
            artifacts:
              - name: libtor-arm64-v8a.so
                engine: tor
                abi: arm64-v8a
                destination: jniLibs
                download_url: https://example.invalid/libtor-arm64-v8a.so
                sha256: ${"a".repeat(64)}
                size_bytes: 1
                source_repo: https://example.invalid/tor
                source_commit: tor-1
              - name: libtor-x86.so
                engine: tor
                abi: x86
                destination: jniLibs
                download_url: https://example.invalid/libtor-x86.so
                sha256: ${"b".repeat(64)}
                size_bytes: 1
                source_repo: https://example.invalid/tor
                source_commit: tor-1
              - name: libiptproxy-arm64-v8a.so
                engine: iptproxy
                abi: arm64-v8a
                destination: jniLibs
                download_url: https://example.invalid/libiptproxy-arm64-v8a.so
                sha256: ${"c".repeat(64)}
                size_bytes: 1
                source_repo: https://example.invalid/ipt
                source_commit: ipt-1
              - name: libbyedpi-arm64-v8a.so
                engine: byedpi
                abi: arm64-v8a
                destination: jniLibs
                download_url: https://example.invalid/libbyedpi-arm64-v8a.so
                sha256: ${"d".repeat(64)}
                size_bytes: 1
                source_repo: https://example.invalid/byedpi
                source_commit: byedpi-1
            """.trimIndent(),
        )
        return lock
    }

    @Test
    fun `generates Kotlin object with tor and iptproxy checksums grouped by abi`() {
        val lock = writeLock()
        val project = ProjectBuilder.builder().withProjectDir(tmp.toFile()).build()
        val outDir = tmp.resolve("gen").toFile()

        val task = project.tasks.register(
            "generateTorChecksums",
            GenerateTorChecksumsTask::class.java,
        ) { t ->
            t.lockFile.set(lock.toFile())
            t.outputDir.set(outDir)
        }.get()

        task.run()

        val generated = outDir.resolve(
            "ru/ozero/enginetor/dynamicmod/TorBinaryChecksums.kt",
        )
        assertThat(generated).exists()
        val content = generated.readText()
        assertThat(content).contains("package ru.ozero.enginetor.dynamicmod")
        assertThat(content).contains("object TorBinaryChecksums")
        assertThat(content).contains("\"arm64-v8a\"")
        assertThat(content).contains("\"x86\"")
        assertThat(content).contains("libtor-arm64-v8a.so")
        assertThat(content).contains("libiptproxy-arm64-v8a.so")
        assertThat(content).contains("a".repeat(64))
        assertThat(content).contains("c".repeat(64))
                assertThat(content).doesNotContain("libbyedpi")
        assertThat(content).doesNotContain("d".repeat(64))
    }

    @Test
    fun `generates entries for all four ABIs when present`() {
        val lock = tmp.resolve("binaries.lock.yaml")
        val abis = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        val sb = StringBuilder()
        sb.appendLine("tag: t")
        sb.appendLine("generated_at: 2026-04-26T00:00:00Z")
        sb.appendLine("artifacts:")
        for (a in abis) {
            sb.appendLine("  - name: libtor-$a.so")
            sb.appendLine("    engine: tor")
            sb.appendLine("    abi: $a")
            sb.appendLine("    destination: jniLibs")
            sb.appendLine("    download_url: https://example.invalid/libtor-$a.so")
            sb.appendLine("    sha256: ${"f".repeat(64)}")
            sb.appendLine("    size_bytes: 1")
            sb.appendLine("    source_repo: https://example.invalid/tor")
            sb.appendLine("    source_commit: tor-1")
        }
        Files.writeString(lock, sb.toString())

        val project = ProjectBuilder.builder().withProjectDir(tmp.toFile()).build()
        val outDir = tmp.resolve("gen").toFile()
        val task = project.tasks.register(
            "generateTorChecksums",
            GenerateTorChecksumsTask::class.java,
        ) { t ->
            t.lockFile.set(lock.toFile())
            t.outputDir.set(outDir)
        }.get()

        task.run()

        val content =
            outDir.resolve("ru/ozero/enginetor/dynamicmod/TorBinaryChecksums.kt").readText()
        for (a in abis) {
            assertThat(content).contains("\"$a\"")
            assertThat(content).contains("libtor-$a.so")
        }
    }
}
