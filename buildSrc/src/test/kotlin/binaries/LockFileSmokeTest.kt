package binaries

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LockFileSmokeTest {
    @Test
    fun `LockFile data class can be constructed with required fields`() {
        val artifact = Artifact(
            name = "libbyedpi-arm64-v8a.so",
            engine = "byedpi",
            abi = "arm64-v8a",
            destination = Destination.JNI_LIBS,
            downloadUrl = "https://example.com/x.so",
            sha256 = "0".repeat(64),
            sizeBytes = 100L,
            sourceRepo = "https://github.com/hufrea/byedpi",
            sourceCommit = "1".repeat(40),
        )
        val lock = LockFile(
            tag = "binaries-abc12345",
            generatedAt = "2026-04-25T10:00:00Z",
            artifacts = listOf(artifact),
        )
        assertThat(lock.tag).isEqualTo("binaries-abc12345")
        assertThat(lock.artifacts).hasSize(1)
        assertThat(lock.findByName("libbyedpi-arm64-v8a.so")).isEqualTo(artifact)
        assertThat(lock.findByName("missing")).isNull()
    }

    @Test
    fun `Destination enum has LIBS and JNI_LIBS`() {
        assertThat(Destination.values()).containsExactlyInAnyOrder(Destination.LIBS, Destination.JNI_LIBS)
    }

    @Test
    fun `LockFileException is a RuntimeException`() {
        val e = LockFileException("msg")
        assertThat(e).isInstanceOf(RuntimeException::class.java)
        assertThat(e.message).isEqualTo("msg")
    }
}
