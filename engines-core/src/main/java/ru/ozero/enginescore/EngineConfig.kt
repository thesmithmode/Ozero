package ru.ozero.enginescore

sealed class EngineConfig {
    abstract val engineId: EngineId

    data class ByeDpi(
        val args: String = "-Ku -a1 -An -o1 -At,r,s -d1",
        val socksPort: Int = 1080,
    ) : EngineConfig() {
        override val engineId = EngineId.BYEDPI
    }

    data class Xray(
        val configJson: String,
        val socksPort: Int = 10808,
    ) : EngineConfig() {
        override val engineId = EngineId.XRAY
    }

    data class Hysteria2(
        val configJson: String,
        val socksPort: Int = 10809,
    ) : EngineConfig() {
        override val engineId = EngineId.HYSTERIA2
    }

    data class Amnezia(
        val configJson: String,
        val socksPort: Int = 10808,
    ) : EngineConfig() {
        override val engineId = EngineId.AMNEZIA
    }

    data class Tor(
        val bridges: List<String> = emptyList(),
        val socksPort: Int = 9050,
    ) : EngineConfig() {
        override val engineId = EngineId.TOR
    }

    data class Naive(
        val proxyUrl: String,
        val socksPort: Int = 1080,
    ) : EngineConfig() {
        override val engineId = EngineId.NAIVE
    }

    data class Urnetwork(
        val jwtToken: String,
        val apiUrl: String = "https://api.urnetwork.com",
        val region: String? = null,
        val mode: String = "consumer",
        val socksPort: Int = 10810,
    ) : EngineConfig() {
        override val engineId = EngineId.URNETWORK
        override fun toString(): String =
            "Urnetwork(jwtToken=***, apiUrl=$apiUrl, region=$region, mode=$mode, socksPort=$socksPort)"
    }
}
