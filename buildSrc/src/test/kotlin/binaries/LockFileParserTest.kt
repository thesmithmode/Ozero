package binaries

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class LockFileParserTest {
    @TempDir
    lateinit var tmp: Path

    private fun write(content: String): Path {
        val f = tmp.resolve("binaries.lock.yaml")
        Files.writeString(f, content)
        return f
    }

    @Test
    fun `parse valid single artifact`() {
        val f = write(
            """
            tag: binaries-abc12345
            generated_at: 2026-04-25T10:00:00Z
            artifacts:
              - name: libbyedpi-arm64-v8a.so
                engine: byedpi
                abi: arm64-v8a
                destination: jniLibs
                download_url: https://example.com/libbyedpi.so
                sha256: 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef
                size_bytes: 285184
                source_repo: https://github.com/hufrea/byedpi
                source_commit: 1111111111111111111111111111111111111111
            """.trimIndent(),
        )
        val lock = LockFileParser.parse(f)
        assertThat(lock.tag).isEqualTo("binaries-abc12345")
        assertThat(lock.artifacts).hasSize(1)
        with(lock.artifacts[0]) {
            assertThat(name).isEqualTo("libbyedpi-arm64-v8a.so")
            assertThat(engine).isEqualTo("byedpi")
            assertThat(abi).isEqualTo("arm64-v8a")
            assertThat(destination).isEqualTo(Destination.JNI_LIBS)
            assertThat(sha256).hasSize(64)
            assertThat(sizeBytes).isEqualTo(285184L)
            assertThat(sourceCommit).isEqualTo("1111111111111111111111111111111111111111")
        }
    }

    @Test
    fun `parse preserves generated at source repo and lookup by name`() {
        val f = write(
            """
            tag: binaries-meta
            generated_at: 2026-04-25T10:00:00Z
            artifacts:
              - name: libone.so
                engine: one
                abi: arm64-v8a
                destination: jniLibs
                download_url: https://example.com/libone.so
                sha256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
                size_bytes: 1
                source_repo: https://example.com/one
                source_commit: 1111111111111111111111111111111111111111
            """.trimIndent(),
        )

        val lock = LockFileParser.parse(f)

        assertThat(lock.generatedAt).isEqualTo("2026-04-25T10:00:00Z")
        assertThat(lock.findByName("libone.so")?.sourceRepo).isEqualTo("https://example.com/one")
        assertThat(lock.findByName("missing.so")).isNull()
    }

    @Test
    fun `parse converts yaml timestamp generated at to instant string`() {
        val f = write(
            """
            tag: binaries-meta
            generated_at: 2026-04-25T10:00:00Z
            artifacts:
              - name: libone.so
                engine: one
                abi: arm64-v8a
                destination: jniLibs
                download_url: https://example.com/libone.so
                sha256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
                size_bytes: 1
                source_repo: https://example.com/one
                source_commit: 1111111111111111111111111111111111111111
            """.trimIndent(),
        )

        val lock = LockFileParser.parse(f)

        assertThat(lock.generatedAt).isEqualTo("2026-04-25T10:00:00Z")
    }

    @Test
    fun `parse preserves plain string generated at without coercion`() {
        val f = write(
            """
            tag: binaries-meta
            generated_at: custom-build-marker
            artifacts:
              - name: libone.so
                engine: one
                abi: arm64-v8a
                destination: jniLibs
                download_url: https://example.com/libone.so
                sha256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
                size_bytes: 1
                source_repo: https://example.com/one
                source_commit: 1111111111111111111111111111111111111111
            """.trimIndent(),
        )

        val lock = LockFileParser.parse(f)

        assertThat(lock.generatedAt).isEqualTo("custom-build-marker")
    }

    @Test
    fun `parse coerces numeric tag to string`() {
        val f = write(
            """
            tag: 123
            generated_at: 2026-04-25T10:00:00Z
            artifacts: []
            """.trimIndent(),
        )

        val lock = LockFileParser.parse(f)

        assertThat(lock.tag).isEqualTo("123")
    }

    @Test
    fun `parse converts unquoted yaml timestamp generated at to instant string`() {
        val f = write(
            """
            tag: binaries-meta
            generated_at: 2026-04-25 10:00:00Z
            artifacts:
              - name: libone.so
                engine: one
                abi: arm64-v8a
                destination: jniLibs
                download_url: https://example.com/libone.so
                sha256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
                size_bytes: 1
                source_repo: https://example.com/one
                source_commit: 1111111111111111111111111111111111111111
            """.trimIndent(),
        )

        val lock = LockFileParser.parse(f)

        assertThat(lock.generatedAt).startsWith("2026-04-25T10:00:00")
    }

    @Test
    fun `parse converts yaml date generated at to instant string`() {
        val f = write(
            """
            tag: binaries-meta
            generated_at: 2026-04-25
            artifacts:
              - name: libone.so
                engine: one
                abi: arm64-v8a
                destination: jniLibs
                download_url: https://example.com/libone.so
                sha256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
                size_bytes: 1
                source_repo: https://example.com/one
                source_commit: 1111111111111111111111111111111111111111
            """.trimIndent(),
        )

        val lock = LockFileParser.parse(f)

        assertThat(lock.generatedAt).startsWith("2026-04-25T00:00:00")
    }

    @Test
    fun `parse converts non string non date generated at using toString`() {
        val f = write(
            """
            tag: binaries-meta
            generated_at: 20260425
            artifacts:
              - name: libone.so
                engine: one
                abi: arm64-v8a
                destination: jniLibs
                download_url: https://example.com/libone.so
                sha256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
                size_bytes: 1
                source_repo: https://example.com/one
                source_commit: 1111111111111111111111111111111111111111
            """.trimIndent(),
        )

        val lock = LockFileParser.parse(f)

        assertThat(lock.generatedAt).isEqualTo("20260425")
    }

    @Test
    fun `parse AAR with libs destination and no abi`() {
        val f = write(
            """
            tag: binaries-deadbeef
            generated_at: 2026-04-25T10:00:00Z
            artifacts:
              - name: libxray.aar
                engine: xray
                destination: libs
                download_url: https://example.com/libxray.aar
                sha256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
                size_bytes: 15728640
                source_repo: https://github.com/XTLS/Xray-core
                source_commit: 2222222222222222222222222222222222222222
            """.trimIndent(),
        )
        val lock = LockFileParser.parse(f)
        assertThat(lock.artifacts[0].destination).isEqualTo(Destination.LIBS)
        assertThat(lock.artifacts[0].abi).isNull()
    }

    @Test
    fun `parse target filename and string size`() {
        val f = write(
            """
            tag: binaries-deadbeef
            generated_at: 2026-04-25T10:00:00Z
            artifacts:
              - name: original.aar
                target_filename: renamed.aar
                engine: xray
                destination: libs
                download_url: https://example.com/original.aar
                sha256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
                size_bytes: "15728640"
                source_repo: https://github.com/XTLS/Xray-core
                source_commit: 2222222222222222222222222222222222222222
            """.trimIndent(),
        )
        val artifact = LockFileParser.parse(f).artifacts.single()
        assertThat(artifact.targetFilename).isEqualTo("renamed.aar")
        assertThat(artifact.sizeBytes).isEqualTo(15_728_640L)
    }

    @Test
    fun `blank target filename falls back to artifact name`() {
        val f = write(
            """
            tag: binaries-deadbeef
            generated_at: 2026-04-25T10:00:00Z
            artifacts:
              - name: original.aar
                target_filename: " "
                engine: xray
                destination: libs
                download_url: https://example.com/original.aar
                sha256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
                size_bytes: 15728640
                source_repo: https://github.com/XTLS/Xray-core
                source_commit: 2222222222222222222222222222222222222222
            """.trimIndent(),
        )
        assertThat(LockFileParser.parse(f).artifacts.single().targetFilename).isNull()
    }

    @Test
    fun `parse lock with absent artifacts as empty list`() {
        val f = write(
            """
            tag: binaries-empty
            generated_at: 2026-04-25T10:00:00Z
            """.trimIndent(),
        )
        assertThat(LockFileParser.parse(f).artifacts).isEmpty()
    }

    @Test
    fun `parse lock with non list artifacts as empty list`() {
        val f = write(
            """
            tag: binaries-empty
            generated_at: 2026-04-25T10:00:00Z
            artifacts:
              byedpi: libbyedpi.so
            """.trimIndent(),
        )
        assertThat(LockFileParser.parse(f).artifacts).isEmpty()
    }
}
