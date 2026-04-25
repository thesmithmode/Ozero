package ru.ozero.enginenaive

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
import ru.ozero.enginenaive.config.JsonWriter

/**
 * NaiveProxy engine. HTTP/2 (или QUIC) CONNECT через Chromium net stack —
 * fingerprint TLS = реальный Chrome. Запускает subprocess naiveproxy с inline JSON конфигом.
 */
class NaiveEngine(
    private val delegate: LibNaiveDelegate,
) : Engine {

    override val id = EngineId.NAIVE

    override val capabilities = EngineCapabilities(
        supportsTcp = true,
        supportsUdp = false, // naive over HTTP/2 = TCP; QUIC variant обрабатывается прозрачно
        supportsDoH = false,
        localOnly = false,
        requiresServer = true,
    )

    @Volatile private var activeSocksPort: Int = 0
    private val _stats = MutableStateFlow(EngineStats())

    override suspend fun start(config: EngineConfig): StartResult {
        require(config is EngineConfig.Naive) { "NaiveEngine требует EngineConfig.Naive" }
        // EngineConfig.Naive хранит proxyUrl и socksPort — но NaiveEngine ожидает
        // на вход уже собранный JSON-конфиг через Naive-specific обвязку. Поскольку
        // упрощённый EngineConfig.Naive имеет только proxyUrl, кладём весь JSON в proxyUrl
        // и парсим: если начинается с `{` — считаем конфигом, иначе строим обёртку.
        val jsonConfig = if (config.proxyUrl.startsWith("{")) {
            config.proxyUrl
        } else {
            JsonWriter.write(
                linkedMapOf<String, Any?>(
                    "listen" to "socks://127.0.0.1:${config.socksPort}",
                    "proxy" to config.proxyUrl,
                    "log" to "",
                ),
            )
        }
        if (jsonConfig.isBlank()) {
            Log.e(TAG, "start: пустой конфиг")
            return StartResult.Failure(reason = "конфиг пуст")
        }
        Log.i(TAG, "start socksPort=${config.socksPort} cfgLen=${jsonConfig.length}")
        return withContext(Dispatchers.IO) {
            try {
                val code = delegate.startNaive(jsonConfig)
                if (code == 0) {
                    activeSocksPort = config.socksPort
                    Log.i(TAG, "started OK на порту ${config.socksPort}")
                    StartResult.Success(socksPort = config.socksPort)
                } else {
                    Log.e(TAG, "startNaive код $code")
                    StartResult.Failure(reason = "startNaive вернул код $code")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "startNaive исключение", e)
                StartResult.Failure(reason = e.message ?: "spawn error", cause = e)
            }
        }
    }

    override suspend fun stop() {
        Log.i(TAG, "stop")
        withContext(Dispatchers.IO) {
            runCatching { delegate.stopNaive() }
                .onFailure { Log.w(TAG, "stopNaive исключение: ${it.message}") }
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
        const val TAG = "NaiveEngine"
    }
}
