package ru.ozero.enginewarp

import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.security.SecureRandom
import java.util.Base64

interface WireguardKeyPairGenerator {
    fun generate(): Pair<String, String>
}

class StubWireguardKeyPairGenerator : WireguardKeyPairGenerator {
    override fun generate(): Pair<String, String> = "stub-priv-base64" to "stub-pub-base64"
}

// Cloudflare WARP register API ожидает реальный X25519 base64-encoded public key.
// StubWireguardKeyPairGenerator возвращает literal "stub-pub-base64" → 400 invalid public key.
class RealCurve25519KeyPairGenerator(
    private val random: SecureRandom = SecureRandom(),
) : WireguardKeyPairGenerator {
    override fun generate(): Pair<String, String> {
        val gen = X25519KeyPairGenerator()
        gen.init(X25519KeyGenerationParameters(random))
        val pair = gen.generateKeyPair()
        val priv = (pair.private as X25519PrivateKeyParameters).encoded
        val pub = (pair.public as X25519PublicKeyParameters).encoded
        val encoder = Base64.getEncoder()
        return encoder.encodeToString(priv) to encoder.encodeToString(pub)
    }
}
