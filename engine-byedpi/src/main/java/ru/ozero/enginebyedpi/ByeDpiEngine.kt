package ru.ozero.enginebyedpi

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.ozero.coreapi.Engine
import ru.ozero.coreapi.EngineCapabilities
import ru.ozero.coreapi.EngineConfig
import ru.ozero.coreapi.EngineId
import ru.ozero.coreapi.EngineStats
import ru.ozero.coreapi.ProbeResult
import ru.ozero.coreapi.StartResult
import ru.ozero.coreorchestrator.probe.Socks5HandshakeProbe
import java.util.concurrent.atomic.AtomicReference

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
    private val proxyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val proxyJobRef = AtomicReference<Job?>(null)

    override suspend fun start(config: EngineConfig): StartResult {
        require(config is EngineConfig.ByeDpi) { "ByeDpiEngine требует EngineConfig.ByeDpi" }
        if (!ByeDpiProxy.libraryLoaded) {
            Log.e(TAG, "native lib не загружена — устройство не поддерживает или stripped APK")
            return StartResult.Failure(reason = "byedpi native library не загружена")
        }
        Log.i(TAG, "start socksPort=${config.socksPort} args=${config.args}")
        val args = buildArgs(config)
        val proxyJob = proxyScope.launch {
            val code = runCatching { proxy.jniStartProxy(args) }
                .onFailure { Log.e(TAG, "jniStartProxy threw", it) }
                .getOrElse { -1 }
            if (code != 0) {
                Log.e(TAG, "jniStartProxy завершился с кодом $code")
            } else {
                Log.i(TAG, "jniStartProxy event-loop завершён нормально")
            }
            activeSocksPort = 0
        }
        proxyJobRef.getAndSet(proxyJob)?.cancel()

        val readyAt = withContext(Dispatchers.IO) {
            waitSocksReady(config.socksPort)
        }
        return if (readyAt > 0) {
            activeSocksPort = config.socksPort
            Log.i(TAG, "started OK socksPort=${config.socksPort} readyMs=$readyAt")
            StartResult.Success(socksPort = config.socksPort)
        } else {
            Log.e(TAG, "byedpi не вышел на порт ${config.socksPort} за ${READY_TIMEOUT_MS}ms")
            runCatching { proxy.jniStopProxy() }
            proxyJob.cancel()
            StartResult.Failure(reason = "byedpi не открыл socks порт ${config.socksPort}")
        }
    }

    private suspend fun waitSocksReady(port: Int): Long {
        val started = System.currentTimeMillis()
        while (System.currentTimeMillis() - started < READY_TIMEOUT_MS) {
            val ok = runCatching { Socks5HandshakeProbe.probe("127.0.0.1", port, READY_PROBE_TIMEOUT_MS) }
                .isSuccess
            if (ok) return System.currentTimeMillis() - started
            delay(READY_RETRY_MS)
        }
        return -1
    }

    override suspend fun stop() {
        Log.i(TAG, "stop")
        withContext(Dispatchers.IO) {
            runCatching { proxy.jniStopProxy() }
                .onFailure { Log.w(TAG, "jniStopProxy исключение: ${it.message}") }
            proxyJobRef.getAndSet(null)?.cancel()
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
        const val READY_TIMEOUT_MS = 5_000L
        const val READY_PROBE_TIMEOUT_MS = 500
        const val READY_RETRY_MS = 100L
    }
}
