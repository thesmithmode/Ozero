package ru.ozero.app.ui.settings.engines.singbox

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.VpnSocketProtectorHolder
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.enginescore.settings.SettingsRepository
import ru.ozero.enginesingbox.ISingboxEngineProcess
import ru.ozero.enginesingbox.ISingboxProtector
import ru.ozero.enginesingbox.SingboxEngine
import ru.ozero.enginesingbox.SingboxHttp204RoutedProbe
import ru.ozero.enginesingbox.SingboxPrefs
import ru.ozero.singboxconfig.ConfigBuilder
import ru.ozero.singboxfmt.AbstractBean
import ru.ozero.singboxfmt.KryoSerializer
import ru.ozero.singboxroom.dao.ProxyProfileDao
import ru.ozero.singboxroom.entity.ProxyProfile
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SingboxProbeService internal constructor(
    private val profileDao: ProxyProfileDao,
    @SingboxPrefs private val dataStore: DataStore<Preferences>,
    private val profileProbe: SingboxProfileProbe,
    private val settingsRepository: SettingsRepository? = null,
    private val probeDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    @Inject
    constructor(
        profileDao: ProxyProfileDao,
        @SingboxPrefs dataStore: DataStore<Preferences>,
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository,
    ) : this(
        profileDao = profileDao,
        dataStore = dataStore,
        profileProbe = SingboxServiceProfileProbe(context),
        settingsRepository = settingsRepository,
    )

    suspend fun probeAndAutoSelect(
        profiles: List<ProxyProfile>,
        onProfileTestingChanged: (Long, Boolean) -> Unit = { _, _ -> },
        updateManualSelection: Boolean = true,
    ) {
        val prefs = dataStore.data.first()
        val settings = settingsRepository?.settings?.first() ?: SettingsModel.DEFAULT
        val probeSettings = SingboxProfileProbeSettings(
            timeoutMs = prefs[PROBE_TIMEOUT_MS_KEY].normalizedSingboxProbeTimeoutMs(),
            dnsServers = prefs[SINGBOX_DNS_SERVERS_KEY]?.sorted()?.ifEmpty { null }
                ?: EngineConfig.Singbox.DEFAULT_DNS_SERVERS,
            ipv6Enabled = settings.ipv6Enabled,
        )
        val probeCandidates = profiles.mapNotNull { profile ->
            val bean = runCatching { KryoSerializer.deserialize<AbstractBean>(profile.beanBlob) }.getOrNull()
            if (bean == null || !ConfigBuilder.isSupportedBean(bean) || !bean.hasRoutableServerAddress()) {
                profileDao.updateProbeResult(profile.id, LATENCY_FAILED, PROBE_ERROR_UNSUPPORTED)
                null
            } else {
                profile to bean
            }
        }
        val results = ConcurrentLinkedQueue<ProbeResult>()
        val nextIndex = AtomicInteger(0)
        val workerCount = minOf(MAX_CONCURRENT_PROFILE_PROBES, probeCandidates.size)
        coroutineScope {
            List(workerCount) {
                async(probeDispatcher) {
                    while (true) {
                        coroutineContext.ensureActive()
                        val index = nextIndex.getAndIncrement()
                        if (index >= probeCandidates.size) break
                        val (profile, bean) = probeCandidates[index]
                        onProfileTestingChanged(profile.id, true)
                        try {
                            val latency = probeLatencyMs(bean, probeSettings)
                            if (latency == LATENCY_SKIPPED_ACTIVE_RUNTIME) continue
                            val storedLatency = if (latency >= 0) latency else LATENCY_FAILED
                            val probeError = if (storedLatency >= 0) null else PROBE_ERROR_FAILED
                            profileDao.updateProbeResult(profile.id, storedLatency, probeError)
                            results.add(ProbeResult(index, profile, storedLatency))
                        } finally {
                            onProfileTestingChanged(profile.id, false)
                        }
                    }
                }
            }.awaitAll()
        }
        val best = results
            .filter { it.latency >= 0 }
            .minWithOrNull(
                compareBy<ProbeResult> { it.latency }
                    .thenBy { it.index },
            )
            ?.profile
            ?: return
        if (!updateManualSelection) return
        dataStore.edit { prefs ->
            if (prefs[SELECTED_PROFILE_KEY] == SingboxEngine.SELECTED_AUTO) return@edit
            prefs[SELECTED_PROFILE_KEY] = best.id
            prefs[BEAN_KEY] = best.beanBlob
        }
    }

    private suspend fun probeLatencyMs(bean: AbstractBean, settings: SingboxProfileProbeSettings): Int =
        profileProbe.probeLatencyMs(bean, settings)

    private suspend fun ProxyProfileDao.updateProbeResult(id: Long, latency: Int, error: String?) {
        updateProbeResult(id, latency, error, System.currentTimeMillis())
    }

    private data class ProbeResult(
        val index: Int,
        val profile: ProxyProfile,
        val latency: Int,
    )

    companion object {
        val BEAN_KEY = byteArrayPreferencesKey("singbox_vless_bean")
        val SELECTED_PROFILE_KEY = longPreferencesKey("singbox_selected_profile_id")
        const val LATENCY_UNTESTED = -1
        const val LATENCY_FAILED = -2
        const val MAX_CONCURRENT_PROFILE_PROBES = 1
        const val PROBE_ERROR_UNSUPPORTED = "unsupported"
        const val PROBE_ERROR_FAILED = "probe failed"
        const val DEFAULT_PROBE_TIMEOUT_MS = 3_000
        const val MIN_PROBE_TIMEOUT_MS = 1_000
        const val MAX_PROBE_TIMEOUT_MS = 30_000
        val PROBE_TIMEOUT_MS_KEY = intPreferencesKey("singbox_probe_timeout_ms")
        val SINGBOX_DNS_SERVERS_KEY = stringSetPreferencesKey("singbox_dns_servers")
    }
}

