package ru.ozero.desktop.engine

import ru.ozero.desktop.model.EngineId

class UnavailableEngine(
    override val id: EngineId,
    override val binaryName: String,
    private val reason: String,
) : DesktopEngine {

    override val isAvailableOnPlatform = false

    override suspend fun start(config: EngineConfig): EngineStartResult =
        EngineStartResult.PlatformUnavailable(reason)

    override suspend fun stop() {}
    override fun isRunning() = false
    override fun listeningPort() = 0
}

object DesktopEngineRegistry {

    private val engines: Map<EngineId, DesktopEngine> = buildMap {
        put(EngineId.SINGBOX, SingboxDesktopEngine())
        put(EngineId.BYEDPI, ByeDpiDesktopEngine())
        put(EngineId.WARP, WarpDesktopEngine())
        put(
            EngineId.MASTERDNS,
            UnavailableEngine(EngineId.MASTERDNS, "mdnsvpn", "MasterDNS is server-side only (Linux VPS)"),
        )
        put(
            EngineId.URNETWORK,
            UnavailableEngine(EngineId.URNETWORK, "urnetwork", "URnetwork SDK requires native Go binding"),
        )
        put(
            EngineId.FPTN,
            UnavailableEngine(EngineId.FPTN, "fptn", "FPTN desktop build not available yet"),
        )
    }

    fun get(id: EngineId): DesktopEngine? = engines[id]

    fun available(): List<DesktopEngine> = engines.values.filter { it.isAvailableOnPlatform }

    fun all(): Map<EngineId, DesktopEngine> = engines
}
