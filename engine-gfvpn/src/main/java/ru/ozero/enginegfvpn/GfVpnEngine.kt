package ru.ozero.enginegfvpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import ru.ozero.enginescore.EngineCapabilities
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.EngineStats
import ru.ozero.enginescore.ExitNodeStrategy
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginescore.ProbeResult
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.TunAttachResult
import ru.ozero.enginescore.TunFdAcceptor
import ru.ozero.enginescore.TunSpec
import ru.ozero.enginescore.Upstream
import ru.ozero.enginescore.settings.GfVpnOptions
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.enginescore.settings.SettingsRepository
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Standalone authenticated TUN engine. Never exposes credentials or a chainable proxy. */
class GfVpnEngine(
    context: Context,
    private val settingsRepository: SettingsRepository? = null,
) : EnginePlugin, TunFdAcceptor {
    private val app = context.applicationContext
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val state = MutableStateFlow(EngineStats())
    private val connectivity = app.getSystemService(ConnectivityManager::class.java)

    @Volatile
    private var runtime: GfVpnRuntime? = null

    private var callback: ConnectivityManager.NetworkCallback? = null
    private var defaultCallback: ConnectivityManager.NetworkCallback? = null
    private var networkChanges: GfVpnNetworkChanges? = null
    private var attached = false
    private var sdkSession: GfVpnRuntime.Session? = null

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
        EngineConfig.GfVpn.takeIf { GfVpnBundledSdk.isAvailable(app) }

    override fun buildProxyConfig(settings: SettingsModel?): EngineConfig? = null

    override suspend fun tunSpec(): TunSpec = TunSpec(
        sessionName = "Ozero ${id.displayName}",
        mtu = 8500,
        blocking = false,
        ipv4Address = "198.18.0.1",
        ipv4PrefixLength = 32,
        dnsServers = listOf(GfVpnTun.dnsAddress(Build.VERSION.SDK_INT)),
        allowFamilyV6 = false,
    )

    override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult = mutex.withLock {
        if (config != EngineConfig.GfVpn || upstream != Upstream.None) {
            return@withLock StartResult.Failure("gfVPN requires standalone VPN mode")
        }
        if (!GfVpnBundledSdk.isAvailable(app)) {
            return@withLock StartResult.Failure("Bundled gfVPN runtime unavailable on this build or device")
        }
        reset()
        val next = GfVpnRuntime(app)
        runtime = next
        try {
            val prefs = app.getSharedPreferences("gfvpn-local", Context.MODE_PRIVATE)
            val identifier = prefs.getString("client-id", null) ?: UUID.randomUUID().toString().also {
                prefs.edit().putString("client-id", it).apply()
            }
            val options = settingsRepository?.let { repository ->
                withTimeoutOrNull(2000L) { repository.settings.first().gfVpnOptions }
                    ?: throw IOException("gfVPN settings unavailable")
            } ?: GfVpnOptions.DEFAULT
            sdkSession = next.start("RU", identifier, options).awaitResult()
            StartResult.Success(0)
        } catch (cancelled: CancellationException) {
            reset()
            throw cancelled
        } catch (error: Exception) {
            reset()
            val detail = diagnosticDetail(error)
            PersistentLoggers.warn("GfVpnEngine", "Startup failed: $detail")
            StartResult.Failure("gfVPN startup failed: $detail")
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
            val detail = diagnosticDetail(error)
            PersistentLoggers.warn("GfVpnEngine", "TUN attachment failed: $detail")
            TunAttachResult.Failure("gfVPN TUN attachment failed")
        }
    }

    override suspend fun probe(): ProbeResult {
        val owner = runtime ?: return ProbeResult.Failure("gfVPN not started")
        // Do not hold the lifecycle lock while a manual challenge is displayed.
        if (!GfVpnCaptchaState.awaitResolution(owner)) {
            return ProbeResult.Failure("gfVPN manual verification timed out")
        }
        return mutex.withLock {
            if (runtime !== owner || !attached) return@withLock ProbeResult.Failure("gfVPN session changed")
            try {
                val result = owner.probeWithStats().awaitResult()
                state.value = state.value.copy(bytesIn = result.bytesIn, bytesOut = result.bytesOut)
                ProbeResult.Success(result.latencyMs)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                ProbeResult.Failure("gfVPN authenticated route unavailable", error)
            }
        }
    }

    override suspend fun awaitReady(): EnginePlugin.ReadyResult {
        val owner = runtime ?: return EnginePlugin.ReadyResult.Timeout("gfVPN not started")
        val ready = withTimeoutOrNull(200_000L) {
            repeat(4) { attempt ->
                if (runtime !== owner) return@withTimeoutOrNull false
                if (probe() is ProbeResult.Success) return@withTimeoutOrNull true
                if (attempt < 3) delay(500L)
            }
            false
        } == true
        return if (ready) {
            EnginePlugin.ReadyResult.Ready
        } else {
            EnginePlugin.ReadyResult.Timeout("gfVPN route unavailable")
        }
    }

    override suspend fun exitNodeStrategy(socksPort: Int): ExitNodeStrategy {
        val owner = mutex.withLock {
            if (!attached || socksPort != 0) null else sdkSession
        } ?: return ExitNodeStrategy.Unavailable("gfVPN session unavailable")
        val info = GfVpnExitInfo.fetch(owner).getOrElse {
            if (it is CancellationException) throw it
            PersistentLoggers.warn(
                "GfVpnEngine",
                "exit IP lookup failed through authenticated SOCKS",
                it,
            )
            return ExitNodeStrategy.Unavailable("gfVPN exit IP lookup failed")
        }
        return mutex.withLock {
            if (!attached || sdkSession !== owner) {
                ExitNodeStrategy.Unavailable("gfVPN session changed")
            } else {
                ExitNodeStrategy.ProviderLabel(info.country ?: id.displayName, info.ip, info.countryCode)
            }
        }
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
        val manager = connectivity ?: return
        val changes = GfVpnNetworkChanges(scope) { notifyChanged(owner) }
        val listener = object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                changes.offer()
            }

            override fun onLost(network: Network) {
                changes.offer()
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        try {
            manager.registerNetworkCallback(request, listener)
            callback = listener
        } catch (failure: Exception) {
            PersistentLoggers.warn("GfVpnEngine", "Network observation unavailable: ${failure.javaClass.simpleName}")
        }
        val defaultListener = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                changes.offer()
            }

            override fun onLost(network: Network) {
                changes.offer()
            }
        }
        try {
            manager.registerDefaultNetworkCallback(defaultListener)
            defaultCallback = defaultListener
        } catch (failure: Exception) {
            PersistentLoggers.warn(
                "GfVpnEngine",
                "Default network observation unavailable: ${failure.javaClass.simpleName}",
            )
        }
        if (callback == null && defaultCallback == null) {
            changes.close()
        } else {
            networkChanges = changes
        }
    }

    private suspend fun notifyChanged(owner: GfVpnRuntime) = mutex.withLock {
        if (runtime === owner && attached) {
            try {
                owner.networkChanged().awaitResult()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                PersistentLoggers.warn("GfVpnEngine", "Network change failed: ${error.javaClass.simpleName}")
            }
        }
    }

    private fun diagnosticDetail(error: Exception): String = (error as? IOException)?.message
        ?.takeIf { it.startsWith("gfVPN ") || it.startsWith("gfVPN: ") }
        ?: error.javaClass.simpleName

    private fun reset() {
        networkChanges?.close()
        networkChanges = null
        callback?.let { runCatching { connectivity?.unregisterNetworkCallback(it) } }
        callback = null
        defaultCallback?.let { runCatching { connectivity?.unregisterNetworkCallback(it) } }
        defaultCallback = null
        val previous = runtime
        runtime = null
        sdkSession = null
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
