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

    data class WarpProxy(
        val socksPort: Int = 10811,
    ) : EngineConfig() {
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

    data class Singbox(
        val beanBlob: ByteArray,
        val protocolType: Int,
        val autoSelectBeanBlobs: List<ByteArray> = emptyList(),
        val chainBeanBlobs: List<ByteArray> = emptyList(),
        val wireGuardConfig: WireGuardOutboundConfig? = null,
        val proxyMode: Boolean = false,
        val dnsServers: List<String> = DEFAULT_DNS_SERVERS,
    ) : EngineConfig() {
        override val engineId = EngineId.SINGBOX
        override fun toString(): String =
            "Singbox(protocol=$protocolType, blobSize=${beanBlob.size}, auto=${autoSelectBeanBlobs.size}" +
                ", chain=${chainBeanBlobs.size}" +
                "${if (wireGuardConfig != null) ", wg=$wireGuardConfig" else ""}, proxyMode=$proxyMode)"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Singbox) return false
            return protocolType == other.protocolType &&
                beanBlob.contentEquals(other.beanBlob) &&
                autoSelectBeanBlobs.size == other.autoSelectBeanBlobs.size &&
                autoSelectBeanBlobs.zip(other.autoSelectBeanBlobs).all { (a, b) -> a.contentEquals(b) } &&
                chainBeanBlobs.size == other.chainBeanBlobs.size &&
                chainBeanBlobs.zip(other.chainBeanBlobs).all { (a, b) -> a.contentEquals(b) } &&
                wireGuardConfig == other.wireGuardConfig &&
                proxyMode == other.proxyMode &&
                dnsServers == other.dnsServers
        }

        override fun hashCode(): Int {
            var result = 31 * protocolType + beanBlob.contentHashCode()
            for (blob in autoSelectBeanBlobs) result = 31 * result + blob.contentHashCode()
            for (blob in chainBeanBlobs) result = 31 * result + blob.contentHashCode()
            result = 31 * result + (wireGuardConfig?.hashCode() ?: 0)
            result = 31 * result + proxyMode.hashCode()
            result = 31 * result + dnsServers.hashCode()
            return result
        }

        companion object {
            val DEFAULT_DNS_SERVERS: List<String> = listOf("1.1.1.1", "1.0.0.1")
        }
    }

    data class Fptn(
        val token: String = "",
        val selectedServerName: String? = null,
        val bypassMethod: String = DEFAULT_BYPASS_METHOD,
        val sniDomain: String = DEFAULT_SNI_DOMAIN,
        val autoSelect: Boolean = true,
        val reconnectOnNetworkChange: Boolean = true,
        val reconnectOnIpChange: Boolean = false,
        val maxReconnectAttempts: Int = 5,
        val reconnectPauseSeconds: Int = 2,
        val resetServerOnDisconnect: Boolean = true,
    ) : EngineConfig() {
        override val engineId = EngineId.FPTN
        override fun toString(): String =
            "Fptn(token=***, server=$selectedServerName, method=$bypassMethod, sni=$sniDomain)"

        companion object {
            const val DEFAULT_BYPASS_METHOD: String = "SNI"
            const val DEFAULT_SNI_DOMAIN: String = "ads.x5.ru"
        }
    }
}
