package ru.ozero.commoncrypto

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import kotlin.test.assertEquals

class Ed25519PemLoaderTest {

    private val samplePem = """
        -----BEGIN PUBLIC KEY-----
        MCowBQYDK2VwAyEAoaTc4Im/Ap0v8quyIoSZG1mhfTYa+PNOO4BNo0UMtQ0=
        -----END PUBLIC KEY-----
    """.trimIndent()

    @Test
    fun parsesValidPemTo32Bytes() {
        val raw = Ed25519PemLoader.parsePublicKey(samplePem)
        assertEquals(32, raw.size)
    }

    @Test
    fun parsesPemWithNoiseAroundMarkers() {
        val noisy = "garbage\n${samplePem}\nmore garbage"
        val raw = Ed25519PemLoader.parsePublicKey(noisy)
        assertEquals(32, raw.size)
    }

    @Test
    fun rejectsEmptyBody() {
        assertThrows<IllegalArgumentException> {
            Ed25519PemLoader.parsePublicKey("-----BEGIN PUBLIC KEY-----\n-----END PUBLIC KEY-----")
        }
    }

    @Test
    fun rejectsMalformedBase64() {
        val bad = "-----BEGIN PUBLIC KEY-----\n!!!notbase64!!!\n-----END PUBLIC KEY-----"
        assertThrows<IllegalArgumentException> {
            Ed25519PemLoader.parsePublicKey(bad)
        }
    }

    @Test
    fun rejectsWrongAlgorithmDerPrefix() {
                val rsaPem = """
            -----BEGIN PUBLIC KEY-----
            MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBALhqzqVmUzGS+RDJzTglNFRkw5szjGjm
            FkWN5Bc3GaPJ5zbBz+CaDNG5kKZNlvNpfQUxkWlpOZsH/H7g6n6e7GcCAwEAAQ==
            -----END PUBLIC KEY-----
        """.trimIndent()
        assertThrows<IllegalArgumentException> {
            Ed25519PemLoader.parsePublicKey(rsaPem)
        }
    }

        @Test
    fun assetUpdatePubkeyParses() {
        val asset = File("../app/src/main/assets/update-pubkey.pem")
        if (!asset.exists()) return 
        val raw = Ed25519PemLoader.parsePublicKey(asset.readText())
        assertEquals(32, raw.size)
    }
}
