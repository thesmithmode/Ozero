package ru.ozero.app.ui.settings.engines.singbox

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.ParcelFileDescriptor
import android.os.IBinder
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.LogSanitizer
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginescore.VpnSocketProtectorHolder
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.enginescore.settings.SettingsRepository
import ru.ozero.enginesingbox.ISingboxEngineProcess
import ru.ozero.enginesingbox.ISingboxProtector
import ru.ozero.enginesingbox.RoutedProbeCancellation
import ru.ozero.enginesingbox.SingboxHttp204RoutedProbe
import ru.ozero.enginesingbox.SingboxPrefs
import ru.ozero.singboxconfig.ConfigBuilder
import ru.ozero.singboxconfig.PersistedProfileRecovery
import ru.ozero.singboxconfig.PersistedProtocol
import ru.ozero.singboxconfig.RecoveryResult
import ru.ozero.singboxfmt.AbstractBean
import ru.ozero.singboxroom.dao.ProxyProfileDao
import ru.ozero.singboxroom.entity.ProxyProfile
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

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
        val selectedProfileAtStart = prefs[SELECTED_PROFILE_KEY]
        val settings = settingsRepository?.settings?.first() ?: SettingsModel.DEFAULT
        val probeSettings = SingboxProfileProbeSettings(
            timeoutMs = prefs[PROBE_TIMEOUT_MS_KEY].normalizedSingboxProbeTimeoutMs(),
            dnsServers = prefs[SINGBOX_DNS_SERVERS_KEY]?.sorted()?.ifEmpty { null }
                ?: EngineConfig.Singbox.DEFAULT_DNS_SERVERS,
            ipv6Enabled = settings.ipv6Enabled,
        )
        val rejectedProfiles = mutableListOf<RejectedProfile>()
        val probeCandidates = profiles.mapNotNull { profile ->
            when (val recovered = PersistedProfileRecovery.recover(profile.beanBlob, profile.protocolType)) {
                is RecoveryResult.Failure -> {
                    rejectedProfiles += RejectedProfile(
                        protocol = PersistedProtocol.fromId(profile.protocolType)?.label ?: "unknown",
                        schema = recovered.detectedSchemas.joinToString("+") { it.name }.ifEmpty { "none" },
                        reason = recovered.category.name,
                    )
                    profileDao.updateProbeResultIfCurrent(
                        expected = profile,
                        latency = LATENCY_FAILED,
                        error = PROBE_ERROR_UNSUPPORTED,
                    )
                    null
                }
                is RecoveryResult.Success -> profile to recovered.bean
            }
        }
        logRejectedProfiles(rejectedProfiles)
        val results = ConcurrentLinkedQueue<ProbeResult>()
        val batchProbe = profileProbe as? SingboxBatchProfileProbe
        for ((batchIndex, batch) in probeCandidates.chunked(MAX_CONCURRENT_PROFILE_PROBES).withIndex()) {
            val indexedBatch = batch.mapIndexed { index, candidate ->
                IndexedProbeCandidate(
                    index = batchIndex * MAX_CONCURRENT_PROFILE_PROBES + index,
                    profile = candidate.first,
                    bean = candidate.second,
                )
            }
            val outcomes = if (batchProbe != null) {
                probeBatch(indexedBatch, probeSettings, batchProbe, onProfileTestingChanged)
            } else {
                probeLegacyBatch(indexedBatch, probeSettings, onProfileTestingChanged)
            }
            if (outcomes.values.any { it is SingboxProbeOutcome.Failure && it.error == PROBE_ERROR_PROCESS_DIED }) {
                PersistentLoggers.warn(
                    "SingboxProbeService",
                    "profile probe batch aborted category=$PROBE_ERROR_PROCESS_DIED batch=$batchIndex",
                )
                return
            }
            indexedBatch.forEach { candidate ->
                when (val outcome = outcomes[candidate.profile.id] ?: SingboxProbeOutcome.Failure(PROBE_ERROR_FAILED)) {
                    is SingboxProbeOutcome.Success -> {
                        profileDao.updateProbeResultIfCurrent(candidate.profile, outcome.latencyMs, null)
                        results.add(ProbeResult(candidate.index, candidate.profile, outcome.latencyMs))
                    }
                    is SingboxProbeOutcome.Failure -> {
                        profileDao.updateProbeResultIfCurrent(
                            candidate.profile,
                            LATENCY_FAILED,
                            outcome.error,
                        )
                        results.add(ProbeResult(candidate.index, candidate.profile, LATENCY_FAILED))
                    }
                    SingboxProbeOutcome.SkippedActiveRuntime -> Unit
                }
            }
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
        val currentBest = profileDao.getById(best.id)
            ?.takeIf { it.sameProbeIdentity(best) }
            ?: return
        dataStore.edit { prefs ->
            if (selectedProfileAtStart == SELECTED_AUTO || prefs[SELECTED_PROFILE_KEY] != selectedProfileAtStart) {
                return@edit
            }
            prefs[SELECTED_PROFILE_KEY] = currentBest.id
            prefs[BEAN_KEY] = currentBest.beanBlob
        }
    }

    private suspend fun probeBatch(
        candidates: List<IndexedProbeCandidate>,
        settings: SingboxProfileProbeSettings,
        batchProbe: SingboxBatchProfileProbe,
        onProfileTestingChanged: (Long, Boolean) -> Unit,
    ): Map<Long, SingboxProbeOutcome> {
        candidates.forEach { onProfileTestingChanged(it.profile.id, true) }
        return try {
            batchProbe.probeBatch(
                candidates.map { SingboxProfileProbeTarget(it.profile.id, it.bean) },
                settings,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logProbeFailure("probeBatch", error)
            candidates.associate { candidate ->
                candidate.profile.id to SingboxProbeOutcome.Failure(PROBE_ERROR_FAILED)
            }
        } finally {
            candidates.forEach { onProfileTestingChanged(it.profile.id, false) }
        }
    }

    private suspend fun probeLegacyBatch(
        candidates: List<IndexedProbeCandidate>,
        settings: SingboxProfileProbeSettings,
        onProfileTestingChanged: (Long, Boolean) -> Unit,
    ): Map<Long, SingboxProbeOutcome> = coroutineScope {
        candidates.map { candidate ->
            async(probeDispatcher) {
                onProfileTestingChanged(candidate.profile.id, true)
                try {
                    try {
                        val latency = withTimeoutOrNull(settings.timeoutMs.toLong()) {
                            profileProbe.probeLatencyMs(candidate.bean, settings)
                        } ?: LATENCY_TIMED_OUT
                        candidate.profile.id to latency.toProbeOutcome()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        logProbeFailure("probeLatency", error, candidate.profile.id)
                        candidate.profile.id to SingboxProbeOutcome.Failure(PROBE_ERROR_FAILED)
                    }
                } finally {
                    onProfileTestingChanged(candidate.profile.id, false)
                }
            }
        }.awaitAll().toMap()
    }

    private suspend fun ProxyProfileDao.updateProbeResultIfCurrent(
        expected: ProxyProfile,
        latency: Int,
        error: String?,
    ) {
        updateProbeResultIfCurrent(
            id = expected.id,
            protocolType = expected.protocolType,
            beanBlob = expected.beanBlob,
            latency = latency,
            probeError = error,
            lastProbeAt = System.currentTimeMillis(),
        )
    }

    private data class ProbeResult(
        val index: Int,
        val profile: ProxyProfile,
        val latency: Int,
    )

    private data class IndexedProbeCandidate(
        val index: Int,
        val profile: ProxyProfile,
        val bean: AbstractBean,
    )

    companion object {
        val BEAN_KEY = byteArrayPreferencesKey("singbox_vless_bean")
        val SELECTED_PROFILE_KEY = longPreferencesKey("singbox_selected_profile_id")
        const val LATENCY_UNTESTED = -1
        const val LATENCY_FAILED = -2
        private const val SELECTED_AUTO = -1L
        const val MAX_CONCURRENT_PROFILE_PROBES = 10
        const val PROBE_ERROR_UNSUPPORTED = "unsupported"
        const val PROBE_ERROR_FAILED = "probe failed"
        const val PROBE_ERROR_PROCESS_DIED = "PROCESS_DIED"
        const val PROBE_ERROR_TIMEOUT = "timeout"
        const val DEFAULT_PROBE_TIMEOUT_MS = 3_000
        const val MIN_PROBE_TIMEOUT_MS = 1_000
        const val MAX_PROBE_TIMEOUT_MS = 10_000
        val PROBE_TIMEOUT_MS_KEY = intPreferencesKey("singbox_probe_timeout_ms")
        val SINGBOX_DNS_SERVERS_KEY = stringSetPreferencesKey("singbox_dns_servers")
    }
}

