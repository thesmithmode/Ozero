package binaries

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class Sha256VerifierTest {
    @TempDir
    lateinit var tmp: Path

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `streamingHash computes correct SHA256 over stream`() {
        val data = "hello".toByteArray()
        val computed = Sha256Verifier.streamingHash(data.inputStream())
        assertThat(computed).isEqualTo(sha256(data))
    }

    @Test
    fun `streamingHash handles empty stream`() {
        val data = ByteArray(0)
        val computed = Sha256Verifier.streamingHash(data.inputStream())
        assertThat(computed).isEqualTo(sha256(data))
    }

    @Test
    fun `verifyAndMove succeeds when SHA matches and moves file atomically`() {
        val src = tmp.resolve("src.bin")
        val dst = tmp.resolve("dst.bin")
        val data = "byedpi-binary-bytes".toByteArray()
        Files.write(src, data)
        Sha256Verifier.verifyAndMove(src, dst, sha256(data))
        assertThat(Files.exists(dst)).isTrue()
        assertThat(Files.exists(src)).isFalse()
        assertThat(Files.readAllBytes(dst)).isEqualTo(data)
    }

    @Test
    fun `verifyAndMove deletes temp and throws on SHA mismatch`() {
        val src = tmp.resolve("src.bin")
        val dst = tmp.resolve("dst.bin")
        Files.write(src, "wrong-content".toByteArray())
        assertThatThrownBy {
            Sha256Verifier.verifyAndMove(src, dst, "0".repeat(64))
        }
            .isInstanceOf(IntegrityException::class.java)
            .hasMessageContaining("expected")
            .hasMessageContaining("actual")
        assertThat(Files.exists(src)).isFalse()
        assertThat(Files.exists(dst)).isFalse()
    }

    @Test
    fun `withFileLock serializes concurrent operations on same key`() {
        val lockKey = tmp.resolve("test.lock")
        val order = mutableListOf<String>()
        val started1 = CountDownLatch(1)
        val canFinish1 = CountDownLatch(1)

        val t1 = thread {
            Sha256Verifier.withFileLock(lockKey) {
                synchronized(order) { order.add("t1-enter") }
                started1.countDown()
                canFinish1.await(2, TimeUnit.SECONDS)
                synchronized(order) { order.add("t1-exit") }
            }
        }
        started1.await(2, TimeUnit.SECONDS)

        val t2 = thread {
            Sha256Verifier.withFileLock(lockKey) {
                synchronized(order) { order.add("t2-enter") }
                synchronized(order) { order.add("t2-exit") }
            }
        }
        Thread.sleep(100)
        synchronized(order) {
            assertThat(order).containsExactly("t1-enter")
        }
        canFinish1.countDown()
        t1.join(2000)
        t2.join(2000)
        assertThat(order).containsExactly("t1-enter", "t1-exit", "t2-enter", "t2-exit")
    }
}
