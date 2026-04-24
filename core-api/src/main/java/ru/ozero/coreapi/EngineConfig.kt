package ru.ozero.coreapi

sealed class EngineConfig {
    data class ByeDpi(
        val args: String = "-Ku -a1 -An -o1 -At,r,s -d1",
        val socksPort: Int = 1080
    ) : EngineConfig()

    data class Xray(
        val configJson: String,
        val socksPort: Int = 10808
    ) : EngineConfig()

    data class Amnezia(
        val configJson: String,
        val socksPort: Int = 10808
    ) : EngineConfig()

    data class Tor(
        val bridges: List<String> = emptyList(),
        val socksPort: Int = 9050
    ) : EngineConfig()

    data class Naive(
        val proxyUrl: String,
        val socksPort: Int = 1080
    ) : EngineConfig()
}