private fun ProxyProfile.sameProbeIdentity(other: ProxyProfile): Boolean =
    id == other.id && protocolType == other.protocolType && beanBlob.contentEquals(other.beanBlob)

internal fun interface SingboxProfileProbe {
    suspend fun probeLatencyMs(bean: AbstractBean, settings: SingboxProfileProbeSettings): Int
}

internal interface SingboxBatchProfileProbe {
    suspend fun probeBatch(
        targets: List<SingboxProfileProbeTarget>,
        settings: SingboxProfileProbeSettings,
    ): Map<Long, SingboxProbeOutcome>
}

internal data class SingboxProfileProbeTarget(
    val profileId: Long,
    val bean: AbstractBean,
)

internal sealed interface SingboxProbeOutcome {
    data class Success(val latencyMs: Int) : SingboxProbeOutcome

    data class Failure(val error: String) : SingboxProbeOutcome

    data object SkippedActiveRuntime : SingboxProbeOutcome
}

internal data class SingboxProfileProbeSettings(
    val timeoutMs: Int,
    val dnsServers: List<String>,
    val ipv6Enabled: Boolean,
)

internal const val LATENCY_SKIPPED_ACTIVE_RUNTIME = Int.MIN_VALUE
internal const val LATENCY_TIMED_OUT = Int.MIN_VALUE + 1
private fun Int.toProbeOutcome(): SingboxProbeOutcome = when {
    this >= 0 -> SingboxProbeOutcome.Success(this)
    this == LATENCY_SKIPPED_ACTIVE_RUNTIME -> SingboxProbeOutcome.SkippedActiveRuntime
    this == LATENCY_TIMED_OUT -> SingboxProbeOutcome.Failure(SingboxProbeService.PROBE_ERROR_TIMEOUT)
    else -> SingboxProbeOutcome.Failure(SingboxProbeService.PROBE_ERROR_FAILED)
}

