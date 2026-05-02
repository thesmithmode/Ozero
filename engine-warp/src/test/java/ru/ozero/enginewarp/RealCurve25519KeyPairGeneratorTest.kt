package ru.ozero.enginewarp

import org.junit.jupiter.api.Test
import java.security.SecureRandom
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RealCurve25519KeyPairGeneratorTest {

    @Test
    fun `generate возвращает 32-байтные base64 ключи`() {
        val gen = RealCurve25519KeyPairGenerator()

        val (priv, pub) = gen.generate()

        val privBytes = Base64.getDecoder().decode(priv)
        val pubBytes = Base64.getDecoder().decode(pub)
        assertEquals(32, privBytes.size, "X25519 private key — 32 байта")
        assertEquals(32, pubBytes.size, "X25519 public key — 32 байта")
    }

    @Test
    fun `generate produces different keys on subsequent calls`() {
        val gen = RealCurve25519KeyPairGenerator()

        val (priv1, pub1) = gen.generate()
        val (priv2, pub2) = gen.generate()

        assertNotEquals(priv1, priv2, "private keys должны быть разными")
        assertNotEquals(pub1, pub2, "public keys должны быть разными")
    }

    @Test
    fun `keys не содержат литерал stub`() {
        val gen = RealCurve25519KeyPairGenerator()

        val (priv, pub) = gen.generate()

        assertTrue(!priv.contains("stub"), "Private key не должен быть stub-литералом")
        assertTrue(
            !pub.contains("stub"),
            "Public key не должен быть stub-литералом — Cloudflare WARP register отвергает их с 400 " +
                "invalid public key. Sentinel против регрессии StubWireguardKeyPairGenerator.",
        )
    }

    @Test
    fun `deterministic с фиксированным seed — same input → same output`() {
        val seed = ByteArray(32) { it.toByte() }
        val gen1 = RealCurve25519KeyPairGenerator(SecureRandom.getInstance("SHA1PRNG").apply { setSeed(seed) })
        val gen2 = RealCurve25519KeyPairGenerator(SecureRandom.getInstance("SHA1PRNG").apply { setSeed(seed) })

        val k1 = gen1.generate()
        val k2 = gen2.generate()

        assertEquals(k1, k2, "одинаковый seed → одинаковая пара ключей")
    }
}
