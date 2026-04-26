package ru.ozero.engineurnetwork

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

/**
 * URnetwork P2P engine.
 *
 * Использует децентрализованную P2P сеть urnetwork.com для маршрутизации
 * трафика. Работает как fallback когда все primary engines (ByeDPI, Xray,
 * Hysteria2, AmneziaWG) недоступны.
 *
 * SDK: https://github.com/urnetwork/sdk (gomobile bind → URnetworkSdk.aar)
 * Java package: com.bringyour
 *
 * Ограничение: URnetwork требует JWT токен авторизации — сам по себе не
 * является «serverless» в смысле zero-config; требует предварительной
 * регистрации на urnetwork.com.
 */
class UrnetworkEngine(
    private val delegate: UrnetworkDelegate,
) : Engine {

    override val id = EngineId.URNETWORK

    override val capabilities = EngineCapabilities(
        supportsTcp = true,
        supportsUdp = true,
        supportsDoH = true,
        localOnly = false,
        requiresServer = false, // P2P — централизованный сервер не нужен
    )

    @Volatile private var activeSocksPort: Int = 0
    private val _stats = MutableStateFlow(EngineStats())

    override suspend fun start(config: EngineConfig): StartResult {
        require(config is EngineConfig.Urnetwork) {
            "UrnetworkEngine требует EngineConfig.Urnetwork, получен ${config::class.simpleName}"
        }

        if (config.jwtToken.isBlank()) {
            Log.e(TAG, "start: пустой jwtToken")
            return StartResult.Failure(reason = "jwtToken пуст")
        }

        Log.i(TAG, "start jwtLen=${config.jwtToken.length} mode=${config.mode} region=${config.region}")

        return withContext(Dispatchers.IO) {
            try {
                val mode = parseMode(config.mode)
                val ok = delegate.connect(
                    jwtToken = config.jwtToken,
                    apiUrl = config.apiUrl,
                    region = config.region,
                    mode = mode,
                )
                if (ok) {
                    // Ждём перехода в CONNECTED (до CONNECT_TIMEOUT_MS)
                    val connected = waitForConnected(timeoutMs = CONNECT_TIMEOUT_MS)
                    if (connected) {
                        activeSocksPort = config.socksPort
                        Log.i(TAG, "started OK port=$activeSocksPort sdk=${delegate.sdkVersion()}")
                        StartResult.Success(socksPort = activeSocksPort)
                    } else {
                        Log.e(TAG, "start timeout — статус=${delegate.connectionStatus()}")
                        StartResult.Failure(reason = "таймаут подключения к URnetwork (${CONNECT_TIMEOUT_MS}ms)")
                    }
                } else {
                    Log.e(TAG, "delegate.connect вернул false")
                    StartResult.Failure(reason = "инициализация URnetwork SDK завершилась ошибкой")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "start исключение", e)
                StartResult.Failure(reason = e.message ?: "URnetwork native error", cause = e)
            }
        }
    }

    override suspend fun stop() {
        Log.i(TAG, "stop")
        withContext(Dispatchers.IO) {
            runCatching { delegate.disconnect() }
                .onFailure { Log.w(TAG, "disconnect исключение: ${it.message}") }
            activeSocksPort = 0
        }
    }

    override suspend fun probe(): ProbeResult {
        if (activeSocksPort == 0) {
            Log.w(TAG, "probe: движок не запущен")
            return ProbeResult.Failure(reason = "движок не запущен")
        }
        return withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val status = runCatching { delegate.connectionStatus() }
                .getOrDefault(UrnetworkConnectionStatus.DISCONNECTED)
            val latency = System.currentTimeMillis() - start

            if (status == UrnetworkConnectionStatus.CONNECTED) {
                Log.d(TAG, "probe OK latency=${latency}ms")
                ProbeResult.Success(latencyMs = latency)
            } else {
                Log.w(TAG, "probe failed: статус=$status")
                ProbeResult.Failure(reason = "URnetwork статус=$status")
            }
        }
    }

    override fun stats(): Flow<EngineStats> = _stats.asStateFlow()

    private suspend fun waitForConnected(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            when (delegate.connectionStatus()) {
                UrnetworkConnectionStatus.CONNECTED -> return true
                UrnetworkConnectionStatus.FAILED -> {
                    Log.w(TAG, "waitForConnected: FAILED")
                    return false
                }
                else -> delay(POLL_INTERVAL_MS)
            }
        }
        return false
    }

    private fun parseMode(mode: String): UrnetworkMode = when (mode.lowercase()) {
        "provider" -> UrnetworkMode.PROVIDER
        else -> UrnetworkMode.CONSUMER
    }

    private companion object {
        const val TAG = "UrnetworkEngine"
        const val CONNECT_TIMEOUT_MS = 30_000L
        const val POLL_INTERVAL_MS = 500L
    }
}
