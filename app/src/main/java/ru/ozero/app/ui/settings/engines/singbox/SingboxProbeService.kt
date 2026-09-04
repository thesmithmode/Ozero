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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
        val probeCandidates = recoverProbeCandidates(profiles)
        val results = ConcurrentLinkedQueue<ProbeResult>()
        val pending = probeCandidates.associate { it.first.id to it.first }.toMutableMap()
        val indexedCandidates = probeCandidates.mapIndexed { index, candidate ->
            IndexedProbeCandidate(
                index = index,
                profile = candidate.first,
                bean = candidate.second,
            )
        }
        var pendingTerminalError = PROBE_ERROR_FAILED
        try {
            probeCandidateBatches(
                indexedCandidates = indexedCandidates,
                settings = probeSettings,
                onProfileTestingChanged = onProfileTestingChanged,
                results = results,
                pending = pending,
            )
        } catch (error: CancellationException) {
            pendingTerminalError = PROBE_ERROR_CANCELLED
            throw error
        } finally {
            persistPendingTerminalResults(pending, pendingTerminalError, onProfileTestingChanged)
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

    private suspend fun recoverProbeCandidates(
        profiles: List<ProxyProfile>,
    ): List<Pair<ProxyProfile, AbstractBean>> {
        val rejectedProfiles = mutableListOf<RejectedProfile>()
        val candidates = mutableListOf<Pair<ProxyProfile, AbstractBean>>()
        profiles.forEach { profile ->
            when (val recovered = PersistedProfileRecovery.recover(profile.beanBlob, profile.protocolType)) {
                is RecoveryResult.Failure -> recordRejectedProfile(profile, recovered, rejectedProfiles)
                is RecoveryResult.Success -> candidates += profile to recovered.bean
            }
        }
        logRejectedProfiles(rejectedProfiles)
        return candidates
    }

    private suspend fun recordRejectedProfile(
        profile: ProxyProfile,
        recovered: RecoveryResult.Failure,
        rejectedProfiles: MutableList<RejectedProfile>,
    ) {
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
    }

    private suspend fun probeCandidateBatches(
        indexedCandidates: List<IndexedProbeCandidate>,
        settings: SingboxProfileProbeSettings,
        onProfileTestingChanged: (Long, Boolean) -> Unit,
        results: ConcurrentLinkedQueue<ProbeResult>,
        pending: MutableMap<Long, ProxyProfile>,
    ) {
        val batchProbe = profileProbe as? SingboxBatchProfileProbe
        indexedCandidates.chunked(MAX_PROBE_RUNTIME_TARGETS).forEach { batch ->
            val outcomes = if (batchProbe != null) {
                probeBatch(batch, settings, batchProbe, onProfileTestingChanged)
            } else {
                probeLegacyBatch(batch, settings, onProfileTestingChanged)
            }
            batch.forEach { candidate ->
                val outcome = outcomes[candidate.profile.id]
                    ?: SingboxProbeOutcome.Failure(PROBE_ERROR_FAILED)
                if (persistProbeOutcome(candidate, outcome, results)) {
                    pending.remove(candidate.profile.id)
                }
            }
        }
    }

    private suspend fun persistProbeOutcome(
        candidate: IndexedProbeCandidate,
        outcome: SingboxProbeOutcome,
        results: ConcurrentLinkedQueue<ProbeResult>,
    ): Boolean = try {
        when (outcome) {
            is SingboxProbeOutcome.Success -> {
                profileDao.updateProbeResultIfCurrent(candidate.profile, outcome.latencyMs, null)
                results.add(ProbeResult(candidate.index, candidate.profile, outcome.latencyMs))
            }
            is SingboxProbeOutcome.Failure -> {
                profileDao.updateProbeResultIfCurrent(candidate.profile, LATENCY_FAILED, outcome.error)
                results.add(ProbeResult(candidate.index, candidate.profile, LATENCY_FAILED))
            }
            SingboxProbeOutcome.SkippedActiveRuntime -> {
                profileDao.updateProbeResultIfCurrent(
                    candidate.profile,
                    LATENCY_FAILED,
                    PROBE_ERROR_RUNTIME_BUSY,
                )
            }
        }
        true
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        logProbeFailure("persistProbeResult", error, candidate.profile.id)
        false
    }

    private suspend fun persistPendingTerminalResults(
        pending: Map<Long, ProxyProfile>,
        terminalError: String,
        onProfileTestingChanged: (Long, Boolean) -> Unit,
    ) {
        if (pending.isEmpty()) return
        withContext(NonCancellable) {
            pending.values.toList().forEach { profile ->
                try {
                    profileDao.updateProbeResultIfCurrent(profile, LATENCY_FAILED, terminalError)
                } catch (error: Exception) {
                    logProbeFailure("persistPendingTerminalResult", error, profile.id)
                } finally {
                    runCatching { onProfileTestingChanged(profile.id, false) }.onFailure { callbackError ->
                        logProbeFailure("clearProfileTestingState", callbackError, profile.id)
                    }
                }
            }
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
        val limiter = Semaphore(MAX_PARALLEL_HTTP_PROBES)
        candidates.map { candidate ->
            async(probeDispatcher) {
                limiter.withPermit {
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
        const val MAX_PARALLEL_HTTP_PROBES = 10
        const val MAX_PROBE_RUNTIME_TARGETS = 50
        const val MAX_CONCURRENT_PROFILE_PROBES = MAX_PARALLEL_HTTP_PROBES
        const val PROBE_ERROR_UNSUPPORTED = "unsupported"
        const val PROBE_ERROR_FAILED = "probe failed"
        const val PROBE_ERROR_PROCESS_DIED = "PROCESS_DIED"
        const val PROBE_ERROR_TIMEOUT = "timeout"
        const val PROBE_ERROR_CANCELLED = "cancelled"
        const val PROBE_ERROR_RUNTIME_BUSY = "runtime busy"
        const val PROBE_ERROR_CONFIG_TOO_LARGE = "config too large"
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

    private suspend fun probeBatchLocked(
        targets: List<SingboxProfileProbeTarget>,
        settings: SingboxProfileProbeSettings,
    ): Map<Long, SingboxProbeOutcome> {
        if (targets.isEmpty()) return emptyMap()
        coroutineContext.ensureActive()
        val ports = allocateProbePorts(targets.size)
        val config = buildProbeConfig(targets, ports, settings)
            ?: return failedOutcomes(targets)
        if (config.toByteArray(Charsets.UTF_8).size > MAX_PROBE_CONFIG_BYTES) {
            logProbeStateFailure("buildConfig", "config_too_large")
            if (targets.size == 1) {
                return outcomes(
                    targets,
                    SingboxProbeOutcome.Failure(SingboxProbeService.PROBE_ERROR_CONFIG_TOO_LARGE),
                )
            }
            val midpoint = targets.size / 2
            return probeBatchLocked(targets.subList(0, midpoint), settings) +
                probeBatchLocked(targets.subList(midpoint, targets.size), settings)
        }
        val binding = bindProcess()
            ?: return failedOutcomes(targets)
        return runProbeRuntime(targets, ports, settings, config, binding)
    }

    private fun buildProbeConfig(
        targets: List<SingboxProfileProbeTarget>,
        ports: List<Int>,
        settings: SingboxProfileProbeSettings,
    ): String? = try {
        ConfigBuilder.buildProbeConfig(
            targets.zip(ports).map { (target, port) ->
                ConfigBuilder.ProbeTarget(target.bean, port)
            },
            dnsServers = settings.dnsServers,
            ipv6Enabled = settings.ipv6Enabled,
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        logProbeFailure("buildConfig", error)
        null
    }

    private suspend fun runProbeRuntime(
        targets: List<SingboxProfileProbeTarget>,
        ports: List<Int>,
        settings: SingboxProfileProbeSettings,
        config: String,
        binding: Binding,
    ): Map<Long, SingboxProbeOutcome> {
        val ownerId = UUID.randomUUID().mostSignificantBits
        val localProtector = ProfileProbeProtector()
        var shouldStop = false
        return try {
            when (startProbeRuntime(binding, ownerId, config, localProtector)) {
                ProbeRuntimeStart.STARTED -> {
                    shouldStop = true
                    if (isProbeRuntimeReady(binding)) {
                        probeTargets(targets, ports, settings, binding)
                    } else {
                        failedOutcomes(targets, processDied = binding.processDied.get())
                    }
                }
                ProbeRuntimeStart.BUSY -> outcomes(targets, SingboxProbeOutcome.SkippedActiveRuntime)
                ProbeRuntimeStart.PROCESS_DIED -> failedOutcomes(targets, processDied = true)
                ProbeRuntimeStart.FAILED -> failedOutcomes(targets)
            }
        } finally {
            cleanupProbeRuntime(binding, ownerId, shouldStop)
        }
    }

    private suspend fun startProbeRuntime(
        binding: Binding,
        ownerId: Long,
        config: String,
        protector: ProfileProbeProtector,
    ): ProbeRuntimeStart {
        if (binding.processDied.get()) return ProbeRuntimeStart.PROCESS_DIED
        coroutineContext.ensureActive()
        val started = try {
            binding.process.startProxyModeIfIdle(ownerId, config, protector)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logProbeFailure("startProxyModeIfIdle", error)
            return if (binding.processDied.get()) ProbeRuntimeStart.PROCESS_DIED else ProbeRuntimeStart.FAILED
        }
        return when {
            binding.processDied.get() -> ProbeRuntimeStart.PROCESS_DIED
            started -> ProbeRuntimeStart.STARTED
            else -> ProbeRuntimeStart.BUSY
        }
    }

    private suspend fun isProbeRuntimeReady(binding: Binding): Boolean {
        coroutineContext.ensureActive()
        if (!waitWhileProcessAlive(binding, PROBE_START_DELAY_MS) || binding.processDied.get()) return false
        val running = try {
            binding.process.runtimeRunning()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logProbeFailure("runtimeRunning", error)
            false
        }
        return running && !binding.processDied.get()
    }

    private suspend fun probeTargets(
        targets: List<SingboxProfileProbeTarget>,
        ports: List<Int>,
        settings: SingboxProfileProbeSettings,
        binding: Binding,
    ): Map<Long, SingboxProbeOutcome> {
        val limiter = Semaphore(SingboxProbeService.MAX_PARALLEL_HTTP_PROBES)
        val results = coroutineScope {
            targets.zip(ports).map { (target, port) ->
                async(Dispatchers.IO) {
                    limiter.withPermit {
                        val deadlineNanos = System.nanoTime() +
                            TimeUnit.MILLISECONDS.toNanos(settings.timeoutMs.toLong())
                        target.profileId to probeRoutedWithRetry(
                            port,
                            settings.timeoutMs,
                            deadlineNanos,
                            binding.processDied,
                            binding.processDeath,
                            binding.probeCancellation,
                        )
                    }
                }
            }.awaitAll().toMap()
        }
        return if (binding.processDied.get()) {
            failedOutcomes(targets, processDied = true)
        } else {
            results
        }
    }

    private suspend fun cleanupProbeRuntime(binding: Binding, ownerId: Long, shouldStop: Boolean) {
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

    private fun failedOutcomes(
        targets: List<SingboxProfileProbeTarget>,
        processDied: Boolean = false,
    ): Map<Long, SingboxProbeOutcome> = outcomes(
        targets,
        SingboxProbeOutcome.Failure(
            if (processDied) {
                SingboxProbeService.PROBE_ERROR_PROCESS_DIED
            } else {
                SingboxProbeService.PROBE_ERROR_FAILED
            },
        ),
    )

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

    private enum class ProbeRuntimeStart {
        STARTED,
        BUSY,
        PROCESS_DIED,
        FAILED,
    }

    private companion object {
        const val PROBE_START_DELAY_MS = 150L
        const val PROBE_RETRY_DELAY_MS = 250L
        const val PROBE_ATTEMPTS = 3
        const val REMOTE_STOP_TIMEOUT_MS = 3_000L
        const val BIND_TIMEOUT_MS = 5_000L
        const val SINGLE_PROFILE_ID = 0L
        const val MAX_PROBE_CONFIG_BYTES = 256 * 1024
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