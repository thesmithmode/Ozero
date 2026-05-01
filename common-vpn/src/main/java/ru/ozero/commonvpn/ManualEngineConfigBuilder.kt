package ru.ozero.commonvpn

import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.settings.HostsMode
import ru.ozero.enginescore.settings.SettingsModel

object ManualEngineConfigBuilder {
    fun build(engineId: EngineId, settings: SettingsModel?): EngineConfig? = when (engineId) {
        EngineId.BYEDPI -> EngineConfig.ByeDpi(
            args = settings?.byedpiWinningArgs?.takeIf { it.isNotBlank() }
                ?: EngineConfig.ByeDpi().args,
            hostsMode = settings?.hostsMode ?: HostsMode.DISABLED,
            hosts = settings?.hosts.orEmpty(),
        )
        EngineId.WARP -> EngineConfig.Warp
        EngineId.URNETWORK -> EngineConfig.Urnetwork(
            jwtToken = settings?.urnetworkJwt.orEmpty(),
        )
        EngineId.XRAY, EngineId.HYSTERIA2, EngineId.AMNEZIA,
        EngineId.TOR, EngineId.NAIVE, EngineId.FPTN -> null
    }
}
