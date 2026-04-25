package binaries

import java.io.InputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.DigestInputStream
import java.security.MessageDigest

class IntegrityException(message: String) : RuntimeException(message)

object Sha256Verifier {
    private const val BUFFER_SIZE = 8192

    fun streamingHash(input: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        DigestInputStream(input, md).use { dis ->
            val buf = ByteArray(BUFFER_SIZE)
            while (dis.read(buf) >= 0) {
                // drain stream — digest accumulates inside DigestInputStream
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun verifyAndMove(temp: Path, finalDst: Path, expectedSha256: String) {
        val actual = Files.newInputStream(temp).use { streamingHash(it) }
        if (actual != expectedSha256) {
            Files.deleteIfExists(temp)
            throw IntegrityException(
                "SHA256 mismatch for ${finalDst.fileName}: expected=$expectedSha256, actual=$actual",
            )
        }
        Files.createDirectories(finalDst.parent)
        Files.move(temp, finalDst, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    fun <T> withFileLock(lockFile: Path, block: () -> T): T {
        val parent = lockFile.parent ?: lockFile.toAbsolutePath().parent
        Files.createDirectories(parent)
        FileChannel.open(
            lockFile,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
        ).use { ch ->
            ch.lock().use {
                return block()
            }
        }
    }
}