internal fun Int?.normalizedSingboxProbeTimeoutMs(): Int =
    (this ?: SingboxProbeService.DEFAULT_PROBE_TIMEOUT_MS).coerceIn(
        SingboxProbeService.MIN_PROBE_TIMEOUT_MS,
        SingboxProbeService.MAX_PROBE_TIMEOUT_MS,
    )

private class SingboxServiceProfileProbe(
    private val context: Context,
) : SingboxProfileProbe, SingboxBatchProfileProbe {
    private val mutex = Mutex()

    override suspend fun probeLatencyMs(
        bean: AbstractBean,
        settings: SingboxProfileProbeSettings,
    ): Int {
        val outcome = probeBatch(
            listOf(SingboxProfileProbeTarget(SINGLE_PROFILE_ID, bean)),
            settings,
        )[SINGLE_PROFILE_ID]
        return when (outcome) {
            is SingboxProbeOutcome.Success -> outcome.latencyMs
            is SingboxProbeOutcome.Failure, null -> SingboxProbeService.LATENCY_FAILED
            SingboxProbeOutcome.SkippedActiveRuntime -> LATENCY_SKIPPED_ACTIVE_RUNTIME
        }
    }

    override suspend fun probeBatch(
        targets: List<SingboxProfileProbeTarget>,
        settings: SingboxProfileProbeSettings,
    ): Map<Long, SingboxProbeOutcome> = mutex.withLock {
        withContext(Dispatchers.IO) {
            probeBatchLocked(targets, settings)
        }
    }

    @Suppress("ReturnCount")
    private suspend fun probeBatchLocked(
        targets: List<SingboxProfileProbeTarget>,
        settings: SingboxProfileProbeSettings,
    ): Map<Long, SingboxProbeOutcome> {
        if (targets.isEmpty()) return emptyMap()
        val localProtector = ProfileProbeProtector()
        coroutineContext.ensureActive()
        val ports = allocateProbePorts(targets.size)
        val config = runCatching {
            ConfigBuilder.buildProbeConfig(
                targets.zip(ports).map { (target, port) ->
                    ConfigBuilder.ProbeTarget(target.bean, port)
                },
                dnsServers = settings.dnsServers,
                ipv6Enabled = settings.ipv6Enabled,
            )
        }.getOrElse {
            it.rethrowCancellation()
            logProbeFailure("buildConfig", it)
            return outcomes(targets, SingboxProbeOutcome.Failure(SingboxProbeService.PROBE_ERROR_FAILED))
        }
        val binding = bindProcess()
            ?: return outcomes(targets, SingboxProbeOutcome.Failure(SingboxProbeService.PROBE_ERROR_FAILED))
        val ownerId = UUID.randomUUID().mostSignificantBits
        var shouldStop = false
        try {
            val process = binding.process
            if (binding.processDied.get()) {
                return outcomes(targets, SingboxProbeOutcome.Failure(SingboxProbeService.PROBE_ERROR_PROCESS_DIED))
            }
            coroutineContext.ensureActive()
            val started = runCatching { process.startProxyModeIfIdle(ownerId, config, localProtector) }.getOrElse {
                it.rethrowCancellation()
                logProbeFailure("startProxyModeIfIdle", it)
                if (binding.processDied.get()) {
                    return outcomes(targets, SingboxProbeOutcome.Failure(SingboxProbeService.PROBE_ERROR_PROCESS_DIED))
                }
                return outcomes(targets, SingboxProbeOutcome.Failure(SingboxProbeService.PROBE_ERROR_FAILED))
            }
            if (!started) return outcomes(targets, SingboxProbeOutcome.SkippedActiveRuntime)
            shouldStop = true
            coroutineContext.ensureActive()
            if (!waitWhileProcessAlive(
                    binding,
                    PROBE_START_DELAY_MS,
                )
            ) {
                return outcomes(targets, SingboxProbeOutcome.Failure(SingboxProbeService.PROBE_ERROR_PROCESS_DIED))
            }
            if (binding.processDied.get()) {
                return outcomes(targets, SingboxProbeOutcome.Failure(SingboxProbeService.PROBE_ERROR_PROCESS_DIED))
            }
            val running = runCatching { process.runtimeRunning() }.getOrElse {
                it.rethrowCancellation()
                logProbeFailure("runtimeRunning", it)
                false
            }
            if (binding.processDied.get()) {
                return outcomes(targets, SingboxProbeOutcome.Failure(SingboxProbeService.PROBE_ERROR_PROCESS_DIED))
            }
            if (!running) {
                return outcomes(targets, SingboxProbeOutcome.Failure(SingboxProbeService.PROBE_ERROR_FAILED))
            }
            coroutineContext.ensureActive()
            val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(settings.timeoutMs.toLong())
            val results = coroutineScope {
                targets.zip(ports).map { (target, port) ->
                    async(Dispatchers.IO) {
                        target.profileId to probeRoutedWithRetry(
                            port,
                            settings.timeoutMs,
                            deadlineNanos,
                            binding.processDied,
                            binding.processDeath,
                            binding.probeCancellation,
                        )
                    }
                }.awaitAll().toMap()
            }
            return if (binding.processDied.get()) {
                outcomes(targets, SingboxProbeOutcome.Failure(SingboxProbeService.PROBE_ERROR_PROCESS_DIED))
            } else {
                results
            }
        } finally {
            if (shouldStop) {
                withContext(NonCancellable) {
                    runCatching { binding.process.stopAndWait(ownerId, REMOTE_STOP_TIMEOUT_MS) }.onFailure {
                        logProbeFailure("stopAndWait", it)
                    }
                }
            }
            runCatching { binding.binder.unlinkToDeath(binding.deathRecipient, 0) }.onFailure {
                logProbeFailure("unlinkToDeath", it)
            }
            runCatching { context.unbindService(binding.connection) }.onFailure {
                logProbeFailure("unbindService", it)
            }
        }
    }

    private suspend fun probeRoutedWithRetry(
        port: Int,
        timeoutMs: Int,
        deadlineNanos: Long,
        processDied: AtomicBoolean,
        processDeath: CompletableDeferred<Unit>,
        probeCancellation: RoutedProbeCancellation,
    ): SingboxProbeOutcome {
        val probe = SingboxHttp204RoutedProbe(timeoutMs = timeoutMs.normalizedSingboxProbeTimeoutMs())
        repeat(PROBE_ATTEMPTS) { attempt ->
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (processDied.get()) {
                return SingboxProbeOutcome.Failure(SingboxProbeService.PROBE_ERROR_PROCESS_DIED)
            }
            if (remainingTimeoutMs(deadlineNanos) == null) {
                return SingboxProbeOutcome.Failure(SingboxProbeService.PROBE_ERROR_TIMEOUT)
            }
            when (val result = probe.probeUntil(port, deadlineNanos, probeCancellation)) {
                is ru.ozero.enginesingbox.RoutedProbeResult.Success -> {
                    return SingboxProbeOutcome.Success(
                        result.latencyMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    )
                }
                is ru.ozero.enginesingbox.RoutedProbeResult.Failure -> {
                    if (processDied.get()) {
                        return SingboxProbeOutcome.Failure(SingboxProbeService.PROBE_ERROR_PROCESS_DIED)
                    }
                    PersistentLoggers.debug(
                        "SingboxProbeService",
                        "profile probe outbound failed ${result.safeDetail ?: "category=${result.reason.name.lowercase()}"}",
                    )
                    if (attempt == PROBE_ATTEMPTS - 1) {
                        return SingboxProbeOutcome.Failure(result.reason.profileProbeStatus())
                    }
                }
            }
            if (attempt < PROBE_ATTEMPTS - 1) {
                val retryDelay = minOf(
                    PROBE_RETRY_DELAY_MS,
                    remainingTimeoutMs(deadlineNanos)?.toLong() ?: 0L,
                )
                val died = withTimeoutOrNull(retryDelay) {
                    processDeath.await()
                    true
                } ?: false
                if (died) return SingboxProbeOutcome.Failure(SingboxProbeService.PROBE_ERROR_PROCESS_DIED)
            }
        }
        return if (remainingTimeoutMs(deadlineNanos) == null) {
            SingboxProbeOutcome.Failure(SingboxProbeService.PROBE_ERROR_TIMEOUT)
        } else {
            SingboxProbeOutcome.Failure(SingboxProbeService.PROBE_ERROR_FAILED)
        }
    }

    private fun outcomes(
        targets: List<SingboxProfileProbeTarget>,
        outcome: SingboxProbeOutcome,
    ): Map<Long, SingboxProbeOutcome> = targets.associate { it.profileId to outcome }

    private fun bindProcess(): Binding? {
        val latch = CountDownLatch(1)
        var process: ISingboxEngineProcess? = null
        var binder: IBinder? = null
        val processDied = AtomicBoolean(false)
        val processDeath = CompletableDeferred<Unit>()
        val probeCancellation = RoutedProbeCancellation()
        val markProcessDied = { operation: String ->
            if (processDied.compareAndSet(false, true)) {
                logProbeStateFailure(operation, "binder_death")
            }
            processDeath.complete(Unit)
            probeCancellation.cancel()
        }
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                process = ISingboxEngineProcess.Stub.asInterface(service)
                binder = service
                latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) {
                process = null
                markProcessDied("onServiceDisconnected")
            }

            override fun onBindingDied(name: ComponentName?) {
                process = null
                markProcessDied("onBindingDied")
            }
        }
        val component = ComponentName(context, "ru.ozero.singboxprocess.SingboxEngineService")
        val intent = Intent().apply { this.component = component }
        val bound = runCatching {
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT)
        }.getOrElse {
            logProbeFailure("bindService", it)
            return null
        }
        if (!bound) {
            logProbeStateFailure("bindService", "bind_rejected")
            unbindProbeService(connection, "bindRejected")
            return null
        }
        if (!latch.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            logProbeStateFailure("bindService", "bind_timeout")
            unbindProbeService(connection, "bindTimeout")
            return null
        }
        val connectedProcess = process ?: run {
            logProbeStateFailure("bindService", "process_null")
            unbindProbeService(connection, "processNull")
            return null
        }
        val connectedBinder = binder ?: run {
            logProbeStateFailure("bindService", "binder_null")
            unbindProbeService(connection, "binderNull")
            return null
        }
        val recipient = IBinder.DeathRecipient { markProcessDied("deathRecipient") }
        if (runCatching { connectedBinder.linkToDeath(recipient, 0) }.onFailure {
                logProbeFailure("linkToDeath", it)
            }.isFailure
        ) {
            markProcessDied("linkToDeath")
        }
        return Binding(
            connectedProcess,
            connection,
            connectedBinder,
            recipient,
            processDied,
            processDeath,
            probeCancellation,
        )
    }

    private fun unbindProbeService(connection: ServiceConnection, operation: String) {
        runCatching { context.unbindService(connection) }.onFailure {
            logProbeFailure("unbindService.$operation", it)
        }
    }

    private suspend fun waitWhileProcessAlive(binding: Binding, delayMs: Long): Boolean {
        if (binding.processDied.get()) return false
        if (delayMs <= 0L) return !binding.processDied.get()
        val died = withTimeoutOrNull(delayMs) {
            binding.processDeath.await()
            true
        } ?: false
        return !died
    }

    private fun allocateProbePorts(count: Int): List<Int> {
        val sockets = List(count) { ServerSocket(0, 1, InetAddress.getLoopbackAddress()) }
        return try {
            sockets.map { it.localPort }
        } finally {
            sockets.forEach { it.close() }
        }
    }

    private fun remainingTimeoutMs(deadlineNanos: Long): Long? {
        val remainingNanos = deadlineNanos - System.nanoTime()
        if (remainingNanos <= 0) return null
        return TimeUnit.NANOSECONDS.toMillis(remainingNanos).coerceAtLeast(1L)
    }

    private data class Binding(
        val process: ISingboxEngineProcess,
        val connection: ServiceConnection,
        val binder: IBinder,
        val deathRecipient: IBinder.DeathRecipient,
        val processDied: AtomicBoolean,
        val processDeath: CompletableDeferred<Unit>,
        val probeCancellation: RoutedProbeCancellation,
    )

    private companion object {
        const val PROBE_START_DELAY_MS = 150L
        const val PROBE_RETRY_DELAY_MS = 250L
        const val PROBE_ATTEMPTS = 3
        const val REMOTE_STOP_TIMEOUT_MS = 3_000L
        const val BIND_TIMEOUT_MS = 5_000L
        const val SINGLE_PROFILE_ID = 0L
    }
}

