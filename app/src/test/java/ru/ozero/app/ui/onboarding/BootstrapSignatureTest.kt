package ru.ozero.app.ui.onboarding

import org.junit.jupiter.api.Test
import ru.ozero.commoncrypto.Ed25519PemLoader
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun `bundled bootstrap snapshot is sanitized seed, not live signed credential feed`() {
        assertTrue(json.readText().contains("\"servers\": []"))
    }
}