internal fun interface SingboxProfileProbe {
    suspend fun probeLatencyMs(bean: AbstractBean, settings: SingboxProfileProbeSettings): Int
}

internal data class SingboxProfileProbeSettings(
    val timeoutMs: Int,
    val dnsServers: List<String>,
    val ipv6Enabled: Boolean,
)

internal const val LATENCY_SKIPPED_ACTIVE_RUNTIME = Int.MIN_VALUE
internal fun Int?.normalizedSingboxProbeTimeoutMs(): Int =
    (this ?: SingboxProbeService.DEFAULT_PROBE_TIMEOUT_MS).coerceIn(
        SingboxProbeService.MIN_PROBE_TIMEOUT_MS,
        SingboxProbeService.MAX_PROBE_TIMEOUT_MS,
    )

private class SingboxServiceProfileProbe(
    private val context: Context,
) : SingboxProfileProbe {
    private val mutex = Mutex()

    private val localProtector = object : ISingboxProtector.Stub() {
        override fun protect(fd: Int): Boolean = VpnSocketProtectorHolder.protect(fd)
    }

    override suspend fun probeLatencyMs(
        bean: AbstractBean,
        settings: SingboxProfileProbeSettings,
    ): Int = mutex.withLock {
        withContext(Dispatchers.IO) {
            coroutineContext.ensureActive()
            val port = allocateProbePort()
            if (!bean.hasRoutableServerAddress()) return@withContext SingboxProbeService.LATENCY_FAILED
            val config = runCatching {
                ConfigBuilder.buildChainConfig(
                    bean = bean,
                    socksPort = port,
                    upstream = null,
                    dnsServers = settings.dnsServers,
                    ipv6Enabled = settings.ipv6Enabled,
                )
            }
                .getOrElse { return@withContext SingboxProbeService.LATENCY_FAILED }
            val binding = bindProcess()
                ?: return@withContext SingboxProbeService.LATENCY_FAILED
            var shouldStop = false
            try {
                val process = binding.process
                val alreadyRunning = runCatching { process.runtimeRunning() }.getOrDefault(false)
                if (alreadyRunning) return@withContext LATENCY_SKIPPED_ACTIVE_RUNTIME
                coroutineContext.ensureActive()
                shouldStop = true
                runCatching { process.startProxyMode(config, localProtector) }
                    .getOrElse { return@withContext SingboxProbeService.LATENCY_FAILED }
                coroutineContext.ensureActive()
                delay(PROBE_START_DELAY_MS)
                val running = runCatching { process.runtimeRunning() }.getOrDefault(false)
                if (!running) return@withContext SingboxProbeService.LATENCY_FAILED
                coroutineContext.ensureActive()
                val latency = probeRoutedWithRetry(port, settings.timeoutMs)
                if (latency >= 0) {
                    latency.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                } else {
                    SingboxProbeService.LATENCY_FAILED
                }
            } finally {
                if (shouldStop) {
                    withContext(NonCancellable) {
                        runCatching { binding.process.stopAndWait(REMOTE_STOP_TIMEOUT_MS) }
                    }
                }
                runCatching { context.unbindService(binding.connection) }
            }
        }
    }

    private suspend fun probeRoutedWithRetry(port: Int, timeoutMs: Int): Long {
        val probe = SingboxHttp204RoutedProbe(timeoutMs = timeoutMs.normalizedSingboxProbeTimeoutMs())
        repeat(PROBE_ATTEMPTS) { attempt ->
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val latency = probe.probeLatencyMs(port)
            if (latency >= 0) return latency
            if (attempt < PROBE_ATTEMPTS - 1) delay(PROBE_RETRY_DELAY_MS)
        }
        return SingboxHttp204RoutedProbe.LATENCY_FAILED
    }

    private fun bindProcess(): Binding? {
        val latch = CountDownLatch(1)
        var process: ISingboxEngineProcess? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                process = ISingboxEngineProcess.Stub.asInterface(service)
                latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) {
                process = null
            }
        }
        val component = ComponentName(context, "ru.ozero.singboxprocess.SingboxEngineService")
        val intent = Intent().apply { this.component = component }
        val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT)
        if (!bound) {
            runCatching { context.unbindService(connection) }
            return null
        }
        if (!latch.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            runCatching { context.unbindService(connection) }
            return null
        }
        return process?.let { Binding(it, connection) } ?: run {
            runCatching { context.unbindService(connection) }
            null
        }
    }

    private fun allocateProbePort(): Int =
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }

    private data class Binding(
        val process: ISingboxEngineProcess,
        val connection: ServiceConnection,
    )

    private companion object {
        const val PROBE_START_DELAY_MS = 150L
        const val PROBE_RETRY_DELAY_MS = 250L
        const val PROBE_ATTEMPTS = 3
        const val REMOTE_STOP_TIMEOUT_MS = 3_000L
        const val BIND_TIMEOUT_MS = 5_000L
    }
}

private fun AbstractBean.hasRoutableServerAddress(): Boolean {
    val host = serverAddress.trim().trim('[', ']').lowercase()
    return host.isNotEmpty() &&
        host != "localhost" &&
        host != "0.0.0.0" &&
        host != "::" &&
        host != "::0" &&
        host != "::1" &&
        !host.startsWith("127.")
}
