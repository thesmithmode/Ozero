package ru.ozero.commoncrypto

import java.util.Base64

/**
 * Парсит Ed25519 PEM (X.509 SubjectPublicKeyInfo, как выдаёт `openssl pkey -pubout`)
 * и извлекает 32-байтовый raw публичный ключ.
 *
 * Формат: 12-байтовый DER-префикс `302a300506032b6570032100` + 32 байта raw ключа.
 */
object Ed25519PemLoader {
    private const val PEM_HEADER = "-----BEGIN PUBLIC KEY-----"
    private const val PEM_FOOTER = "-----END PUBLIC KEY-----"
    private const val DER_PREFIX_SIZE = 12
    private const val RAW_KEY_SIZE = 32
    private const val EXPECTED_DER_SIZE = DER_PREFIX_SIZE + RAW_KEY_SIZE

    private val EXPECTED_DER_PREFIX = byteArrayOf(
        0x30, 0x2a, 0x30, 0x05, 0x06, 0x03,
        0x2b, 0x65, 0x70, 0x03, 0x21, 0x00,
    )

    fun parsePublicKey(pem: String): ByteArray {
        val body = pem
            .substringAfter(PEM_HEADER, "")
            .substringBefore(PEM_FOOTER, "")
            .replace("\\s".toRegex(), "")
        require(body.isNotEmpty()) { "не найден PEM body между BEGIN/END markers" }
        val der = Base64.getDecoder().decode(body)
        require(der.size == EXPECTED_DER_SIZE) {
            "ожидаем $EXPECTED_DER_SIZE байт DER, got ${der.size}"
        }
        for (i in 0 until DER_PREFIX_SIZE) {
            require(der[i] == EXPECTED_DER_PREFIX[i]) {
                "DER-префикс не Ed25519 X.509 SubjectPublicKeyInfo (byte $i)"
            }
        }
        return der.copyOfRange(DER_PREFIX_SIZE, EXPECTED_DER_SIZE)
    }
}
