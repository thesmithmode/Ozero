package ru.ozero.enginetor.dynamicmod

import android.util.Log
import java.io.File
import java.security.DigestInputStream
import java.security.MessageDigest

interface TorBinaryVerifier {
    suspend fun verify(abi: String, libDir: File): VerifyResult
}

sealed class VerifyResult {
    data object Ok : VerifyResult()
    data class Missing(val fileName: String) : VerifyResult()
    data class UnknownAbi(val abi: String) : VerifyResult()
    data class Corrupted(
        val fileName: String,
        val expected: String,
        val actual: String,
    ) : VerifyResult()
}

class Sha256TorBinaryVerifier(
    private val checksumsByAbi: Map<String, Map<String, String>>,
) : TorBinaryVerifier {

    override suspend fun verify(abi: String, libDir: File): VerifyResult {
        val expected = checksumsByAbi[abi]
        if (expected.isNullOrEmpty()) {
            Log.e(TAG, "no expected checksums for abi=$abi — REJECT install")
            return VerifyResult.UnknownAbi(abi)
        }
        for ((fileName, expectedSha) in expected) {
            val file = File(libDir, fileName)
            if (!file.isFile) {
                Log.e(TAG, "abi=$abi file=$fileName missing in $libDir")
                return VerifyResult.Missing(fileName)
            }
            val actual = sha256Hex(file)
            if (!actual.equals(expectedSha, ignoreCase = true)) {
                Log.e(
                    TAG,
                    "abi=$abi file=$fileName checksum mismatch expected=$expectedSha actual=$actual",
                )
                return VerifyResult.Corrupted(fileName, expectedSha, actual)
            }
            Log.d(TAG, "abi=$abi file=$fileName checksum OK")
        }
        return VerifyResult.Ok
    }

    private fun sha256Hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        DigestInputStream(file.inputStream(), md).use { dis ->
            val buf = ByteArray(BUFFER_SIZE)
            @Suppress("ControlFlowWithEmptyBody")
            while (dis.read(buf) >= 0) Unit
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val TAG = "TorBinaryVerifier"
        const val BUFFER_SIZE = 8192
    }
}
