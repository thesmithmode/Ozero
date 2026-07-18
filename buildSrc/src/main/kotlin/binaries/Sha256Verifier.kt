package binaries

import java.io.InputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

class IntegrityException(message: String) : RuntimeException(message)

object Sha256Verifier {
    private const val BUFFER_SIZE = 8192
    private val inProcessLocks = ConcurrentHashMap<Path, ReentrantLock>()

    fun streamingHash(input: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        DigestInputStream(input, md).use { dis ->
            val buf = ByteArray(BUFFER_SIZE)
            while (dis.read(buf) >= 0) {
                            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun verify(path: Path, expectedSha256: String) {
        val actual = Files.newInputStream(path).use { streamingHash(it) }
        if (actual != expectedSha256) {
            throw IntegrityException(
                "SHA256 mismatch for ${path.fileName}: expected=$expectedSha256, actual=$actual",
            )
        }
    }

    fun verifyAndMove(temp: Path, finalDst: Path, expectedSha256: String) {
        try {
            verify(temp, expectedSha256)
        } catch (e: IntegrityException) {
            Files.deleteIfExists(temp)
            throw e
        }
        Files.createDirectories(finalDst.parent)
        Files.move(temp, finalDst, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    fun <T> withFileLock(lockFile: Path, block: () -> T): T {
        val key = lockFile.toAbsolutePath().normalize()
        val inProcessLock = inProcessLocks.computeIfAbsent(key) { ReentrantLock() }
        inProcessLock.lock()
        try {
            val parent = key.parent
            Files.createDirectories(parent)
            FileChannel.open(key, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { ch ->
                ch.lock().use {
                    return block()
                }
            }
        } finally {
            inProcessLock.unlock()
        }
    }
}
