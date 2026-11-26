package ru.ozero.enginegfvpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import ru.ozero.enginescore.EngineCapabilities
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.EngineStats
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginescore.ProbeResult
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.TunAttachResult
import ru.ozero.enginescore.TunFdAcceptor
import ru.ozero.enginescore.TunSpec
import ru.ozero.enginescore.Upstream
import ru.ozero.enginescore.settings.SettingsModel
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Standalone authenticated TUN engine. Never exposes credentials or a chainable proxy. */
class GfVpnEngine(context: Context) : EnginePlugin, TunFdAcceptor {
    private val app = context.applicationContext
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val state = MutableStateFlow(EngineStats())
    private val connectivity = app.getSystemService(ConnectivityManager::class.java)

    @Volatile
    private var runtime: GfVpnRuntime? = null

    private var callback: ConnectivityManager.NetworkCallback? = null
    private var attached = false

    override val id = EngineId.GFVPN
    override val capabilities = EngineCapabilities(
        supportsTcp = true,
        supportsUdp = false,
        supportsDoH = false,
        localOnly = false,
        requiresServer = false,
        supportsUpstreamSocks = false,
        providesLocalSocks = false,
        providesLocalSocksWithoutUpstream = false,
        requiresRoutedProbe = true,
    )

    override fun buildManualConfig(settings: SettingsModel?): EngineConfig? =
        EngineConfig.GfVpn.takeIf { GfVpnImport.isInstalled(app) }

    override fun buildProxyConfig(settings: SettingsModel?): EngineConfig? = null

    override suspend fun tunSpec(): TunSpec = TunSpec(
        sessionName = "Ozero gfVPN",
        mtu = 8500,
        blocking = false,
        ipv4Address = "10.10.10.10",
        ipv4PrefixLength = 32,
        dnsServers = listOf("9.9.9.9"),
        allowFamilyV6 = false,
    )

    override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult = mutex.withLock {
        if (config != EngineConfig.GfVpn || upstream != Upstream.None) {
            return@withLock StartResult.Failure("gfVPN requires standalone VPN mode")
        }
        if (!GfVpnImport.isInstalled(app)) {
            return@withLock StartResult.Failure("Import the local gfVPN APK in engine settings")
        }
        reset()
        val next = GfVpnRuntime(app)
        runtime = next
        try {
            val prefs = app.getSharedPreferences("gfvpn-local", Context.MODE_PRIVATE)
            val identifier = prefs.getString("client-id", null) ?: UUID.randomUUID().toString().also {
                prefs.edit().putString("client-id", it).apply()
            }
            next.start("RU", identifier).awaitResult()
            StartResult.Success(0)
        } catch (cancelled: CancellationException) {
            reset()
            throw cancelled
        } catch (error: Exception) {
            reset()
            StartResult.Failure("gfVPN startup failed", error)
        }
    }

    override suspend fun attachTun(tunFd: Int): TunAttachResult = mutex.withLock {
        val current = runtime ?: return@withLock TunAttachResult.Failure("gfVPN not started")
        if (attached) return@withLock TunAttachResult.Failure("TUN already attached")
        try {
            ParcelFileDescriptor.fromFd(tunFd).use { current.attachTun(it).awaitResult() }
            // The coordinator retains ownership on failure, transfers it only on success.
            // Attach is already successful; the original fd must not be returned to the caller.
            runCatching { ParcelFileDescriptor.adoptFd(tunFd).close() }
            attached = true
            runCatching { observeNetworks(current) }
            state.value = EngineStats(connectedSince = System.currentTimeMillis())
            TunAttachResult.Success
        } catch (cancelled: CancellationException) {
            reset()
            throw cancelled
        } catch (error: Exception) {
            reset()
            PersistentLoggers.warn("GfVpnEngine", "TUN attachment failed: ${error.javaClass.simpleName}")
            TunAttachResult.Failure("gfVPN TUN attachment failed")
        }
    }

    override suspend fun probe(): ProbeResult = mutex.withLock {
        val current = runtime
        if (current == null || !attached) return@withLock ProbeResult.Failure("gfVPN not attached")
        try {
            ProbeResult.Success(current.probe().awaitResult())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            ProbeResult.Failure("gfVPN authenticated route unavailable", error)
        }
    }

    override suspend fun awaitReady(): EnginePlugin.ReadyResult =
        if (probe() is ProbeResult.Success) {
            EnginePlugin.ReadyResult.Ready
        } else {
            EnginePlugin.ReadyResult.Timeout("gfVPN route unavailable")
        }

    override fun stats() = state

    override suspend fun stop() {
        // A blocked probe/network callback cannot prevent the remote process from being aborted.
        val owner = runtime
        if (!mutex.tryLock()) {
            owner?.close()
            mutex.withLock { if (runtime === owner) reset() }
            return
        }
        try {
            if (runtime === owner && owner != null) withTimeoutOrNull(1500) { owner.stop().awaitResult() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Process teardown is the final fallback.
        } finally {
            if (runtime === owner) reset()
            mutex.unlock()
        }
    }

    private fun observeNetworks(owner: GfVpnRuntime) {
        val listener = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = notifyChanged(owner)
            override fun onLost(network: Network) = notifyChanged(owner)
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        connectivity?.registerNetworkCallback(request, listener)
        callback = listener
    }

    private fun notifyChanged(owner: GfVpnRuntime) {
        scope.launch {
            mutex.withLock {
                if (runtime === owner && attached) {
                    try {
                        owner.networkChanged().awaitResult()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Routed watchdog will fail closed if the SDK cannot recover.
                    }
                }
            }
        }
    }

    private fun reset() {
        callback?.let { runCatching { connectivity?.unregisterNetworkCallback(it) } }
        callback = null
        val previous = runtime
        runtime = null
        attached = false
        state.value = EngineStats()
        previous?.close()
    }

    private suspend fun <T> CompletableFuture<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        whenComplete { value, error ->
            if (error != null) {
                continuation.resumeWithException(error)
            } else {
                continuation.resume(value)
            }
        }
        continuation.invokeOnCancellation { cancel(true) }
    }
}
