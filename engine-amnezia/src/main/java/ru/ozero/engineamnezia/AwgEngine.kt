package ru.ozero.engineamnezia

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
import java.util.concurrent.atomic.AtomicBoolean

class AwgEngine(
    private val delegate: LibAwgDelegate,
) : Engine {

    override val id = EngineId.AMNEZIA

    override val capabilities = EngineCapabilities(
        supportsTcp = true,
        supportsUdp = true,
        supportsDoH = true,
        localOnly = false,
        requiresServer = true,
    )

    private val started = AtomicBoolean(false)
    private val _stats = MutableStateFlow(EngineStats())

    override suspend fun start(config: EngineConfig): StartResult {
        require(config is EngineConfig.Amnezia) { "AwgEngine требует EngineConfig.Amnezia" }
        if (config.configJson.isBlank()) {
            Log.e(TAG, "start: пустой configJson")
            return StartResult.Failure(reason = "configJson пуст")
        }
        Log.i(TAG, "start cfgLen=${config.configJson.length}")
        return withContext(Dispatchers.IO) {
            try {
                val code = delegate.startAwg(config.configJson)
                if (code == 0) {
                    started.set(true)
                    Log.i(TAG, "started OK")
                    StartResult.Success(socksPort = config.socksPort)
                } else {
                    Log.e(TAG, "startAwg код $code")
                    StartResult.Failure(reason = "startAwg вернул код $code")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "startAwg исключение", e)
                StartResult.Failure(reason = e.message ?: "native error", cause = e)
            }
        }
    }

    override suspend fun stop() {
        Log.i(TAG, "stop")
        withContext(Dispatchers.IO) {
            runCatching { delegate.stopAwg() }
                .onFailure { Log.w(TAG, "stopAwg исключение: ${it.message}") }
            started.set(false)
        }
    }

    override suspend fun probe(): ProbeResult {
        if (!started.get()) {
            Log.w(TAG, "probe: движок не запущен")
            return ProbeResult.Failure(reason = "движок не запущен")
        }
        return withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val up = runCatching { delegate.isUp() }.getOrDefault(false)
            val latency = System.currentTimeMillis() - start
            if (up) {
                Log.d(TAG, "probe OK latency=${latency}ms")
                ProbeResult.Success(latencyMs = latency)
            } else {
                Log.w(TAG, "probe failed: tun не активен")
                ProbeResult.Failure(reason = "tun не активен")
            }
        }
    }

    override fun stats(): Flow<EngineStats> = _stats.asStateFlow()

    private companion object {
        const val TAG = "AwgEngine"
    }
}
