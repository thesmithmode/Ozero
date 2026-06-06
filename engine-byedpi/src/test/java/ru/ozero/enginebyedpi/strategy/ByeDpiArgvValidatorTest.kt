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
        assertTrue(ByeDpiArgvValidator.isValid("-n \"google.com\" -Qr -f-204 -s1:5+sm -a1"))
        assertTrue(ByeDpiArgvValidator.isValid("-d9+s -q20+s -s 25+s -t5 -At,r,s -r1+h -a1"))
    }

    @Test
    fun `split option values reject missing detached value`() {
        assertFalse(ByeDpiArgvValidator.isValid("-s -t5 -a1"))
        assertFalse(ByeDpiArgvValidator.isValid("google.com -Qr -a1"))
    }

    @Test
    fun `unresolved placeholders and dangling value options are rejected`() {
        assertFalse(ByeDpiArgvValidator.isValid("-n {sni} -Qr -a1"))
        assertFalse(ByeDpiArgvValidator.isValid("-n \"-google.com\" -Qr -a1"))
        assertFalse(ByeDpiArgvValidator.isValid("-n"))
        assertFalse(ByeDpiArgvValidator.isValid("--ttl"))
    }

    @Test
    fun `long option syntax branches are validated`() {
        assertTrue(ByeDpiArgvValidator.isValid("--ttl=8 --fake=-1 --split=1+s --disorder=3+s -K"))
        assertTrue(ByeDpiArgvValidator.isValid("--ttl 8 --fake -1 --split 1+s --disorder 3+s -K"))
        assertFalse(ByeDpiArgvValidator.isValid("--TTL 8 -K"))
        assertFalse(ByeDpiArgvValidator.isValid("-- 8 -K"))
        assertFalse(ByeDpiArgvValidator.isValid("--ttl= -K"))
        assertFalse(ByeDpiArgvValidator.isValid("--ttl=-1 -K"))
        assertFalse(ByeDpiArgvValidator.isValid("--ttl -1 -K"))
        assertFalse(ByeDpiArgvValidator.isValid("--fake abc -K"))
        assertFalse(ByeDpiArgvValidator.isValid("--split -bad -K"))
        assertFalse(ByeDpiArgvValidator.isValid("--disorder bad/value -K"))
    }

    @Test
    fun `short flag token branches are validated`() {
        assertTrue(ByeDpiArgvValidator.isValid("-K -S -Y -Qr -At,r,s,c -Kt,u,h -Mh,d,r -l:127.0.0.1"))
        assertTrue(ByeDpiArgvValidator.isValid("-a 1+s -d 2+s -e test -f 204 -m 1 -o 2 -p 3 -q 4 -r 5 -s 6 -t 7 -O 8 -R 9"))
        assertTrue(ByeDpiArgvValidator.isValid("-a1+s -d2+s -eabc -f-204 -m1 -o2 -p3 -q4 -r5 -s6 -t7 -O8 -R9"))
        assertFalse(ByeDpiArgvValidator.isValid("-"))
        assertFalse(ByeDpiArgvValidator.isValid("-Z"))
        assertFalse(ByeDpiArgvValidator.isValid("-Qx"))
        assertFalse(ByeDpiArgvValidator.isValid("-Axyz"))
        assertFalse(ByeDpiArgvValidator.isValid("-Kxyz"))
        assertFalse(ByeDpiArgvValidator.isValid("-Mxyz"))
        assertFalse(ByeDpiArgvValidator.isValid("-a bad/value"))
        assertFalse(ByeDpiArgvValidator.isValid("-a -1"))
    }

    @Test
    fun `domain values and empty commands are rejected precisely`() {
        assertFalse(ByeDpiArgvValidator.isValid(""))
        assertFalse(ByeDpiArgvValidator.isValid("   "))
        assertFalse(ByeDpiArgvValidator.isValid("-n bad/domain -K"))
        assertFalse(ByeDpiArgvValidator.isValid("-n \"\" -K"))
        assertTrue(ByeDpiArgvValidator.isValid("-n sub_domain.example-test.com -K"))
    }
}