internal fun ru.ozero.enginesingbox.RoutedProbeResult.Reason.profileProbeStatus(): String = when (this) {
    ru.ozero.enginesingbox.RoutedProbeResult.Reason.REMOTE_CLOSED -> "Remote closed"
    ru.ozero.enginesingbox.RoutedProbeResult.Reason.SOCKS_REPLY -> "SOCKS rejected"
    ru.ozero.enginesingbox.RoutedProbeResult.Reason.DNS -> "DNS failed"
    ru.ozero.enginesingbox.RoutedProbeResult.Reason.TLS_CERTIFICATE -> "TLS certificate"
    ru.ozero.enginesingbox.RoutedProbeResult.Reason.TLS_HANDSHAKE,
    ru.ozero.enginesingbox.RoutedProbeResult.Reason.TLS,
    -> "TLS handshake"
    ru.ozero.enginesingbox.RoutedProbeResult.Reason.TIMEOUT -> "Timeout"
    ru.ozero.enginesingbox.RoutedProbeResult.Reason.UNEXPECTED_RESPONSE -> "Unexpected response"
    ru.ozero.enginesingbox.RoutedProbeResult.Reason.CONNECT,
    ru.ozero.enginesingbox.RoutedProbeResult.Reason.IO,
    ru.ozero.enginesingbox.RoutedProbeResult.Reason.SOCKS_NOT_READY,
    -> "Connect failed"
}

