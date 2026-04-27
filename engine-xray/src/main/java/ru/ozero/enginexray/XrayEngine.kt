package ru.ozero.enginexray

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import ru.ozero.coreapi.Engine
import ru.ozero.coreapi.EngineCapabilities
import ru.ozero.coreapi.EngineConfig
import ru.ozero.coreapi.EngineId
import ru.ozero.coreapi.EngineStats
import ru.ozero.coreapi.ProbeResult
import ru.ozero.coreapi.StartResult
import ru.ozero.coreorchestrator.probe.Socks5HandshakeProbe

class XrayEngine(
    private val delegate: LibXrayDelegate,
) : Engine {

    override val id = EngineId.XRAY

    override val capabilities = EngineCapabilities(
        supportsTcp = true,
        supportsUdp = true,
        supportsDoH = true,
        localOnly = false,
        requiresServer = true,
    )

    @Volatile private var activeSocksPort: Int = 0
    private val _stats = MutableStateFlow(EngineStats())

    override suspend fun start(config: EngineConfig): StartResult {
        require(config is EngineConfig.Xray) { "XrayEngine требует EngineConfig.Xray" }
        if (config.configJson.isBlank()) {
            Log.e(TAG, "start: пустой configJson")
            return StartResult.Failure(reason = "configJson пуст")
        }
        Log.i(TAG, "start socksPort=${config.socksPort} cfgLen=${config.configJson.length}")
        return withContext(Dispatchers.IO) {
            try {
                val code = delegate.startXray(config.configJson)
                if (code == 0) {
                    activeSocksPort = config.socksPort
                    Log.i(TAG, "started OK на порту ${config.socksPort}")
                    StartResult.Success(socksPort = config.socksPort)
                } else {
                    Log.e(TAG, "startXray код $code")
                    StartResult.Failure(reason = "startXray вернул код $code")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "startXray исключение", e)
                StartResult.Failure(reason = e.message ?: "native error", cause = e)
            }
        }
    }

    override suspend fun stop() {
        Log.i(TAG, "stop")
        withContext(Dispatchers.IO) {
            runCatching { delegate.stopXray() }
                .onFailure { Log.w(TAG, "stopXray исключение: ${it.message}") }
            activeSocksPort = 0
        }
    }

    override suspend fun probe(): ProbeResult {
        val port = activeSocksPort
        if (port == 0) {
            Log.w(TAG, "probe: движок не запущен")
            return ProbeResult.Failure(reason = "движок не запущен")
        }
        return try {
            val latency = Socks5HandshakeProbe.probe("127.0.0.1", port, timeoutMs = 3_000)
            Log.d(TAG, "probe OK latency=${latency}ms")
            ProbeResult.Success(latencyMs = latency)
        } catch (e: Exception) {
            Log.w(TAG, "probe failed: ${e.message}")
            ProbeResult.Failure(reason = e.message ?: "connection refused")
        }
    }

    override fun stats(): Flow<EngineStats> = _stats.asStateFlow()

    private companion object {
        const val TAG = "XrayEngine"
    }
}
