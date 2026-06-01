package ru.ozero.app.ui.onboarding

import org.junit.jupiter.api.Test
import ru.ozero.commoncrypto.Ed25519PemLoader
import ru.ozero.commoncrypto.Ed25519Verifier
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BootstrapSignatureTest {

    private val json = File("src/main/assets/bootstrap-servers.json")
    private val sig = File("src/main/assets/bootstrap-servers.json.sig")
    private val pem = File("src/main/assets/update-pubkey.pem")

    @Test
    fun `all signature assets exist`() {
        assertTrue(json.exists(), "bootstrap-servers.json must exist in assets")
        assertTrue(sig.exists(), "bootstrap-servers.json.sig must exist in assets")
        assertTrue(pem.exists(), "update-pubkey.pem must exist in assets")
    }

    @Test
    fun `signature is exactly 64 bytes raw Ed25519`() {
        assertEquals(64, sig.length(), "raw Ed25519 signature must be 64 bytes")
    }

    @Test
    fun `pubkey parses as 32 byte raw Ed25519`() {
        val raw = Ed25519PemLoader.parsePublicKey(pem.readText())

        assertEquals(32, raw.size, "raw Ed25519 pubkey must be 32 bytes")
    }

    @Test
    fun `bundled bootstrap snapshot signature verifies against release public key`() {
        val raw = Ed25519PemLoader.parsePublicKey(pem.readText())

        assertTrue(
            Ed25519Verifier.verify(json.readBytes(), sig.readBytes(), raw),
            "bootstrap-servers.json must match bootstrap-servers.json.sig and update-pubkey.pem",
        )
    }

    @Test
    fun `tampered bootstrap snapshot does not verify`() {
        val raw = Ed25519PemLoader.parsePublicKey(pem.readText())
        val tampered = json.readBytes() + 0x0a

        assertFalse(
            Ed25519Verifier.verify(tampered, sig.readBytes(), raw),
            "Any bootstrap snapshot mutation must break Ed25519 verification.",
        )
    }

    @Test
    fun `tampered bootstrap signature does not verify`() {
        val raw = Ed25519PemLoader.parsePublicKey(pem.readText())
        val tamperedSig = sig.readBytes().also { it[0] = (it[0].toInt() xor 0x01).toByte() }

        assertFalse(
            Ed25519Verifier.verify(json.readBytes(), tamperedSig, raw),
            "Any detached signature mutation must break Ed25519 verification.",
        )
    }

    @Test
    fun `bundled bootstrap snapshot is sanitized seed, not live signed credential feed`() {
        assertTrue(json.readText().contains("\"servers\": []"))
    }
}
