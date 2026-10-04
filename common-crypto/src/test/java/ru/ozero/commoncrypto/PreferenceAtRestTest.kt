package ru.ozero.commoncrypto

import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PreferenceAtRestTest {
    @BeforeEach
    fun installKey() {
        AtRestSecrets.installCipher(AesGcmAtRestCipher(SecretKeySpec(TEST_KEY, "AES")))
    }

    @Test
    fun `seal hides plaintext and open restores it`() {
        val sealed = PreferenceAtRest.seal("session-secret")

        assertTrue(sealed.startsWith(AtRestSecrets.PREFIX))
        assertFalse(sealed.contains("session-secret"))
        assertEquals("session-secret", PreferenceAtRest.open(sealed))
    }

    @Test
    fun `sealing ciphertext again does not double encrypt`() {
        val once = PreferenceAtRest.seal("session-secret")

        assertEquals(once, PreferenceAtRest.seal(once))
        assertEquals("session-secret", PreferenceAtRest.open(once))
    }

    @Test
    fun `plaintext without prefix is returned unchanged`() {
        assertEquals("plain-token", PreferenceAtRest.open("plain-token"))
        assertFalse(PreferenceAtRest.isSealed("plain-token"))
    }

    @Test
    fun `tampered ciphertext is rejected`() {
        val sealed = PreferenceAtRest.seal("session-secret")
        val tampered = sealed.dropLast(1) + if (sealed.last() == 'A') "B" else "A"

        assertThrows<Exception> { PreferenceAtRest.open(tampered) }
    }

    private companion object {
        val TEST_KEY = ByteArray(32) { index -> (index + 7).toByte() }
    }
}
