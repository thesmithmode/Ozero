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
import androidx.datastore.preferences.core.longPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.ozero.enginescore.VpnSocketProtectorHolder
import ru.ozero.enginesingbox.ISingboxEngineProcess
import ru.ozero.enginesingbox.ISingboxProtector
import ru.ozero.enginesingbox.SingboxEngine
import ru.ozero.enginesingbox.SingboxHttp204RoutedProbe
import ru.ozero.enginesingbox.SingboxPrefs
import ru.ozero.enginesingbox.SingboxRoutedProbe
import ru.ozero.singboxconfig.ConfigBuilder
import ru.ozero.singboxfmt.AbstractBean
import ru.ozero.singboxfmt.KryoSerializer
import ru.ozero.singboxroom.dao.ProxyProfileDao
import ru.ozero.singboxroom.entity.ProxyProfile
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SingboxProbeService internal constructor(
    private val profileDao: ProxyProfileDao,
    @SingboxPrefs private val dataStore: DataStore<Preferences>,
    private val profileProbe: SingboxProfileProbe,
) {

    @Inject
    constructor(
        profileDao: ProxyProfileDao,
        @SingboxPrefs dataStore: DataStore<Preferences>,
        @ApplicationContext context: Context,
    ) : this(
        profileDao = profileDao,
        dataStore = dataStore,
        profileProbe = SingboxServiceProfileProbe(context),
    )

    suspend fun probeAndAutoSelect(
        profiles: List<ProxyProfile>,
        scope: CoroutineScope,
    ) {
        val results = profiles.mapNotNull { profile ->
            val bean = runCatching { KryoSerializer.deserialize<AbstractBean>(profile.beanBlob) }.getOrNull()
            if (bean == null || !ConfigBuilder.isSupportedBean(bean)) {
                profileDao.updateLatency(profile.id, LATENCY_FAILED)
                null
            } else {
                scope.async(Dispatchers.IO) {
                    val latency = probeLatencyMs(bean)
                    profileDao.updateLatency(profile.id, latency)
                    profile to latency
                }
            }
        }.awaitAll()
        val best = results.filter { it.second >= 0 }.minByOrNull { it.second }?.first ?: return
        dataStore.edit { prefs ->
            if (prefs[SELECTED_PROFILE_KEY] == SingboxEngine.SELECTED_AUTO) return@edit
            prefs[SELECTED_PROFILE_KEY] = best.id
            prefs[BEAN_KEY] = best.beanBlob
        }
    }

    private suspend fun probeLatencyMs(bean: AbstractBean): Int = profileProbe.probeLatencyMs(bean)

    companion object {
        val BEAN_KEY = byteArrayPreferencesKey("singbox_vless_bean")
        val SELECTED_PROFILE_KEY = longPreferencesKey("singbox_selected_profile_id")
        const val LATENCY_UNTESTED = -1
        const val LATENCY_FAILED = -2
    }
}

internal fun interface SingboxProfileProbe {
    suspend fun probeLatencyMs(bean: AbstractBean): Int
}

private class SingboxServiceProfileProbe(
    private val context: Context,
    private val routedProbe: SingboxRoutedProbe = SingboxHttp204RoutedProbe(),
) : SingboxProfileProbe {
    private val mutex = Mutex()

    private val localProtector = object : ISingboxProtector.Stub() {
        override fun protect(fd: Int): Boolean = VpnSocketProtectorHolder.protect(fd)
    }

    override suspend fun probeLatencyMs(bean: AbstractBean): Int = mutex.withLock {
        withContext(Dispatchers.IO) {
            val port = allocateProbePort()
            val config = runCatching { ConfigBuilder.buildChainConfig(bean, port, upstream = null) }
                .getOrElse { return@withContext SingboxProbeService.LATENCY_FAILED }
            val binding = bindProcess()
                ?: return@withContext SingboxProbeService.LATENCY_FAILED
            var shouldStop = false
            try {
                val process = binding.process
                val alreadyRunning = runCatching { process.runtimeRunning() }.getOrDefault(false)
                if (alreadyRunning) return@withContext SingboxProbeService.LATENCY_FAILED
                shouldStop = true
                runCatching { process.startProxyMode(config, localProtector) }
                    .getOrElse { return@withContext SingboxProbeService.LATENCY_FAILED }
                delay(PROBE_START_DELAY_MS)
                val running = runCatching { process.runtimeRunning() }.getOrDefault(false)
                if (!running) return@withContext SingboxProbeService.LATENCY_FAILED
                val latency = routedProbe.probeLatencyMs(port)
                if (latency >= 0) {
                    latency.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                } else {
                    SingboxProbeService.LATENCY_FAILED
                }
            } finally {
                if (shouldStop) runCatching { binding.process.stopAndWait(REMOTE_STOP_TIMEOUT_MS) }
                runCatching { context.unbindService(binding.connection) }
            }
        }
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
        val intent = Intent().setComponent(ComponentName(context, "ru.ozero.singboxprocess.SingboxEngineService"))
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
        const val REMOTE_STOP_TIMEOUT_MS = 3_000L
        const val BIND_TIMEOUT_MS = 5_000L
    }
}
