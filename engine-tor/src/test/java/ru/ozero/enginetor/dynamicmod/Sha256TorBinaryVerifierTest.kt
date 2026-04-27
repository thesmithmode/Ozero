package ru.ozero.enginetor.dynamicmod

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertIs

class Sha256TorBinaryVerifierTest {

    @TempDir
    lateinit var tmp: Path

    private fun writeBytes(name: String, bytes: ByteArray): File {
        val f = tmp.resolve(name).toFile()
        f.writeBytes(bytes)
        return f
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `Ok when all files match expected checksums`() = runTest {
        val torBytes = "tor-arm64-content".toByteArray()
        val iptBytes = "iptproxy-arm64-content".toByteArray()
        writeBytes("libtor-arm64-v8a.so", torBytes)
        writeBytes("libiptproxy-arm64-v8a.so", iptBytes)
        val verifier = Sha256TorBinaryVerifier(
            mapOf(
                "arm64-v8a" to mapOf(
                    "libtor-arm64-v8a.so" to sha256Hex(torBytes),
                    "libiptproxy-arm64-v8a.so" to sha256Hex(iptBytes),
                ),
            ),
        )

        val result = verifier.verify("arm64-v8a", tmp.toFile())

        assertEquals(VerifyResult.Ok, result)
    }

    @Test
    fun `Corrupted when one file does not match`() = runTest {
        val good = "good".toByteArray()
        val bad = "bad-actual".toByteArray()
        writeBytes("libtor-arm64-v8a.so", good)
        writeBytes("libiptproxy-arm64-v8a.so", bad)
        val expectedTor = sha256Hex(good)
        val expectedIpt = sha256Hex("expected-other".toByteArray())
        val verifier = Sha256TorBinaryVerifier(
            mapOf(
                "arm64-v8a" to mapOf(
                    "libtor-arm64-v8a.so" to expectedTor,
                    "libiptproxy-arm64-v8a.so" to expectedIpt,
                ),
            ),
        )

        val result = verifier.verify("arm64-v8a", tmp.toFile())

        val corr = assertIs<VerifyResult.Corrupted>(result)
        assertEquals("libiptproxy-arm64-v8a.so", corr.fileName)
        assertEquals(expectedIpt, corr.expected)
        assertEquals(sha256Hex(bad), corr.actual)
    }

    @Test
    fun `Missing when file absent on disk`() = runTest {
        val verifier = Sha256TorBinaryVerifier(
            mapOf(
                "arm64-v8a" to mapOf(
                    "libtor-arm64-v8a.so" to "0".repeat(64),
                ),
            ),
        )

        val result = verifier.verify("arm64-v8a", tmp.toFile())

        val miss = assertIs<VerifyResult.Missing>(result)
        assertEquals("libtor-arm64-v8a.so", miss.fileName)
    }

    @Test
    fun `UnknownAbi when abi has no expected entries`() = runTest {
        val verifier = Sha256TorBinaryVerifier(
            mapOf("arm64-v8a" to mapOf("libtor-arm64-v8a.so" to "0".repeat(64))),
        )

        val result = verifier.verify("mips", tmp.toFile())

        assertEquals(VerifyResult.UnknownAbi("mips"), result)
    }

    @Test
    fun `ABI filter ignores files for other ABIs`() = runTest {
        val torBytes = "tor-arm64".toByteArray()
        writeBytes("libtor-arm64-v8a.so", torBytes)
                writeBytes("libtor-x86.so", "garbage".toByteArray())
        val verifier = Sha256TorBinaryVerifier(
            mapOf(
                "arm64-v8a" to mapOf("libtor-arm64-v8a.so" to sha256Hex(torBytes)),
                "x86" to mapOf("libtor-x86.so" to "0".repeat(64)),
            ),
        )

        val result = verifier.verify("arm64-v8a", tmp.toFile())

        assertEquals(VerifyResult.Ok, result)
    }
}
