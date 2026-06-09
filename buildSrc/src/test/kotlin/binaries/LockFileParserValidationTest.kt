package binaries

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class LockFileParserValidationTest {
    @TempDir
    lateinit var tmp: Path

    private fun write(content: String): Path {
        val f = tmp.resolve("binaries.lock.yaml")
        Files.writeString(f, content)
        return f
    }

    @Test
    fun `reject scalar lock root`() {
        val f = write("not-a-map")
        assertThatThrownBy { LockFileParser.parse(f) }
            .isInstanceOf(LockFileException::class.java)
            .hasMessageContaining("root")
    }

    @Test
    fun `reject list lock root`() {
        val f = write(
            """
            - tag: binaries-x
            - generated_at: 2026-04-25T10:00:00Z
            """.trimIndent(),
        )
        assertThatThrownBy { LockFileParser.parse(f) }
            .isInstanceOf(LockFileException::class.java)
            .hasMessageContaining("root")
    }

    @Test
    fun `reject missing tag field`() {
        val f = write("generated_at: 2026-04-25T10:00:00Z\nartifacts: []\n")
        assertThatThrownBy { LockFileParser.parse(f) }
            .isInstanceOf(LockFileException::class.java)
            .hasMessageContaining("tag")
    }

    @Test
    fun `reject missing generated at field`() {
        val f = write("tag: binaries-x\nartifacts: []\n")
        assertThatThrownBy { LockFileParser.parse(f) }
            .isInstanceOf(LockFileException::class.java)
            .hasMessageContaining("generated_at")
    }

    @Test
    fun `reject missing sha256 on artifact`() {
        val f = write(
            """
            tag: binaries-x
            generated_at: 2026-04-25T10:00:00Z
            artifacts:
              - name: libbyedpi-arm64-v8a.so
                engine: byedpi
                abi: arm64-v8a
                destination: jniLibs
                download_url: https://example.com/x.so
                size_bytes: 100
                source_repo: https://github.com/hufrea/byedpi
                source_commit: 4444444444444444444444444444444444444444
            """.trimIndent(),
        )
        assertThatThrownBy { LockFileParser.parse(f) }
            .isInstanceOf(LockFileException::class.java)
            .hasMessageContaining("sha256")
    }

    @Test
    fun `reject duplicate artifact names`() {
        val f = write(
            """
            tag: binaries-x
            generated_at: 2026-04-25T10:00:00Z
            artifacts:
              - name: libbyedpi-arm64-v8a.so
                engine: byedpi
                abi: arm64-v8a
                destination: jniLibs
                download_url: https://example.com/a.so
                sha256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
                size_bytes: 1
                source_repo: https://example.com
                source_commit: 5555555555555555555555555555555555555555
              - name: libbyedpi-arm64-v8a.so
                engine: byedpi
                abi: arm64-v8a
                destination: jniLibs
                download_url: https://example.com/b.so
                sha256: bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
                size_bytes: 1
                source_repo: https://example.com
                source_commit: 6666666666666666666666666666666666666666
            """.trimIndent(),
        )
        assertThatThrownBy { LockFileParser.parse(f) }
            .isInstanceOf(LockFileException::class.java)
            .hasMessageContaining("Duplicate")
    }

    @Test
    fun `reject jniLibs artifact without abi`() {
        val f = write(
            """
            tag: binaries-x
            generated_at: 2026-04-25T10:00:00Z
            artifacts:
              - name: libbyedpi.so
                engine: byedpi
                destination: jniLibs
                download_url: https://example.com/x.so
                sha256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
                size_bytes: 1
                source_repo: https://example.com
                source_commit: 7777777777777777777777777777777777777777
            """.trimIndent(),
        )
        assertThatThrownBy { LockFileParser.parse(f) }
            .isInstanceOf(LockFileException::class.java)
            .hasMessageContaining("abi")
    }

    @Test
    fun `reject jniLibs artifact with blank abi`() {
        val f = write(
            """
            tag: binaries-x
            generated_at: 2026-04-25T10:00:00Z
            artifacts:
              - name: libbyedpi.so
                engine: byedpi
                abi: " "
                destination: jniLibs
                download_url: https://example.com/x.so
                sha256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
                size_bytes: 1
                source_repo: https://example.com
                source_commit: 7777777777777777777777777777777777777777
            """.trimIndent(),
        )
        assertThatThrownBy { LockFileParser.parse(f) }
            .isInstanceOf(LockFileException::class.java)
            .hasMessageContaining("abi")
    }

    @Test
    fun `reject unknown destination value`() {
        val f = write(
            """
            tag: binaries-x
            generated_at: 2026-04-25T10:00:00Z
            artifacts:
              - name: libx.so
                engine: x
                abi: arm64-v8a
                destination: somewhere_else
                download_url: https://example.com/x.so
                sha256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
                size_bytes: 1
                source_repo: https://example.com
                source_commit: 8888888888888888888888888888888888888888
            """.trimIndent(),
        )
        assertThatThrownBy { LockFileParser.parse(f) }
            .isInstanceOf(LockFileException::class.java)
            .hasMessageContaining("destination")
    }

    @Test
    fun `reject sha256 with wrong length`() {
        val f = write(
            """
            tag: binaries-x
            generated_at: 2026-04-25T10:00:00Z
            artifacts:
              - name: libx.so
                engine: x
                abi: arm64-v8a
                destination: jniLibs
                download_url: https://example.com/x.so
                sha256: deadbeef
                size_bytes: 1
                source_repo: https://example.com
                source_commit: 9999999999999999999999999999999999999999
            """.trimIndent(),
        )
        assertThatThrownBy { LockFileParser.parse(f) }
            .isInstanceOf(LockFileException::class.java)
            .hasMessageContaining("sha256")
    }

    @Test
    fun `reject uppercase sha256`() {
        val f = write(
            """
            tag: binaries-x
            generated_at: 2026-04-25T10:00:00Z
            artifacts:
              - name: libx.so
                engine: x
                abi: arm64-v8a
                destination: jniLibs
                download_url: https://example.com/x.so
                sha256: AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
                size_bytes: 1
                source_repo: https://example.com
                source_commit: 9999999999999999999999999999999999999999
            """.trimIndent(),
        )
        assertThatThrownBy { LockFileParser.parse(f) }
            .isInstanceOf(LockFileException::class.java)
            .hasMessageContaining("lowercase")
    }

    @Test
    fun `reject jniLibs artifact with numeric abi`() {
        val f = write(
            """
            tag: binaries-x
            generated_at: 2026-04-25T10:00:00Z
            artifacts:
              - name: libx.so
                engine: x
                abi: 64
                destination: jniLibs
                download_url: https://example.com/x.so
                sha256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
                size_bytes: 1
                source_repo: https://example.com
                source_commit: 9999999999999999999999999999999999999999
            """.trimIndent(),
        )
        assertThatThrownBy { LockFileParser.parse(f) }
            .isInstanceOf(LockFileException::class.java)
            .hasMessageContaining("abi")
    }

    @Test
    fun `reject empty file`() {
        val f = write("")
        assertThatThrownBy { LockFileParser.parse(f) }
            .isInstanceOf(LockFileException::class.java)
    }

    @Test
    fun `reject relative download url`() {
        val f = write(
            """
            tag: binaries-x
            generated_at: 2026-04-25T10:00:00Z
            artifacts:
              - name: libx.so
                engine: x
                abi: arm64-v8a
                destination: jniLibs
                download_url: /relative/path/x.so
                sha256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
                size_bytes: 1
                source_repo: https://example.com
                source_commit: 9999999999999999999999999999999999999999
            """.trimIndent(),
        )
        assertThatThrownBy { LockFileParser.parse(f) }
            .isInstanceOf(LockFileException::class.java)
            .hasMessageContaining("absolute")
    }

    @Test
    fun `reject malformed download url`() {
        val f = write(
            """
            tag: binaries-x
            generated_at: 2026-04-25T10:00:00Z
            artifacts:
              - name: libx.so
                engine: x
                abi: arm64-v8a
                destination: jniLibs
                download_url: http://[
                sha256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
                size_bytes: 1
                source_repo: https://example.com
                source_commit: 9999999999999999999999999999999999999999
            """.trimIndent(),
        )
        assertThatThrownBy { LockFileParser.parse(f) }
            .isInstanceOf(LockFileException::class.java)
            .hasMessageContaining("download_url")
    }

    @Test
    fun `reject non integer string size bytes`() {
        val f = write(
            """
            tag: binaries-x
            generated_at: 2026-04-25T10:00:00Z
            artifacts:
              - name: libx.so
                engine: x
                abi: arm64-v8a
                destination: jniLibs
                download_url: https://example.com/x.so
                sha256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
                size_bytes: not-a-number
                source_repo: https://example.com
                source_commit: 9999999999999999999999999999999999999999
            """.trimIndent(),
        )
        assertThatThrownBy { LockFileParser.parse(f) }
            .isInstanceOf(LockFileException::class.java)
            .hasMessageContaining("integer")
    }

    @Test
    fun `reject non-positive size bytes`() {
        val f = write(
            """
            tag: binaries-x
            generated_at: 2026-04-25T10:00:00Z
            artifacts:
              - name: libx.so
                engine: x
                abi: arm64-v8a
                destination: jniLibs
                download_url: https://example.com/x.so
                sha256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
                size_bytes: 0
                source_repo: https://example.com
                source_commit: 9999999999999999999999999999999999999999
            """.trimIndent(),
        )
        assertThatThrownBy { LockFileParser.parse(f) }
            .isInstanceOf(LockFileException::class.java)
            .hasMessageContaining("size_bytes")
    }

    @Test
    fun `reject null size bytes`() {
        val f = write(
            """
            tag: binaries-x
            generated_at: 2026-04-25T10:00:00Z
            artifacts:
              - name: libx.so
                engine: x
                abi: arm64-v8a
                destination: jniLibs
                download_url: https://example.com/x.so
                sha256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
                size_bytes: null
                source_repo: https://example.com
                source_commit: 9999999999999999999999999999999999999999
            """.trimIndent(),
        )
        assertThatThrownBy { LockFileParser.parse(f) }
            .isInstanceOf(LockFileException::class.java)
            .hasMessageContaining("size_bytes")
    }

    @Test
    fun `reject artifact missing source metadata and size`() {
        val missingSourceRepo = write(
            """
            tag: binaries-x
            generated_at: 2026-04-25T10:00:00Z
            artifacts:
              - name: libx.so
                engine: x
                abi: arm64-v8a
                destination: jniLibs
                download_url: https://example.com/x.so
                sha256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
                size_bytes: 1
                source_commit: 9999999999999999999999999999999999999999
            """.trimIndent(),
        )
        assertThatThrownBy { LockFileParser.parse(missingSourceRepo) }
            .isInstanceOf(LockFileException::class.java)
            .hasMessageContaining("source_repo")

        val missingSize = write(
            """
            tag: binaries-x
            generated_at: 2026-04-25T10:00:00Z
            artifacts:
              - name: libx.so
                engine: x
                abi: arm64-v8a
                destination: jniLibs
                download_url: https://example.com/x.so
                sha256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
                source_repo: https://example.com
                source_commit: 9999999999999999999999999999999999999999
            """.trimIndent(),
        )
        assertThatThrownBy { LockFileParser.parse(missingSize) }
            .isInstanceOf(LockFileException::class.java)
            .hasMessageContaining("size_bytes")

        val missingSourceCommit = write(
            """
            tag: binaries-x
            generated_at: 2026-04-25T10:00:00Z
            artifacts:
              - name: libx.so
                engine: x
                abi: arm64-v8a
                destination: jniLibs
                download_url: https://example.com/x.so
                sha256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
                size_bytes: 1
                source_repo: https://example.com
            """.trimIndent(),
        )
        assertThatThrownBy { LockFileParser.parse(missingSourceCommit) }
            .isInstanceOf(LockFileException::class.java)
            .hasMessageContaining("source_commit")
    }

    @Test
    fun `reject null artifact field with canonical missing message`() {
        val f = write(
            """
            tag: binaries-x
            generated_at: 2026-04-25T10:00:00Z
            artifacts:
              - name: null
                engine: x
                abi: arm64-v8a
                destination: jniLibs
                download_url: https://example.com/x.so
                sha256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
                size_bytes: 1
                source_repo: https://example.com
                source_commit: 9999999999999999999999999999999999999999
            """.trimIndent(),
        )
        assertThatThrownBy { LockFileParser.parse(f) }
            .isInstanceOf(LockFileException::class.java)
            .hasMessageContaining("name")
    }

    @Test
    fun `reject malformed yaml`() {
        val f = write("tag: [unclosed")
        assertThatThrownBy { LockFileParser.parse(f) }
            .isInstanceOf(LockFileException::class.java)
    }

    @Test
    fun `reject non map artifact entries`() {
        val f = write(
            """
            tag: binaries-x
            generated_at: 2026-04-25T10:00:00Z
            artifacts:
              - not-a-map
            """.trimIndent(),
        )
        assertThatThrownBy { LockFileParser.parse(f) }
            .isInstanceOf(LockFileException::class.java)
            .hasMessageContaining("Artifact #0")
    }

    @Test
    fun `parse scalar artifacts field as empty list`() {
        val f = write(
            """
            tag: binaries-x
            generated_at: 2026-04-25T10:00:00Z
            artifacts: not-a-list
            """.trimIndent(),
        )

        assertThat(LockFileParser.parse(f).artifacts).isEmpty()
    }
}
