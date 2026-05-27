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
                download_url: https://github.com/example-owner/example-repo/releases/download/binaries-abc12345/libbyedpi-arm64-v8a.so
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
    fun `reject missing tag field`() {
        val f = write("generated_at: 2026-04-25T10:00:00Z\nartifacts: []\n")
        assertThatThrownBy { LockFileParser.parse(f) }
            .isInstanceOf(LockFileException::class.java)
            .hasMessageContaining("tag")
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
    fun `reject malformed yaml`() {
        val f = write("tag: [unclosed")
        assertThatThrownBy { LockFileParser.parse(f) }
            .isInstanceOf(LockFileException::class.java)
    }
}
