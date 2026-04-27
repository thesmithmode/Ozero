package ru.ozero.app.ui.onboarding

import org.junit.jupiter.api.Test
import ru.ozero.commoncrypto.Ed25519PemLoader
import ru.ozero.commoncrypto.Ed25519Verifier
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Гарантирует что snapshot bootstrap-servers.json подписан валидно текущим
 * `update-pubkey.pem`. Если этот тест красный — обновлять подпись через
 * `tools/sign_bootstrap.sh` (или аналог). Fail-closed на проде: в `AssetsFirstRunBootstrap`
 * импорт пропускается, но в репо такого допускать нельзя.
 */
class BootstrapSignatureTest {

    private val json = File("src/main/assets/bootstrap-servers.json")
    private val sig = File("src/main/assets/bootstrap-servers.json.sig")
    private val pem = File("src/main/assets/update-pubkey.pem")

    @Test
    fun `all signature assets exist`() {
        assertTrue(json.exists(), "bootstrap-servers.json должен быть в assets")
        assertTrue(sig.exists(), "bootstrap-servers.json.sig должен быть в assets")
        assertTrue(pem.exists(), "update-pubkey.pem должен быть в assets")
    }

    @Test
    fun `signature is exactly 64 bytes raw Ed25519`() {
        assertEquals(64, sig.length(), "raw Ed25519 signature = 64 байта")
    }

    @Test
    fun `pubkey parses as 32 byte raw Ed25519`() {
        val raw = Ed25519PemLoader.parsePublicKey(pem.readText())
        assertEquals(32, raw.size, "raw Ed25519 pubkey = 32 байта")
    }

    @Test
    fun `real signature verifies against real pubkey`() {
        val raw = Ed25519PemLoader.parsePublicKey(pem.readText())
        assertTrue(
            Ed25519Verifier.verify(json.readBytes(), sig.readBytes(), raw),
            "snapshot должен валидно подписываться текущим update-pubkey.pem",
        )
    }

    @Test
    fun `tampered json bytes fail verification`() {
        val raw = Ed25519PemLoader.parsePublicKey(pem.readText())
        val tampered = json.readBytes().copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() }
        assertFalse(
            Ed25519Verifier.verify(tampered, sig.readBytes(), raw),
            "изменённый byte → подпись недействительна",
        )
    }

    @Test
    fun `tampered signature bytes fail verification`() {
        val raw = Ed25519PemLoader.parsePublicKey(pem.readText())
        val tampered = sig.readBytes().copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() }
        assertFalse(
            Ed25519Verifier.verify(json.readBytes(), tampered, raw),
            "изменённый byte подписи → invalid",
        )
    }
}
