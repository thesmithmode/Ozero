package ru.ozero.enginewarp

import com.wireguard.crypto.KeyPair

interface WireguardKeyPairGenerator {
    fun generate(): Pair<String, String>
}

class RealWireguardKeyPairGenerator : WireguardKeyPairGenerator {
    override fun generate(): Pair<String, String> {
        val kp = KeyPair()
        return kp.privateKey.toBase64() to kp.publicKey.toBase64()
    }
}

class StubWireguardKeyPairGenerator : WireguardKeyPairGenerator {
    override fun generate(): Pair<String, String> = "stub-priv-base64" to "stub-pub-base64"
}
