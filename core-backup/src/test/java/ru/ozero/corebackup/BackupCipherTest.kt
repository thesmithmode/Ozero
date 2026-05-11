package ru.ozero.corebackup

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupCipherTest {

    @Test
    fun `encrypt и decrypt возвращают исходные байты`() {
        val plain = "Hello backup!".toByteArray(Charsets.UTF_8)
        val enc = BackupCipher.encrypt(plain)
        assertContentEquals(plain, BackupCipher.decrypt(enc))
    }

    @Test
    fun `encrypt пустого массива roundtrip`() {
        val plain = ByteArray(0)
        assertContentEquals(plain, BackupCipher.decrypt(BackupCipher.encrypt(plain)))
    }

    @Test
    fun `encrypt большого JSON-файла roundtrip`() {
        val plain = buildString {
            repeat(1000) { append("""{"key":"value","num":$it},""") }
        }.toByteArray(Charsets.UTF_8)
        assertContentEquals(plain, BackupCipher.decrypt(BackupCipher.encrypt(plain)))
    }

    @Test
    fun `каждый encrypt генерирует уникальный ciphertext случайный nonce`() {
        val plain = "data".toByteArray()
        val a = BackupCipher.encrypt(plain)
        val b = BackupCipher.encrypt(plain)
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun `decrypt подделанного последнего байта бросает исключение`() {
        val enc = BackupCipher.encrypt("secret".toByteArray()).copyOf()
        enc[enc.size - 1] = (enc[enc.size - 1].toInt() xor 0xFF).toByte()
        assertThrows<Exception> { BackupCipher.decrypt(enc) }
    }

    @Test
    fun `decrypt подделанного nonce бросает исключение`() {
        val enc = BackupCipher.encrypt("secret".toByteArray()).copyOf()
        enc[5] = (enc[5].toInt() xor 0xFF).toByte()
        assertThrows<Exception> { BackupCipher.decrypt(enc) }
    }

    @Test
    fun `isEncrypted true для зашифрованного файла`() {
        val enc = BackupCipher.encrypt("x".toByteArray())
        assertTrue(BackupCipher.isEncrypted(enc))
    }

    @Test
    fun `isEncrypted false для plaintext JSON`() {
        val json = """{"version":1}""".toByteArray()
        assertFalse(BackupCipher.isEncrypted(json))
    }

    @Test
    fun `isEncrypted false для пустого массива`() {
        assertFalse(BackupCipher.isEncrypted(ByteArray(0)))
    }

    @Test
    fun `decrypt слишком короткого массива бросает IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> {
            BackupCipher.decrypt(byteArrayOf(0x4F, 0x5A, 0x52, 0x42))
        }
    }

    @Test
    fun `decrypt неверной магии бросает IllegalArgumentException`() {
        val bad = ByteArray(40) { 0x00 }
        assertThrows<IllegalArgumentException> { BackupCipher.decrypt(bad) }
    }
}
