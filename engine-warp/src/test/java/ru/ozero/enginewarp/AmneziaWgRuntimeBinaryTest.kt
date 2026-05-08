package ru.ozero.enginewarp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import java.security.DigestInputStream
import java.security.MessageDigest

class AmneziaWgRuntimeBinaryTest {

    @TestFactory
    fun checkedInSoFilesMatchPortalWgReference(): List<DynamicTest> = listOf(
        Triple("libam.so", "91a601d509a06e2b9abd92af124ba988c93ccf8e1de4e854af9f3188b5f8bb6f", 95448L),
        Triple("libam-go.so", "2ebc0ee96c0a0f0edbdf4687f2981888184b63d417417343b2774f5355daa603", 8578640L),
        Triple("libam-quick.so", "b7ac338f658387ab70de732f7fe63374409f48b9a1589a33f729344a492804e1", 30792L),
    ).map { (name, expectedSha, expectedSize) ->
        DynamicTest.dynamicTest("$name SHA256+size matches PORTAL_WG_v1.4.3 reference") {
            val candidates = listOf(
                File("src/main/jniLibs/arm64-v8a/$name"),
                File("engine-warp/src/main/jniLibs/arm64-v8a/$name"),
            )
            val file = candidates.firstOrNull { it.exists() }
                ?: error("checked-in SO not found, looked at: ${candidates.map { it.absolutePath }}")
            assertTrue(file.length() > 0, "$name is empty")
            assertEquals(expectedSize, file.length(), "$name size mismatch — wrong runtime")
            val actualSha = file.inputStream().use { sha256(it) }
            assertEquals(
                expectedSha,
                actualSha,
                "$name SHA256 mismatch — checked-in SO != PORTAL_WG_v1.4.3 reference; " +
                    "do NOT replace with maven com.zaneschepke build (it crashes on raw INI with I1 blob)",
            )
        }
    }

    private fun sha256(stream: java.io.InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        DigestInputStream(stream, md).use { dis ->
            val buf = ByteArray(8192)
            while (dis.read(buf) >= 0) {
                // drain
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
