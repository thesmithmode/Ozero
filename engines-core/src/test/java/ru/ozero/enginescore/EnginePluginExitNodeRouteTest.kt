package ru.ozero.enginescore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EnginePluginExitNodeRouteTest {

    @Test
    fun `ProviderLabel known ip is preserved in legacy ipProbeRoute`() = runTest {
        val plugin = StaticExitNodePlugin(
            ExitNodeStrategy.ProviderLabel(
                label = "Germany",
                ip = "198.51.100.70",
                countryCode = "DE",
            ),
        )

        val route = plugin.ipProbeRoute(0)

        val location = assertIs<IpProbeRoute.StaticLocation>(route)
        assertEquals("Germany", location.country)
        assertEquals("DE", location.countryCode)
        assertEquals("198.51.100.70", location.ip)
    }

    private class StaticExitNodePlugin(
        private val strategy: ExitNodeStrategy,
    ) : EnginePlugin {
        override val id = EngineId.FPTN
        override val capabilities = EngineCapabilities(
            supportsTcp = true,
            supportsUdp = true,
            supportsDoH = false,
            localOnly = false,
            requiresServer = true,
            supportsUpstreamSocks = false,
        )

        override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult =
            StartResult.Failure("not used")

        override suspend fun stop() = Unit

        override suspend fun probe(): ProbeResult = ProbeResult.Failure("not used")

        override fun stats(): Flow<EngineStats> = flowOf(EngineStats())

        override suspend fun exitNodeStrategy(socksPort: Int): ExitNodeStrategy = strategy
    }
}
