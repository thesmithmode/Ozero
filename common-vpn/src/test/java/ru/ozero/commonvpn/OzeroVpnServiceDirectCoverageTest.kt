package ru.ozero.commonvpn

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.ChainOrchestrator
import ru.ozero.enginescore.EngineCapabilities
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EngineId.BYEDPI
import ru.ozero.enginescore.EngineId.WARP
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.EngineStats
import ru.ozero.enginescore.ProbeResult
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream
import ru.ozero.enginescore.settings.SettingsModel
import kotlin.test.assertEquals

class OzeroVpnServiceDirectCoverageTest {

    @Test
    fun `engine extras is empty before chain orchestrator injection`() {
        val service = OzeroVpnService()

        assertEquals("", service.callEngineExtras())
    }

    @Test
    fun `engine extras stays empty when injected chain has no active engines`() {
        val service = OzeroVpnService()
        service.chainOrchestrator = ChainOrchestrator(
            setOf(
                MinimalEngine(EngineId.SINGBOX),
                MinimalEngine(EngineId.BYEDPI),
            ),
        )

        val extras = service.callEngineExtras()

        assertEquals("", extras)
    }

    @Test
    fun `engine extras joins active engine display names when chain has started engines`() {
        val chain = ChainOrchestrator(setOf(BYEDPI, WARP).map(::MinimalEngine).toSet())
        val startedField = ChainOrchestrator::class.java.getDeclaredField("started")
        startedField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val started = startedField.get(chain) as MutableList<EnginePlugin>
        started += MinimalEngine(BYEDPI)
        started += MinimalEngine(WARP)

        val service = OzeroVpnService()
        service.chainOrchestrator = chain

        val extras = service.callEngineExtras()

        assertEquals("ByeDPI + WARP", extras)
    }

    @Test
    fun `engine extras falls back to empty string when active engines request fails`() {
        val chain = mockk<ChainOrchestrator>()
        every { chain.activeEngines() } throws IllegalStateException("boom")

        val service = OzeroVpnService()
        service.chainOrchestrator = chain

        val extras = service.callEngineExtras()

        assertEquals("", extras)
    }

    private fun OzeroVpnService.callEngineExtras(): String {
        val method = OzeroVpnService::class.java.getDeclaredMethod("engineExtras")
        method.isAccessible = true
        return method.invoke(this) as String
    }

    private class MinimalEngine(
        override val id: EngineId,
    ) : EnginePlugin {
        override val capabilities = EngineCapabilities(
            supportsTcp = true,
            supportsUdp = true,
            supportsDoH = false,
            localOnly = true,
            requiresServer = false,
            supportsUpstreamSocks = false,
            providesLocalSocks = true,
        )

        override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult =
            StartResult.Success(0)

        override suspend fun stop() = Unit

        override suspend fun probe(): ProbeResult = ProbeResult.Success(0)

        override fun stats(): Flow<EngineStats> = emptyFlow()

        override fun buildManualConfig(settings: SettingsModel?): EngineConfig? = null
    }
}
