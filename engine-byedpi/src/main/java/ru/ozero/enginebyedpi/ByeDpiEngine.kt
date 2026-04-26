package ru.ozero.enginebyedpi

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

class ByeDpiEngine(
    private val proxy: ByeDpiProxy = ByeDpiProxy(),
) : Engine {

    override val id = EngineId.BYEDPI

    override val capabilities = EngineCapabilities(
        supportsTcp = true,
        supportsUdp = false,
        supportsDoH = false,
        localOnly = true,
        requiresServer = false,
    )

    @Volatile private var activeSocksPort: Int = 0
    private val _stats = MutableStateFlow(EngineStats())

    override suspend fun start(config: EngineConfig): StartResult {
        require(config is EngineConfig.ByeDpi) { "ByeDpiEngine требует EngineConfig.ByeDpi" }
        if (!ByeDpiProxy.libraryLoaded) {
            Log.e(TAG, "native lib не загружена — устройство не поддерживает или stripped APK")
            return StartResult.Failure(reason = "byedpi native library не загружена")
        }
        Log.i(TAG, "start socksPort=${config.socksPort} args=${config.args}")
        return withContext(Dispatchers.IO) {
            val args = buildArgs(config)
            val code = proxy.jniStartProxy(args)
            if (code == 0) {
                // Порт активируется только ПОСЛЕ успешного старта, иначе probe может
                // посчитать движок живым до того как jni подтвердил запуск.
                activeSocksPort = config.socksPort
                Log.i(TAG, "started OK на порту ${config.socksPort}")
                StartResult.Success(socksPort = config.socksPort)
            } else {
                Log.e(TAG, "jniStartProxy код $code")
                StartResult.Failure(reason = "jniStartProxy вернул код $code")
            }
        }
    }

    override suspend fun stop() {
        Log.i(TAG, "stop")
        withContext(Dispatchers.IO) {
            // runCatching: иначе JNI exception оставит engine в состоянии "активен" навсегда,
            // блокируя повторный старт (AwgEngine.stop сделан так же).
            runCatching { proxy.jniStopProxy() }
                .onFailure { Log.w(TAG, "jniStopProxy исключение: ${it.message}") }
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

    private fun buildArgs(config: EngineConfig.ByeDpi): Array<String> {
        val extra =
            config.args.trim()
                .takeIf { it.isNotEmpty() }
                ?.split("\\s+".toRegex())
                .orEmpty()
        return (listOf("-p", config.socksPort.toString()) + extra).toTypedArray()
    }

    private companion object {
        const val TAG = "ByeDpiEngine"
    }
}
