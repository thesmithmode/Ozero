package ru.ozero.enginewarp

interface WireguardKeyPairGenerator {
    fun generate(): Pair<String, String>
}

class StubWireguardKeyPairGenerator : WireguardKeyPairGenerator {
    override fun generate(): Pair<String, String> = "stub-priv-base64" to "stub-pub-base64"
}
