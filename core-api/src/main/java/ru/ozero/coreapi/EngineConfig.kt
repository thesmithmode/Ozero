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

    data class Hysteria2(
        val configJson: String,
        val socksPort: Int = 10809
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

    data class Urnetwork(
        val jwtToken: String,
        val apiUrl: String = "https://api.urnetwork.com",
        val region: String? = null,
        val mode: String = "consumer",
        val socksPort: Int = 10810,
    ) : EngineConfig() {
        override fun toString(): String =
            "Urnetwork(jwtToken=***, apiUrl=$apiUrl, region=$region, mode=$mode, socksPort=$socksPort)"
    }
}
