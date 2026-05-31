package ru.ozero.enginebyedpi.strategy

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ByeDpiArgvValidatorTest {

    @Test
    fun `known seed syntax is accepted`() {
        assertTrue(ByeDpiArgvValidator.isValid("-K -An -s2 -d1 -a1"))
        assertTrue(ByeDpiArgvValidator.isValid("--fake -1 --ttl 8 --split 1+s --disorder 3+s -a1"))
        assertTrue(ByeDpiArgvValidator.isValid("-n google.com -Qr -f-204 -s1:5+sm -a1"))
    }

    @Test
    fun `split option values are rejected unless option grammar allows a value token`() {
        assertFalse(ByeDpiArgvValidator.isValid("-s 25+s -t5 -a1"))
        assertFalse(ByeDpiArgvValidator.isValid("google.com -Qr -a1"))
    }

    @Test
    fun `unresolved placeholders and dangling value options are rejected`() {
        assertFalse(ByeDpiArgvValidator.isValid("-n {sni} -Qr -a1"))
        assertFalse(ByeDpiArgvValidator.isValid("-n"))
        assertFalse(ByeDpiArgvValidator.isValid("--ttl"))
    }
}
