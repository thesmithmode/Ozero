package ru.ozero.enginescore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

class EnginePluginRecoverTest {

    private class StubPlugin : EnginePlugin {
        override val id = EngineId.BYEDPI
        override val capabilities = EngineCapabilities(
            supportsTcp = true,
            supportsUdp = false,
            supportsDoH = false,
            localOnly = true,
            requiresServer = false,
            supportsUpstreamSocks = false,
        )
        private val _stats = MutableStateFlow(EngineStats())
        override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult =
            StartResult.Success(socksPort = 1080)
        override suspend fun stop() = Unit
        override suspend fun probe(): ProbeResult = ProbeResult.Failure("stub")
        override fun stats(): Flow<EngineStats> = _stats
    }

    @Test
    fun `default recover возвращает NotSupported`() = runTest {
        val plugin = StubPlugin()
        assertIs<EnginePlugin.RecoverResult.NotSupported>(plugin.recover())
    }

    @Test
    fun `RecoverResult Success Failed NotSupported все различимы`() {
        val success: EnginePlugin.RecoverResult = EnginePlugin.RecoverResult.Success
        val failed: EnginePlugin.RecoverResult = EnginePlugin.RecoverResult.Failed("reason")
        val notSupported: EnginePlugin.RecoverResult = EnginePlugin.RecoverResult.NotSupported
        assertIs<EnginePlugin.RecoverResult.Success>(success)
        assertIs<EnginePlugin.RecoverResult.Failed>(failed)
        assertIs<EnginePlugin.RecoverResult.NotSupported>(notSupported)
    }
}