internal class ProfileProbeProtector : ISingboxProtector.Stub() {
    private val modeLogged = AtomicBoolean(false)
    private val failureLogged = AtomicBoolean(false)

    override fun protect(socket: ParcelFileDescriptor): Boolean = socket.use {
        val result = when (VpnSocketProtectorHolder.protectIfBound(it.fd)) {
            null -> {
                logModeOnce("no Ozero VPN, protection not required")
                true
            }
            true -> {
                logModeOnce("active VPN protector")
                true
            }
            false -> {
                logModeOnce("active VPN protector")
                if (failureLogged.compareAndSet(false, true)) {
                    PersistentLoggers.warn("SingboxProbeService", "active VPN protect failed")
                }
                false
            }
        }
        result
    }

    private fun logModeOnce(mode: String) {
        if (modeLogged.compareAndSet(false, true)) {
            PersistentLoggers.info("SingboxProbeService", "profile probe protector: $mode")
        }
    }
}
private data class RejectedProfile(val protocol: String, val schema: String, val reason: String)

private fun logProbeFailure(operation: String, failure: Throwable, profileId: Long? = null) {
    PersistentLoggers.warn(
        "SingboxProbeService",
        "operation=$operation stableCategory=probe exceptionClass=${failure::class.java.simpleName} " +
            "sanitizedMessage=${LogSanitizer.sanitize(failure.message.orEmpty())} " +
            "generation=probe processId=unknown profileId=${profileId ?: "unknown"}",
    )
}

