package ru.ozero.enginescore

import ru.ozero.enginescore.settings.HostsMode

sealed class EngineConfig {
    abstract val engineId: EngineId

    data class ByeDpi(
        val args: String = "-Kt,h -o1 -e97 -An -Ku -a1 -An",
        val socksPort: Int = 1080,
        val hostsMode: HostsMode = HostsMode.DISABLED,
        val hosts: List<String> = emptyList(),
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

    data object Warp : EngineConfig() {
        override val engineId = EngineId.WARP
    }

    data class MasterDns(
        val configToml: String,
        val resolvers: List<String>,
        val socksPort: Int = 18000,
    ) : EngineConfig() {
        override val engineId = EngineId.MASTERDNS
        override fun toString(): String =
            "MasterDns(configToml=***, resolvers=${resolvers.size} entries, socksPort=$socksPort)"
    }

    data object Fptn : EngineConfig() {
        override val engineId = EngineId.FPTN
    }
}
