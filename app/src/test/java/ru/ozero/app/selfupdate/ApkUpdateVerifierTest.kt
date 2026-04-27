package ru.ozero.app.selfupdate

import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.security.SecureRandom
import kotlin.io.path.div
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApkUpdateVerifierTest {

    @Test
    fun acceptsValidSignature(@TempDir tmp: Path) {
        val (priv, pub) = generateKeyPair()
        val apk = (tmp / "ozero.apk").toFile().apply { writeBytes(byteArrayOf(0x01, 0x02, 0x03, 0x04)) }
        val sig = (tmp / "ozero.apk.sig").toFile().apply { writeBytes(sign(priv, apk.readBytes())) }
        val verifier = ApkUpdateVerifier(pub)
        assertTrue(verifier.verify(apk, sig))
    }

    @Test
    fun rejectsTamperedApk(@TempDir tmp: Path) {
        val (priv, pub) = generateKeyPair()
        val apk = (tmp / "ozero.apk").toFile().apply { writeBytes(byteArrayOf(0x01, 0x02, 0x03)) }
        val sig = (tmp / "ozero.apk.sig").toFile().apply { writeBytes(sign(priv, apk.readBytes())) }
                apk.writeBytes(byteArrayOf(0x01, 0x02, 0x99.toByte()))
        val verifier = ApkUpdateVerifier(pub)
        assertFalse(verifier.verify(apk, sig))
    }

    @Test
    fun rejectsWrongKey(@TempDir tmp: Path) {
        val (priv, _) = generateKeyPair()
        val (_, otherPub) = generateKeyPair()
        val apk = (tmp / "ozero.apk").toFile().apply { writeBytes(byteArrayOf(0x01, 0x02)) }
        val sig = (tmp / "ozero.apk.sig").toFile().apply { writeBytes(sign(priv, apk.readBytes())) }
        assertFalse(ApkUpdateVerifier(otherPub).verify(apk, sig))
    }

    @Test
    fun rejectsMissingFiles(@TempDir tmp: Path) {
        val (_, pub) = generateKeyPair()
        val apk = File(tmp.toFile(), "missing.apk")
        val sig = File(tmp.toFile(), "missing.sig")
        assertFalse(ApkUpdateVerifier(pub).verify(apk, sig))
    }

    @Test
    fun rejectsTooShortSignature(@TempDir tmp: Path) {
        val (_, pub) = generateKeyPair()
        val apk = (tmp / "ozero.apk").toFile().apply { writeBytes(byteArrayOf(0x01)) }
        val sig = (tmp / "ozero.apk.sig").toFile().apply { writeBytes(ByteArray(63)) }
        assertFalse(ApkUpdateVerifier(pub).verify(apk, sig))
    }

    @Test
    fun rejectsInvalidPublicKeyLength() {
        val ex = runCatching { ApkUpdateVerifier(ByteArray(31)) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    private fun generateKeyPair(): Pair<ByteArray, ByteArray> {
        val gen = Ed25519KeyPairGenerator().apply { init(Ed25519KeyGenerationParameters(SecureRandom())) }
        val kp = gen.generateKeyPair()
        val priv = (kp.private as Ed25519PrivateKeyParameters).encoded
        val pub = (kp.public as Ed25519PublicKeyParameters).encoded
        return priv to pub
    }

    private fun sign(privKey: ByteArray, message: ByteArray): ByteArray {
                val prefixed = "ozero.update.v1:".toByteArray(Charsets.UTF_8) + message
        val signer = Ed25519Signer().apply { init(true, Ed25519PrivateKeyParameters(privKey, 0)) }
        signer.update(prefixed, 0, prefixed.size)
        return signer.generateSignature()
    }
}
