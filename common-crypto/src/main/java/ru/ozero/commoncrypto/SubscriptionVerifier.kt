package ru.ozero.commoncrypto

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.io.InputStream

object SubscriptionVerifier {

    private val BOOTSTRAP_DOMAIN = "ozero.bootstrap.v1:".toByteArray(Charsets.UTF_8)
    private val UPDATE_DOMAIN = "ozero.update.v1:".toByteArray(Charsets.UTF_8)
    private const val STREAM_CHUNK_BYTES = 64 * 1024

    fun verifyBootstrap(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean =
        verify(BOOTSTRAP_DOMAIN + message, signature, publicKey)

    fun verifyUpdate(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean =
        verify(UPDATE_DOMAIN + message, signature, publicKey)

    fun verifyUpdate(messageStream: InputStream, signature: ByteArray, publicKey: ByteArray): Boolean {
        return verifyUpdate(messageStream, signature, publicKey) { Ed25519Signer() }
    }

    @Suppress("SwallowedException")
    internal fun verifyUpdate(
        messageStream: InputStream,
        signature: ByteArray,
        publicKey: ByteArray,
        signerFactory: () -> Ed25519Signer,
    ): Boolean {
        if (signature.size != 64 || publicKey.size != 32) return false
        return try {
            val signer = signerFactory()
            signer.init(false, Ed25519PublicKeyParameters(publicKey))
            signer.update(UPDATE_DOMAIN, 0, UPDATE_DOMAIN.size)
            val buf = ByteArray(STREAM_CHUNK_BYTES)
            while (true) {
                val n = messageStream.read(buf)
                if (n < 0) break
                if (n > 0) signer.update(buf, 0, n)
            }
            signer.verifySignature(signature)
        } catch (e: IllegalArgumentException) {
            false
        } catch (e: org.bouncycastle.crypto.DataLengthException) {
            false
        } catch (e: java.io.IOException) {
            false
        } catch (e: RuntimeException) {
            false
        }
    }

    fun verify(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        return verify(message, signature, publicKey) { Ed25519Signer() }
    }

    @Suppress("SwallowedException")
    internal fun verify(
        message: ByteArray,
        signature: ByteArray,
        publicKey: ByteArray,
        signerFactory: () -> Ed25519Signer,
    ): Boolean {
        if (signature.size != 64 || publicKey.size != 32) return false
        return try {
            val params = Ed25519PublicKeyParameters(publicKey)
            val signer = signerFactory()
            signer.init(false, params)
            signer.update(message, 0, message.size)
            signer.verifySignature(signature)
        } catch (e: IllegalArgumentException) {
            false
        } catch (e: org.bouncycastle.crypto.DataLengthException) {
            false
        } catch (e: RuntimeException) {
            false
        }
    }
}
