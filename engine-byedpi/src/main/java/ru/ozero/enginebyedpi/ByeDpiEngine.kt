package ru.ozero.enginebyedpi

import android.util.Log
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.withTimeoutOrNull
import ru.ozero.enginescore.EngineCapabilities
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EnginePreflight
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.EngineStats
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginescore.ProbeResult
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream
import ru.ozero.enginescore.IpProbeRoute
import ru.ozero.enginescore.probe.Socks5HandshakeProbe
import ru.ozero.enginescore.settings.HostsMode
import java.util.concurrent.atomic.AtomicReference

class ByeDpiEngine(
    private val proxy: ByeDpiProxyContract = ByeDpiProxy(),
    private val socksProbe: suspend (String, Int, Int) -> Long = Socks5HandshakeProbe::probe,
    private val readyProbeTimeoutMs: Int = READY_PROBE_TIMEOUT_MS,
    private val readyTotalTimeoutMs: Long = READY_TIMEOUT_MS,
) : EnginePlugin {

    override val id = EngineId.BYEDPI

    override val capabilities = EngineCapabilities(
        supportsTcp = true,
        supportsUdp = false,
        supportsDoH = false,
        localOnly = true,
        requiresServer = false,
        supportsUpstreamSocks = false,
    )

    @Volatile private var activeSocksPort: Int = 0
    private val _stats = MutableStateFlow(EngineStats())
    private val proxyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val proxyJobRef = AtomicReference<Job?>(null)

    override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult {
        require(config is EngineConfig.ByeDpi) { "ByeDpiEngine требует EngineConfig.ByeDpi" }
        require(upstream is Upstream.None) {
            "ByeDpiEngine — terminal proxy (нет --proxy/-x в getopt_long). " +
                "Может быть только head of chain или standalone, не принимает upstream"
        }
        ByeDpiProxy.loadOnce()
        if (!ByeDpiProxy.libraryLoaded) {
            PersistentLoggers.error(TAG, "native lib не загружена — устройство не поддерживает или stripped APK")
            return StartResult.Failure(reason = "byedpi native library не загружена: ${ByeDpiProxy.loadError}")
        }
        Log.i(TAG, "start socksPort=${config.socksPort} args=${config.args}")
        val args = buildArgs(config)
        val proxyJob = proxyScope.launch {
            val code = runCatching { proxy.startProxy(args) }
                .onFailure { PersistentLoggers.error(TAG, "jniStartProxy threw: ${it.message}") }
                .getOrElse { -1 }
            if (code != 0) {
                PersistentLoggers.error(TAG, "jniStartProxy завершился с кодом $code")
            }
            activeSocksPort = 0
        }
        proxyJobRef.getAndSet(proxyJob)?.cancel()

        val readyAt = waitSocksReady(config.socksPort)
        return if (readyAt >= 0) {
            activeSocksPort = config.socksPort
            Log.i(TAG, "started socksPort=${config.socksPort} readyMs=$readyAt")
            StartResult.Success(socksPort = config.socksPort)
        } else {
            PersistentLoggers.error(TAG, "byedpi не вышел на порт ${config.socksPort} за ${READY_TIMEOUT_MS}ms")
            runCatching { proxy.stopProxy() }
                .onFailure { PersistentLoggers.warn(TAG, "jniStopProxy on failure: ${it.message}") }
            runCatching { proxy.forceClose() }
                .onFailure { PersistentLoggers.warn(TAG, "jniForceClose on failure: ${it.message}") }
            proxyJob.cancel()
            proxyJobRef.compareAndSet(proxyJob, null)
            StartResult.Failure(reason = "byedpi не открыл socks порт ${config.socksPort}")
        }
    }

    private suspend fun waitSocksReady(port: Int): Long {
        val started = System.currentTimeMillis()
        withTimeoutOrNull(readyTotalTimeoutMs) {
            while (true) {
                val ok = try {
                    socksProbe("127.0.0.1", port, readyProbeTimeoutMs)
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    false
                }
                if (ok) return@withTimeoutOrNull
                delay(READY_RETRY_MS)
            }
        } ?: return -1
        return System.currentTimeMillis() - started
    }

    override suspend fun stop() {
        Log.i(TAG, "stop")
        withContext(Dispatchers.IO) {
            runCatching { proxy.stopProxy() }
                .onFailure { PersistentLoggers.warn(TAG, "jniStopProxy исключение: ${it.message}") }
            val job = proxyJobRef.getAndSet(null)
            if (job != null) {
                val completed = withTimeoutOrNull(STOP_GRACE_MS) {
                    job.join()
                    true
                }
                if (completed == null) {
                    PersistentLoggers.warn(TAG, "proxyJob не завершился за ${STOP_GRACE_MS}ms — jniForceClose")
                    runCatching { proxy.forceClose() }
                        .onFailure { PersistentLoggers.warn(TAG, "jniForceClose исключение: ${it.message}") }
                    job.cancel()
                }
            }
            activeSocksPort = 0
        }
    }

    override suspend fun probe(): ProbeResult {
        val port = activeSocksPort
        if (port == 0) {
            PersistentLoggers.warn(TAG, "probe: движок не запущен")
            return ProbeResult.Failure(reason = "движок не запущен")
        }
        return try {
            val latency = socksProbe("127.0.0.1", port, 3_000)
            ProbeResult.Success(latencyMs = latency)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            PersistentLoggers.warn(TAG, "probe failed on port $port")
            ProbeResult.Failure(reason = "connection refused")
        }
    }

    override fun stats(): Flow<EngineStats> = _stats.asStateFlow()

    override fun preflight(): EnginePreflight = ByeDpiPreflight()

    override suspend fun ipProbeRoute(socksPort: Int): IpProbeRoute {
        val port = if (socksPort > 0) socksPort else activeSocksPort
        return if (port > 0) IpProbeRoute.Socks("127.0.0.1", port) else IpProbeRoute.Default
    }

    internal fun buildArgs(config: EngineConfig.ByeDpi): Array<String> {
        val extra =
            config.args.trim()
                .takeIf { it.isNotEmpty() }
                ?.split("\\s+".toRegex())
                .orEmpty()
        val hostsArgs = buildHostsArgs(config)
        return (listOf("ciadpi", "--ip", "127.0.0.1", "-p", config.socksPort.toString()) + extra + hostsArgs)
            .toTypedArray()
    }

    internal fun buildHostsArgs(config: EngineConfig.ByeDpi): List<String> {
        if (config.hostsMode == HostsMode.DISABLED) return emptyList()
        val cleaned = config.hosts.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) return emptyList()
        val hostStr = ":" + cleaned.joinToString(" ")
        return when (config.hostsMode) {
            HostsMode.BLACKLIST -> listOf("-H$hostStr", "-An")
            HostsMode.WHITELIST -> listOf("-H$hostStr")
            HostsMode.DISABLED -> emptyList()
        }
    }

    private companion object {
        const val TAG = "ByeDpiEngine"
        const val READY_TIMEOUT_MS = 5_000L
        const val READY_PROBE_TIMEOUT_MS = 500
        const val READY_RETRY_MS = 100L
        const val STOP_GRACE_MS = 1_500L
    }
}
