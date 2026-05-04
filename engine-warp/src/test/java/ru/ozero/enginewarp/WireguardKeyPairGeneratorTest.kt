package ru.ozero.enginewarp

import org.junit.jupiter.api.Test
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WireguardKeyPairGeneratorTest {

    @Test
    fun `StubWireguardKeyPairGenerator возвращает предсказуемые значения`() {
        val gen = StubWireguardKeyPairGenerator()
        val (priv, pub) = gen.generate()
        assertEquals("stub-priv-base64", priv)
        assertEquals("stub-pub-base64", pub)
    }

    @Test
    fun `StubWireguardKeyPairGenerator идемпотентен — каждый вызов возвращает те же значения`() {
        val gen = StubWireguardKeyPairGenerator()
        val first = gen.generate()
        val second = gen.generate()
        assertEquals(first, second)
    }

    @Test
    fun `RealWireguardKeyPairGenerator возвращает непустые строки`() {
        val gen = RealWireguardKeyPairGenerator()
        val (priv, pub) = gen.generate()
        assertTrue(priv.isNotBlank(), "privateKey should not be blank")
        assertTrue(pub.isNotBlank(), "publicKey should not be blank")
    }

    @Test
    fun `RealWireguardKeyPairGenerator возвращает валидный base64`() {
        val gen = RealWireguardKeyPairGenerator()
        val (priv, pub) = gen.generate()
        val decoder = Base64.getDecoder()
        val privBytes = runCatching { decoder.decode(priv) }.getOrNull()
        val pubBytes = runCatching { decoder.decode(pub) }.getOrNull()
        assertTrue(privBytes != null && privBytes.size == 32, "privateKey should be 32-byte base64")
        assertTrue(pubBytes != null && pubBytes.size == 32, "publicKey should be 32-byte base64")
    }

    @Test
    fun `RealWireguardKeyPairGenerator каждый раз генерирует разные ключи`() {
        val gen = RealWireguardKeyPairGenerator()
        val first = gen.generate()
        val second = gen.generate()
        assertNotEquals(first.first, second.first, "privateKey должен быть уникальным")
        assertNotEquals(first.second, second.second, "publicKey должен быть уникальным")
    }

    @Test
    fun `RealWireguardKeyPairGenerator priv и pub различаются`() {
        val gen = RealWireguardKeyPairGenerator()
        val (priv, pub) = gen.generate()
        assertNotEquals(priv, pub, "privateKey и publicKey не должны совпадать")
    }
}
