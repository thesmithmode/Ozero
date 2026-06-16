package ru.ozero.enginebyedpi.strategy

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ByeDpiArgvValidatorTest {

    @Test
    fun `known seed syntax is accepted`() {
        assertTrue(ByeDpiArgvValidator.isValid("-K -An -s2 -d1 -a1"))
        assertTrue(
            ByeDpiArgvValidator.isValid("--fake -1 --ttl 8 --split 1+s --disorder 3+s -a1"),
        )
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
        assertTrue(
            ByeDpiArgvValidator.isValid("--ttl=8 --fake=-1 --split=1+s --disorder=3+s -K"),
        )
        assertTrue(
            ByeDpiArgvValidator.isValid("--ttl 8 --fake -1 --split 1+s --disorder 3+s -K"),
        )
        assertFalse(ByeDpiArgvValidator.isValid("--TTL 8 -K"))
        assertFalse(ByeDpiArgvValidator.isValid("-- 8 -K"))
        assertFalse(ByeDpiArgvValidator.isValid("--ttl= -K"))
        assertFalse(ByeDpiArgvValidator.isValid("--ttl=-1 -K"))
        assertFalse(ByeDpiArgvValidator.isValid("--ttl -1 -K"))
        assertFalse(ByeDpiArgvValidator.isValid("--fake abc -K"))
        assertFalse(ByeDpiArgvValidator.isValid("--split -bad -K"))
        assertFalse(ByeDpiArgvValidator.isValid("--split=-bad -K"))
        assertFalse(ByeDpiArgvValidator.isValid("--disorder bad/value -K"))
    }

    @Test
    fun `short flag token branches are validated`() {
        assertTrue(
            ByeDpiArgvValidator.isValid("-K -S -Y -Qr -At,r,s,c -Kt,u,h -Mh,d,r -l:127.0.0.1"),
        )
        assertTrue(
            ByeDpiArgvValidator.isValid(
                "-a 1+s -d 2+s -e test -f 204 -m 1 -o 2 -p 3 -q 4 -r 5 -s 6 -t 7 -O 8 -R 9",
            ),
        )
        assertTrue(
            ByeDpiArgvValidator.isValid(
                "-a1+s -d2+s -eabc -f-204 -m1 -o2 -p3 -q4 -r5 -s6 -t7 -O8 -R9",
            ),
        )
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

    @Test
    fun `long option names reject uppercase underscore and bare double dash`() {
        assertFalse(ByeDpiArgvValidator.isValid("--ttl_ms 8 -K"))
        assertFalse(ByeDpiArgvValidator.isValid("--Fake -1 -K"))
        assertFalse(ByeDpiArgvValidator.isValid("-- -K"))
    }

    @Test
    fun `detached long option values must satisfy option specific validation`() {
        assertFalse(ByeDpiArgvValidator.isValid("--ttl abc -K"))
        assertFalse(ByeDpiArgvValidator.isValid("--fake +abc -K"))
        assertFalse(ByeDpiArgvValidator.isValid("--disorder -1+s -K"))
        assertFalse(ByeDpiArgvValidator.isValid("--split bad/value -K"))
        assertTrue(ByeDpiArgvValidator.isValid("--ttl 0 --fake +1 --split 1+s --disorder 2+d -K"))
    }

    @Test
    fun `short option values reject blank dangling and invalid modifier characters`() {
        assertFalse(ByeDpiArgvValidator.isValid("-a"))
        assertFalse(ByeDpiArgvValidator.isValid("-d @bad"))
        assertFalse(ByeDpiArgvValidator.isValid("-e -next"))
        assertFalse(ByeDpiArgvValidator.isValid("-f bad/value"))
        assertFalse(ByeDpiArgvValidator.isValid("-m bad_value"))
        assertFalse(ByeDpiArgvValidator.isValid("-o --next"))
        assertTrue(ByeDpiArgvValidator.isValid("-a 1+s -d 2+d -e abc.def -f 204 -K"))
    }

    @Test
    fun `compound short flags accept only documented characters`() {
        assertTrue(ByeDpiArgvValidator.isValid("-Antrs,c -Ktuh, -Mhdr, -K"))
        assertFalse(ByeDpiArgvValidator.isValid("-Aq -K"))
        assertFalse(ByeDpiArgvValidator.isValid("-Kx -K"))
        assertFalse(ByeDpiArgvValidator.isValid("-Mz -K"))
        assertFalse(ByeDpiArgvValidator.isValid("-l 127.0.0.1 -K"))
    }

    @Test
    fun `placeholder braces reject both opening and closing markers`() {
        assertFalse(ByeDpiArgvValidator.isValid("-n sni} -K"))
        assertFalse(ByeDpiArgvValidator.isValid("-n {sni -K"))
    }

    @Test
    fun `long option attached values reject invalid numeric and modifier forms`() {
        assertFalse(ByeDpiArgvValidator.isValid("--fake=abc -K"))
        assertFalse(ByeDpiArgvValidator.isValid("--ttl=abc -K"))
        assertFalse(ByeDpiArgvValidator.isValid("--disorder=bad/value -K"))
        assertFalse(ByeDpiArgvValidator.isValid("--split=-1+s -K"))
    }

    @Test
    fun `unknown long option with detached value is treated as standalone flag`() {
        assertFalse(ByeDpiArgvValidator.isValid("--unknown value -K"))
        assertTrue(ByeDpiArgvValidator.isValid("--unknown -K"))
    }

    @Test
    fun `short token branches reject whitespace and invalid attached values`() {
        assertFalse(ByeDpiArgvValidator.isValid("'-a 1' -K"))
        assertFalse(ByeDpiArgvValidator.isValid("-p@bad -K"))
        assertFalse(ByeDpiArgvValidator.isValid("-R/bad -K"))
        assertTrue(ByeDpiArgvValidator.isValid("-l:127.0.0.1:1080 -K"))
    }

    @Test
    fun `domain and integer validators cover signed unsigned and quoted edges`() {
        assertTrue(ByeDpiArgvValidator.isValid("-n \"sub.example\" --fake -42 --ttl 42 -K"))
        assertTrue(ByeDpiArgvValidator.isValid("-n host_name.example --ttl +42 --fake 0 -K"))
        assertFalse(ByeDpiArgvValidator.isValid("-n .bad/slash -K"))
        assertFalse(ByeDpiArgvValidator.isValid("-n -bad.example -K"))
        assertFalse(ByeDpiArgvValidator.isValid("--fake 1.5 -K"))
        assertFalse(ByeDpiArgvValidator.isValid("--ttl +abc -K"))
    }

    @Test
    fun `attached value characters cover every allowed separator`() {
        assertTrue(ByeDpiArgvValidator.isValid("-aA1-b+c:d,e.f -K"))
        assertFalse(ByeDpiArgvValidator.isValid("-aA1_b -K"))
    }

    @Test
    fun `validator covers remaining flag and value edge branches`() {
        assertTrue(ByeDpiArgvValidator.isValid("-S -Y -K"))
        assertTrue(ByeDpiArgvValidator.isValid("--custom=value -K"))
        assertFalse(ByeDpiArgvValidator.isValid("--custom= -K"))
        assertFalse(ByeDpiArgvValidator.isValid("-A -K"))
        assertFalse(ByeDpiArgvValidator.isValid("-K -M -K"))
        assertFalse(ByeDpiArgvValidator.isValid("-l -K"))
        assertFalse(ByeDpiArgvValidator.isValid("-a@ -K"))
        assertFalse(ByeDpiArgvValidator.isValid("-n \"   \" -K"))
        assertFalse(ByeDpiArgvValidator.isValid("-n bad?domain -K"))
    }

    @Test
    fun `private validator primitives cover option parser branch matrix`() {
        val flag = ByeDpiArgvValidator::class.java.getDeclaredMethod("isFlagToken", String::class.java).apply {
            isAccessible = true
        }
        val longToken = ByeDpiArgvValidator::class.java
            .getDeclaredMethod("isLongOptionToken", String::class.java)
            .apply { isAccessible = true }
        val valueValid = ByeDpiArgvValidator::class.java.getDeclaredMethod(
            "isValueValid",
            String::class.java,
            String::class.java,
        ).apply { isAccessible = true }
        val detachedLong = ByeDpiArgvValidator::class.java.getDeclaredMethod(
            "expectsDetachedLongValue",
            String::class.java,
        ).apply { isAccessible = true }
        val valueChar = ByeDpiArgvValidator::class.java.getDeclaredMethod(
            "isAttachedValueChar",
            Char::class.javaPrimitiveType,
        ).apply { isAccessible = true }

        listOf("-K", "-S", "-Y", "-An", "-Kt", "-Mh", "-a", "-a1", "-l:127.0.0.1").forEach {
            assertTrue(flag.invoke(ByeDpiArgvValidator, it) as Boolean, it)
        }
        listOf("-", "-A", "-Kx", "-M", "-l", "-a_", "-Q", "-Qr1", "-Mhz").forEach {
            assertFalse(flag.invoke(ByeDpiArgvValidator, it) as Boolean, it)
        }
        assertFalse(flag.invoke(ByeDpiArgvValidator, "-a 1") as Boolean)
        listOf("--a", "--abc", "--abc-1", "--abc=1").forEach {
            assertTrue(longToken.invoke(ByeDpiArgvValidator, it) as Boolean, it)
        }
        listOf("--", "--A", "--abc_", "--abc=").forEach {
            assertFalse(longToken.invoke(ByeDpiArgvValidator, it) as Boolean, it)
        }
        assertTrue(valueValid.invoke(ByeDpiArgvValidator, "--unknown", "value") as Boolean)
        assertFalse(valueValid.invoke(ByeDpiArgvValidator, "--unknown", "") as Boolean)
        assertTrue(detachedLong.invoke(ByeDpiArgvValidator, "--ttl") as Boolean)
        assertFalse(detachedLong.invoke(ByeDpiArgvValidator, "--ttl=8") as Boolean)
        listOf('a', '1', '-', '+', ':', ',', '.').forEach {
            assertTrue(valueChar.invoke(ByeDpiArgvValidator, it) as Boolean, it.toString())
        }
        assertFalse(valueChar.invoke(ByeDpiArgvValidator, '_') as Boolean)
    }
}
