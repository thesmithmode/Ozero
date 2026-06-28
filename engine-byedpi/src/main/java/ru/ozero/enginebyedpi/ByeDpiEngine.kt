package ru.ozero.enginebyedpi

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.EnginePreflight
import ru.ozero.enginescore.EngineStats
import ru.ozero.enginescore.ExitNodeStrategy
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginescore.ProbeResult
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream
import ru.ozero.enginescore.probe.Socks5HandshakeProbe
import ru.ozero.enginescore.settings.HostsMode
import ru.ozero.enginescore.settings.SettingsModel
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class ByeDpiEngine(
    private val proxy: ByeDpiProxyContract = ByeDpiProxy(),
    private val socksProbe: suspend (String, Int, Int) -> Long = Socks5HandshakeProbe::probe,
    private val readyProbeTimeoutMs: Int = READY_PROBE_TIMEOUT_MS,
    private val readyTotalTimeoutMs: Long = READY_TIMEOUT_MS,
    private val portFreeChecker: (Int) -> Boolean = ::defaultPortFreeCheck,
    private val testDispatcherOverride: CoroutineDispatcher? = null,
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

    override fun stopTimeoutMs(): Long = BYEDPI_STOP_TIMEOUT_MS

    @Volatile private var activeSocksPort: Int = 0
    private val portCounter = AtomicInteger(0)
    private val proxyGeneration = AtomicInteger(0)
    private val _stats = MutableStateFlow(EngineStats())

    @OptIn(ExperimentalCoroutinesApi::class)
    private var proxyDispatcher: CoroutineDispatcher = newProxyDispatcher()
    private var proxyScope = CoroutineScope(SupervisorJob() + (testDispatcherOverride ?: proxyDispatcher))
    private val proxyJobRef = AtomicReference<Job?>(null)
    private val nativeMayBeWedged = AtomicBoolean(false)

    override fun buildManualConfig(settings: SettingsModel?): EngineConfig {
        val args = when {
            settings?.byedpiUseUiMode == true ->
                ByeDpiUiArgsBuilder.buildArgsOnly(settings.byedpiUiSettings).joinToString(" ")
            !settings?.byedpiWinningArgs.isNullOrBlank() ->
                settings.byedpiWinningArgs!!.trim() // CMD mode is verbatim; suffix injection changes strategy topology.
            else -> EngineConfig.ByeDpi().args
        }
        return EngineConfig.ByeDpi(
            args = args,
            socksPort = AUTO_ROTATE_PORT,
            hostsMode = settings?.hostsMode ?: HostsMode.DISABLED,
            hosts = settings?.hosts.orEmpty(),
        )
    }

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
        val resolvedPort = if (config.socksPort > 0) config.socksPort else nextRotatedPort()
        val resolvedConfig = if (config.socksPort > 0) config else config.copy(socksPort = resolvedPort)
        Log.i(TAG, "start socksPort=$resolvedPort args=${resolvedConfig.args}")
        val oldJob = proxyJobRef.getAndSet(null)
        val hadKnownWedge = nativeMayBeWedged.getAndSet(false)
        var rotateBeforeLaunch = hadKnownWedge
        if (oldJob != null) {
            if (oldJob.isActive) {
                PersistentLoggers.warn(TAG, "start: предыдущий прокси ещё активен — останавливаю")
                runCatching { proxy.stopProxy() }
                    .onFailure { PersistentLoggers.warn(TAG, "oldProxy jniStopProxy: ${it.message}") }
                withTimeoutOrNull(STOP_GRACE_MS) { oldJob.join() }
            }
            runCatching { proxy.forceClose() }
                .onFailure { PersistentLoggers.warn(TAG, "oldProxy jniForceClose: ${it.message}") }
            if (oldJob.isActive) {
                withTimeoutOrNull(STOP_GRACE_MS) { oldJob.join() }
                if (oldJob.isActive) {
                    rotateBeforeLaunch = true
                    oldJob.cancel()
                }
            }
            PersistentLoggers.debug(TAG, "start: barrier pre-drain oldJob.isActive=${oldJob.isActive}")
        }

        if (rotateBeforeLaunch) {
            rotateProxyLane("start: previous proxy lane wedged")
        } else if (oldJob != null) {
            drainOrRotateProxyLane()
        }
        val args = buildArgs(resolvedConfig)
        PersistentLoggers.debug(
            TAG,
            "jniStartProxy argv=[${args.joinToString(" ")}] (native префиксует argv[0]=\"byedpi\")",
        )
        val generation = proxyGeneration.incrementAndGet()
        val proxyJob = proxyScope.launch {
            PersistentLoggers.debug(TAG, "launch entered port=$resolvedPort")
            val code = startProxySafely(args)
            PersistentLoggers.debug(TAG, "startProxy returned code=$code port=$resolvedPort")
            when {
                code == 0 -> Unit
                code == JNI_GUARD_BUSY -> PersistentLoggers.warn(
                    TAG,
                    "jniStartProxy guard busy — старая main() ещё держит CAS; queue serialization",
                )
                else -> PersistentLoggers.error(TAG, "jniStartProxy завершился с кодом $code")
            }
            if (proxyGeneration.get() == generation) activeSocksPort = 0
        }
        proxyJobRef.set(proxyJob)

        val readyAt = waitSocksReady(resolvedPort, proxyJob)
        return if (readyAt >= 0) {
            activeSocksPort = resolvedPort
            Log.i(TAG, "started socksPort=$resolvedPort readyMs=$readyAt")
            StartResult.Success(socksPort = resolvedPort)
        } else {
            PersistentLoggers.error(TAG, "byedpi не вышел на порт $resolvedPort за ${READY_TIMEOUT_MS}ms")
            if (proxyJob.isActive) {
                runCatching { proxy.stopProxy() }
                    .onFailure { PersistentLoggers.warn(TAG, "jniStopProxy on failure: ${it.message}") }
                withTimeoutOrNull(STOP_GRACE_MS) { proxyJob.join() }
            }
            runCatching { proxy.forceClose() }
                .onFailure { PersistentLoggers.warn(TAG, "jniForceClose on failure: ${it.message}") }
            if (proxyJob.isActive) {
                nativeMayBeWedged.set(true)
                proxyJob.cancel()
                rotateProxyLane("start failure: proxyJob did not finish")
            }
            proxyJobRef.compareAndSet(proxyJob, null)
            StartResult.Failure(reason = "byedpi не открыл socks порт $resolvedPort")
        }
    }

    private fun nextRotatedPort(): Int {
        repeat(PORT_ROTATION_RANGE) {
            val candidate = PORT_ROTATION_BASE + (portCounter.getAndIncrement() and (PORT_ROTATION_RANGE - 1))
            if (portFreeChecker(candidate)) return candidate
        }
        return ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
    }

    private suspend fun drainOrRotateProxyLane() {
        val drained = withTimeoutOrNull(STOP_GRACE_MS) {
            withContext(proxyDispatcher) {}
            true
        }
        if (drained != null) return
        PersistentLoggers.warn(TAG, "start: dispatcher drain timeout — rotating ByeDPI proxy lane")
        rotateProxyLane("start: dispatcher drain timeout")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun newProxyDispatcher(): CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    private fun rotateProxyLane(reason: String) {
        proxyDispatcher = newProxyDispatcher()
        proxyScope = CoroutineScope(SupervisorJob() + (testDispatcherOverride ?: proxyDispatcher))
        nativeMayBeWedged.set(false)
        PersistentLoggers.warn(TAG, "$reason — new ByeDPI proxy lane")
    }

    private fun startProxySafely(args: Array<String>): Int =
        safeJniCall(fallback = -1, tag = "jniStartProxy threw") {
            proxy.startProxy(args)
        }

    private inline fun <T> safeJniCall(fallback: T, tag: String, block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        PersistentLoggers.error(TAG, "$tag: ${t.message}")
        fallback
    }

    private suspend fun waitSocksReady(port: Int, proxyJob: Job): Long {
        val started = System.currentTimeMillis()
        var probeSuccess = false
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
                if (ok) {
                    probeSuccess = true
                    return@withTimeoutOrNull
                }
                if (!proxyJob.isActive) return@withTimeoutOrNull
                delay(READY_RETRY_MS)
            }
        }
        return if (probeSuccess) System.currentTimeMillis() - started else -1
    }

    override suspend fun stop() {
        Log.i(TAG, "stop")
        withContext(Dispatchers.IO) {
            runCatching { proxy.stopProxy() }
                .onFailure { PersistentLoggers.warn(TAG, "jniStopProxy исключение: ${it.message}") }
            // forceClose before join unblocks native READ_WAIT; coroutine cancel cannot stop JNI main().
            runCatching { proxy.forceClose() }
                .onFailure { PersistentLoggers.warn(TAG, "jniForceClose исключение: ${it.message}") }
            val job = proxyJobRef.getAndSet(null)
            proxyGeneration.incrementAndGet()
            if (job != null) {
                val completed = withTimeoutOrNull(STOP_GRACE_MS) {
                    job.join()
                    true
                }
                if (completed == null) {
                    PersistentLoggers.warn(
                        TAG,
                        "proxyJob не завершился за ${STOP_GRACE_MS}ms — cancel and defer lane drain",
                    )
                    nativeMayBeWedged.set(true)
                    job.cancel()
                    rotateProxyLane("stop: proxyJob did not finish within ${STOP_GRACE_MS}ms")
                } else {
                    nativeMayBeWedged.set(false)
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

    override suspend fun exitNodeStrategy(socksPort: Int): ExitNodeStrategy {
        val port = if (socksPort > 0) socksPort else activeSocksPort
        return if (port > 0) {
            ExitNodeStrategy.DirectHttp
        } else {
            ExitNodeStrategy.Unavailable("ByeDPI SOCKS endpoint unavailable")
        }
    }

    // Native inserts argv[0]="byedpi"; Kotlin prepending program-name makes getopt drop --ip.
    internal fun buildArgs(config: EngineConfig.ByeDpi): Array<String> {
        val extra =
            config.args.trim()
                .takeIf { it.isNotEmpty() }
                ?.split("\\s+".toRegex())
                .orEmpty()
        val hostsArgs = buildHostsArgs(config)
        return (listOf("--ip", "127.0.0.1", "-p", config.socksPort.toString()) + extra + hostsArgs)
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

    companion object {
        const val TAG = "ByeDpiEngine"
        const val READY_TIMEOUT_MS = 5_000L
        const val JNI_GUARD_BUSY = -2
        const val READY_PROBE_TIMEOUT_MS = 500
        const val READY_RETRY_MS = 100L
        const val STOP_GRACE_MS = 1_500L

        const val BYEDPI_STOP_TIMEOUT_MS = 2_500L
        const val AUTO_ROTATE_PORT = 0
        const val PORT_ROTATION_BASE = 49_152
        const val PORT_ROTATION_RANGE = 256
    }
}

private fun defaultPortFreeCheck(port: Int): Boolean = runCatching {
    ServerSocket(port, 1, InetAddress.getLoopbackAddress()).use { }
}.isSuccess