private fun logProbeStateFailure(operation: String, stableCategory: String) {
    PersistentLoggers.warn(
        "SingboxProbeService",
        "operation=$operation stableCategory=$stableCategory exceptionClass=none " +
            "sanitizedMessage=$stableCategory generation=probe processId=unknown profileId=unknown",
    )
}

private fun Throwable.rethrowCancellation() {
    if (this is CancellationException) throw this
}

private fun logRejectedProfiles(rejectedProfiles: List<RejectedProfile>) {
    if (rejectedProfiles.isEmpty()) return
    val protocols = rejectedProfiles.groupingBy { it.protocol }.eachCount().toStableDiagnosticString()
    val schemas = rejectedProfiles.groupingBy { it.schema }.eachCount().toStableDiagnosticString()
    val reasons = rejectedProfiles.groupingBy { it.reason }.eachCount().toStableDiagnosticString()
    PersistentLoggers.warn(
        "SingboxProbeService",
        "singbox profiles rejected count=${rejectedProfiles.size} " +
            "byProtocol=$protocols bySchema=$schemas byReason=$reasons",
    )
}

private fun Map<String, Int>.toStableDiagnosticString(): String =
    entries.sortedBy { it.key }.joinToString(prefix = "{", postfix = "}") { "${it.key}=${it.value}" }
