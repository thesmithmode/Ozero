package ru.ozero.commonvpn

import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import ru.ozero.commonvpn.split.SplitTunnelConfig
import ru.ozero.commonvpn.split.TunBuilderConfigurator
import ru.ozero.enginescore.ChainOrchestrator
import ru.ozero.enginescore.ChainResult
import ru.ozero.enginescore.ChainStep
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.EnginePreflight
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginescore.SocketProtector
import ru.ozero.enginescore.TunAttachResult
import ru.ozero.enginescore.TunFdAcceptor
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.enginescore.settings.SettingsRepository
import ru.ozero.enginescore.settings.SplitTunnelMode
import ru.ozero.enginescore.settings.TrafficMode
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class StartSequenceState(
    val tunFdRef: AtomicReference<ParcelFileDescriptor?>,
    val tunIfaceNameRef: AtomicReference<String?>,
    val lockdownStartupFdRef: AtomicReference<ParcelFileDescriptor?>,
    val sessionStartMsRef: AtomicReference<Long>,
    val sessionIdRef: AtomicReference<Long>,
    val stopping: AtomicBoolean,
)

class StartSequenceCollaborators(
    val enginePlugins: Set<EnginePlugin>,
    val chainOrchestrator: ChainOrchestrator,
    val tunnelController: TunnelController,
    val tunnelGateway: HevTunnelGateway,
    val healthMonitor: HealthMonitor,
    val tunBuilderHelper: TunBuilderHelper,
    val engineWatchdog: EngineWatchdogCoordinator,
    val statsLogger: TunnelStatsLogger,
    val splitTunnelRulesProvider: SplitTunnelRulesProvider,
    val settingsRepository: SettingsRepository,
    val sessionStatsRecorder: SessionStatsRecorder,
)

@Suppress("TooManyFunctions")
class StartSequenceCoordinator(
    private val packageName: String,
    private val deps: StartSequenceCollaborators,
    private val state: StartSequenceState,
    private val killswitchSetter: (Boolean) -> Unit,
    private val stopVpnRequest: () -> Unit,
) {

    suspend fun run() {
        if (state.stopping.get()) return
        val settings = withTimeoutOrNull(SETTINGS_READ_TIMEOUT_MS) {
            runCatching { deps.settingsRepository.settings.first() }.getOrNull()
        }
        val splitConfig = readSplitConfig(settings?.splitMode ?: SplitTunnelMode.ALL)
        val trafficMode = settings?.trafficMode ?: TrafficMode.TUN
        val killswitch = settings?.killswitchEnabled ?: false
        killswitchSetter(trafficMode == TrafficMode.TUN && killswitch)
        if (trafficMode == TrafficMode.TUN && killswitch) {
            val startupIpv6 = settings?.ipv6Enabled ?: false
            val startupDns = settings?.customDnsServers.orEmpty()
            runCatching {
                deps.tunBuilderHelper.buildTunBuilder(
                    splitConfig = splitConfig,
                    ipv6Enabled = startupIpv6,
                    customDnsServers = startupDns,
                    applyUnderlying = true,
                ).establish()
            }
                .onSuccess { fd ->
                    if (fd != null) {
                        state.lockdownStartupFdRef.set(fd)
                        PersistentLoggers.info(TAG, "instant lockdown TUN — engine pick pending")
                    }
                }
                .onFailure { PersistentLoggers.error(TAG, "lockdown startup TUN failed: ${it.message}") }
        }

        val manualEngine = settings?.manualEngine
        val autoPicks = if (manualEngine == null) {
            autoCandidatesWithPreflight(settings, trafficMode)
        } else {
            emptyList()
        }
        val picks = if (manualEngine != null) {
            val cfg = buildEngineConfig(manualEngine, settings, trafficMode)
            if (cfg == null || !engineAllowedForTrafficMode(manualEngine, trafficMode)) {
                emptyList()
            } else {
                listOf(manualEngine to cfg)
            }
        } else {
            autoPicks
        }
        if (picks.isEmpty()) {
            val mode = if (manualEngine == null) "auto" else "manual"
            val targetForUi = resolveTargetForUi(manualEngine, settings)
            if (targetForUi == null) {
                PersistentLoggers.error(TAG, "no plugins registered — отказ старта")
            } else {
                PersistentLoggers.error(TAG, "no engine reachable ($mode mode) — отказ старта")
                deps.tunnelController.onProbing(targetForUi)
                deps.tunnelController.onEngineDied(targetForUi, "no engine reachable ($mode mode)")
            }
            stopVpnRequest()
            return
        }
        picks.forEachIndexed { index, pick ->
            val isLast = index == picks.lastIndex
            val success = startSingleEngineCandidate(
                activeEngineId = pick.first,
                activeConfig = pick.second,
                settings = settings,
                splitConfig = splitConfig,
                trafficMode = trafficMode,
                notifyFailure = manualEngine != null || isLast,
            )
            if (success || state.stopping.get()) return
            if (!isLast) resetAfterAutoCandidateFailure(pick.first)
        }
    }

    private suspend fun startSingleEngineCandidate(
        activeEngineId: EngineId,
        activeConfig: EngineConfig,
        settings: SettingsModel?,
        splitConfig: SplitTunnelConfig,
        trafficMode: TrafficMode,
        notifyFailure: Boolean,
    ): Boolean {
        deps.tunnelController.onProbing(activeEngineId)
        if (state.stopping.get()) return false
        if (trafficMode == TrafficMode.PROXY) {
            return runSingleProxy(activeEngineId, activeConfig, notifyFailure)
        }
        val usesCustomTun = engineNeedsCustomTun(activeEngineId)
        val ipv6Enabled = settings?.ipv6Enabled ?: false
        val established = establishTunAndChain(
            activeEngineId = activeEngineId,
            activeConfig = activeConfig,
            splitConfig = splitConfig,
            ipv6Enabled = ipv6Enabled,
            customDns = settings?.customDnsServers.orEmpty(),
            usesCustomTun = usesCustomTun,
            notifyFailure = notifyFailure,
        ) ?: return false
        val fd = established.first
        val chainResult = established.second
        if (!routeTrafficForEngine(activeEngineId, fd, chainResult.finalSocksPort, notifyFailure)) return false

        if (!awaitEngineReady(activeEngineId)) {
            runCatching { deps.chainOrchestrator.stop() }
            reportEngineFailure(
                activeEngineId,
                "awaitReady fail — handshake/probe не подтверждён",
                notifyFailure,
            )
            return false
        }

        deps.tunnelController.onEngineStarted(activeEngineId, chainResult.finalSocksPort)
        val nowMs = System.currentTimeMillis()
        state.sessionStartMsRef.set(nowMs)
        state.sessionIdRef.set(
            runCatching { deps.sessionStatsRecorder.startSession(activeEngineId.name, nowMs) }.getOrDefault(-1L),
        )
        if (!usesCustomTun) {
            runCatching { deps.healthMonitor.start(chainResult.finalSocksPort) }
                .onFailure { t ->
                    if (t is CancellationException) throw t
                    PersistentLoggers.warn(TAG, "healthMonitor.start threw: ${t.message}")
                }
        }
        if (!usesCustomTun) deps.engineWatchdog.startHealthKillswitchWatcher(activeEngineId)
        if (usesCustomTun) deps.engineWatchdog.startPeerWatchdog(activeEngineId)
        deps.engineWatchdog.startStagnationWatchdog(activeEngineId)
        deps.statsLogger.start()
        return true
    }

    private suspend fun runSingleProxy(
        engineId: EngineId,
        config: EngineConfig,
        notifyFailure: Boolean,
    ): Boolean {
        val chainResult = startChain(engineId, config, notifyFailure) ?: return false
        return finishProxyOnly(engineId, chainResult.finalSocksPort, notifyFailure)
    }

    private suspend fun finishProxyOnly(
        engineId: EngineId,
        socksPort: Int,
        notifyFailure: Boolean,
    ): Boolean {
        if (socksPort <= 0) {
            runCatching { deps.chainOrchestrator.stop() }
            reportEngineFailure(engineId, "engine does not expose local proxy endpoint", notifyFailure)
            return false
        }
        if (!awaitEngineReady(engineId)) {
            runCatching { deps.chainOrchestrator.stop() }
            reportEngineFailure(engineId, "proxy awaitReady fail", notifyFailure)
            return false
        }
        deps.tunnelController.onEngineStarted(engineId, socksPort)
        val nowMs = System.currentTimeMillis()
        state.sessionStartMsRef.set(nowMs)
        state.sessionIdRef.set(
            runCatching { deps.sessionStatsRecorder.startSession("${engineId.name}:PROXY", nowMs) }.getOrDefault(-1L),
        )
        runCatching { deps.healthMonitor.start(socksPort) }
            .onFailure { t ->
                if (t is CancellationException) throw t
                PersistentLoggers.warn(TAG, "proxy healthMonitor.start threw: ${t.message}")
            }
        deps.engineWatchdog.startHealthKillswitchWatcher(engineId)
        deps.statsLogger.start()
        return true
    }

    suspend fun engineNeedsCustomTun(engineId: EngineId): Boolean {
        val plugin = deps.enginePlugins.firstOrNull { it.id == engineId } ?: return false
        return plugin is TunFdAcceptor
    }

    private suspend fun establishTunAndChain(
        activeEngineId: EngineId,
        activeConfig: EngineConfig,
        splitConfig: SplitTunnelConfig,
        ipv6Enabled: Boolean,
        customDns: List<String>,
        usesCustomTun: Boolean,
        notifyFailure: Boolean = true,
    ): Pair<ParcelFileDescriptor, ChainResult.Success>? {
        if (usesCustomTun) {
            val tun = establishTunForEngine(activeEngineId, splitConfig, ipv6Enabled) ?: return null
            state.lockdownStartupFdRef.getAndSet(null)?.runCatching { close() }
            if (state.stopping.get()) {
                runCatching { tun.close() }
                state.tunFdRef.compareAndSet(tun, null)
                return null
            }
            val chain = startChain(activeEngineId, activeConfig, notifyFailure) ?: return null
            return tun to chain
        }
        val chain = startChain(activeEngineId, activeConfig, notifyFailure) ?: return null
        val tun = establishTun(splitConfig, ipv6 = ipv6Enabled, customDns = customDns) ?: run {
            runCatching { deps.chainOrchestrator.stop() }
            reportEngineFailure(
                activeEngineId,
                "establishTun fail после startChain — UI не должен застрять в Connecting",
                notifyFailure,
            )
            return null
        }
        state.lockdownStartupFdRef.getAndSet(null)?.runCatching { close() }
        if (state.stopping.get()) {
            runCatching { tun.close() }
            state.tunFdRef.compareAndSet(tun, null)
            runCatching { deps.chainOrchestrator.stop() }
            return null
        }
        return tun to chain
    }

    private suspend fun readSplitConfig(mode: SplitTunnelMode): SplitTunnelConfig {
        val allowlist = if (mode == SplitTunnelMode.ALLOWLIST) {
            val r = withTimeoutOrNull(SETTINGS_READ_TIMEOUT_MS) {
                runCatching { deps.splitTunnelRulesProvider.allowlistPackages() }.getOrNull()
            }
            if (r == null) PersistentLoggers.warn(TAG, "allowlist read timeout — fallback emptySet")
            r ?: emptySet()
        } else {
            emptySet()
        }
        val blocklist = if (mode == SplitTunnelMode.BLOCKLIST) {
            val r = withTimeoutOrNull(SETTINGS_READ_TIMEOUT_MS) {
                runCatching { deps.splitTunnelRulesProvider.blocklistPackages() }.getOrNull()
            }
            if (r == null) PersistentLoggers.warn(TAG, "blocklist read timeout — fallback emptySet")
            r ?: emptySet()
        } else {
            emptySet()
        }
        return SplitTunnelConfig(mode = mode, allowlist = allowlist, blocklist = blocklist)
    }

    private suspend fun awaitEngineReady(engineId: EngineId): Boolean {
        val plugin = deps.enginePlugins.firstOrNull { it.id == engineId } ?: return true
        return when (val result = plugin.awaitReady()) {
            EnginePlugin.ReadyResult.Ready -> true
            is EnginePlugin.ReadyResult.Timeout -> {
                PersistentLoggers.warn(
                    TAG,
                    "awaitReady timeout for $engineId: ${result.reason} → engineFailure (fast-fail)",
                )
                false
            }
        }
    }

    private fun buildEngineConfig(
        engineId: EngineId,
        settings: SettingsModel?,
        trafficMode: TrafficMode = TrafficMode.TUN,
    ): EngineConfig? {
        val plugin = deps.enginePlugins.firstOrNull { it.id == engineId } ?: return null
        return if (trafficMode == TrafficMode.PROXY) {
            plugin.buildProxyConfig(settings)
        } else {
            plugin.buildManualConfig(settings)
        }
    }

    private fun resolveTargetForUi(manualEngine: EngineId?, settings: SettingsModel?): EngineId? = manualEngine
        ?: settings?.engineAutoPriority?.firstOrNull()
        ?: deps.enginePlugins.firstOrNull()?.id

    private fun autoCandidates(settings: SettingsModel?, trafficMode: TrafficMode): List<Pair<EngineId, EngineConfig>> {
        val priority = settings?.engineAutoPriority ?: SettingsModel.DEFAULT_ENGINE_AUTO_PRIORITY
        return priority.mapNotNull { id ->
            if (!engineAllowedForTrafficMode(id, trafficMode)) return@mapNotNull null
            val cfg = buildEngineConfig(id, settings, trafficMode) ?: return@mapNotNull null
            id to cfg
        }
    }

    private suspend fun autoCandidatesWithPreflight(
        settings: SettingsModel?,
        trafficMode: TrafficMode,
        skipIds: Set<EngineId> = emptySet(),
    ): List<Pair<EngineId, EngineConfig>> {
        val candidates = autoCandidates(settings, trafficMode).filterNot { it.first in skipIds }
        if (candidates.isEmpty()) return emptyList()
        val accepted = mutableListOf<Pair<EngineId, EngineConfig>>()
        val protector = SocketProtector { _ -> true }
        for ((id, cfg) in candidates) {
            val plugin = deps.enginePlugins.firstOrNull { it.id == id }
            val preflight = plugin?.preflight()
            if (preflight == null) {
                Log.i(TAG, "auto-mode preflight skip (null) engine=$id")
                accepted += id to cfg
                continue
            }
            deps.tunnelController.onProbing(id)
            val result = withTimeoutOrNull(PREFLIGHT_HARD_TIMEOUT_MS) {
                runCatching { preflight.probe(protector) }
                    .getOrElse {
                        EnginePreflight.Result.Fail("preflight threw: ${it.message}")
                    }
            } ?: EnginePreflight.Result.Fail("preflight hard-timeout ${PREFLIGHT_HARD_TIMEOUT_MS}ms")
            when (result) {
                is EnginePreflight.Result.Ok -> {
                    Log.i(TAG, "auto-mode preflight OK engine=$id")
                    accepted += id to cfg
                }
                is EnginePreflight.Result.Fail -> {
                    PersistentLoggers.warn(TAG, "auto-mode preflight FAIL engine=$id reason=${result.reason}")
                }
            }
        }
        return accepted
    }

    private fun engineAllowedForTrafficMode(engineId: EngineId, trafficMode: TrafficMode): Boolean {
        if (trafficMode == TrafficMode.TUN) return true
        return deps.enginePlugins.firstOrNull { it.id == engineId }
            ?.capabilities
            ?.providesLocalSocksWithoutUpstream == true
    }

    private suspend fun establishTunForEngine(
        engineId: EngineId,
        splitConfig: SplitTunnelConfig,
        ipv6Enabled: Boolean,
    ): ParcelFileDescriptor? {
        val plugin = deps.enginePlugins.firstOrNull { it.id == engineId } ?: return null
        val spec = plugin.tunSpec() ?: return null
        val builder = deps.tunBuilderHelper.applyEngineTunSpec(spec, ipv6Enabled)
        TunBuilderConfigurator(packageName).apply(builder, splitConfig, excludeSelf = true)
        PersistentLoggers.debug(
            TAG,
            "engineTUN params: engine=$engineId mode=${splitConfig.mode} " +
                "blocklist=${splitConfig.blocklist.size}[${splitConfig.blocklist.joinToString(",")}] " +
                "allowlist=${splitConfig.allowlist.size} " +
                "allowFamilyV4=${spec.allowFamilyV4} allowFamilyV6=${spec.allowFamilyV6} " +
                "routeAllV4=${spec.routeAllV4} routeAllV6=${spec.routeAllV6} " +
                "dns=${spec.dnsServers} ipv6Enabled=$ipv6Enabled",
        )
        val before = TunInterfaceStats.snapshotTunInterfaces()
        val pfd = try {
            builder.establish()
        } catch (t: Throwable) {
            PersistentLoggers.error(TAG, "engine TUN establish threw: ${t.message}")
            deps.tunnelController.onEngineDied(engineId, "VPN slot занят — выключите другой VPN")
            stopVpnRequest()
            return null
        }
        if (pfd == null) {
            PersistentLoggers.error(
                TAG,
                "engine TUN establish returned null — VPN slot занят другим приложением",
            )
            deps.tunnelController.onEngineDied(engineId, "VPN slot занят — выключите другой VPN")
            stopVpnRequest()
            return null
        }
        state.tunFdRef.set(pfd)
        captureTunIfaceName(before)
        val iface = state.tunIfaceNameRef.get()
        Log.i(TAG, "engine TUN established fd=${pfd.fd} engineId=$engineId mtu=${spec.mtu} iface=$iface")
        return pfd
    }

    private fun captureTunIfaceName(before: Set<String>) {
        val after = TunInterfaceStats.snapshotTunInterfaces()
        val picked = TunInterfaceStats.pickNewTunInterface(before, after)
        state.tunIfaceNameRef.set(picked)
        if (picked == null) {
            Log.d(TAG, "tun interface discovery failed — stats logger будет получать null")
        }
    }

    private fun establishTun(
        splitConfig: SplitTunnelConfig,
        ipv6: Boolean,
        customDns: List<String>,
    ): ParcelFileDescriptor? {
        val before = TunInterfaceStats.snapshotTunInterfaces()
        val fd = try {
            deps.tunBuilderHelper.buildTunBuilder(
                splitConfig = splitConfig,
                ipv6Enabled = ipv6,
                customDnsServers = customDns,
            ).establish()
        } catch (t: Throwable) {
            PersistentLoggers.error(TAG, "establish threw: ${t.message}")
            stopVpnRequest()
            return null
        }
        if (fd == null) {
            PersistentLoggers.error(
                TAG,
                "establish returned null — VPN slot занят другим приложением (другой VPN active)",
            )
            stopVpnRequest()
            return null
        }
        state.tunFdRef.set(fd)
        captureTunIfaceName(before)
        Log.i(TAG, "TUN established fd=${fd.fd} iface=${state.tunIfaceNameRef.get()}")
        return fd
    }

    private suspend fun startChain(
        engineId: EngineId,
        config: EngineConfig,
        notifyFailure: Boolean = true,
    ): ChainResult.Success? {
        val chainResult = withTimeoutOrNull(CHAIN_START_TIMEOUT_MS) {
            try {
                deps.chainOrchestrator.start(listOf(ChainStep(engineId, config)))
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                PersistentLoggers.error(TAG, "chain.start threw: ${t.message}")
                null
            }
        }
        Log.i(TAG, "chain result=$chainResult engineId=$engineId")
        if (chainResult !is ChainResult.Success) {
            reportEngineFailure(engineId, chainResult?.toString() ?: "timeout", notifyFailure)
            return null
        }
        deps.tunnelController.onConnecting(engineId)
        return chainResult
    }

    private suspend fun routeTrafficForEngine(
        engineId: EngineId,
        fd: ParcelFileDescriptor,
        socksPort: Int,
        notifyFailure: Boolean = true,
    ): Boolean {
        val engine = deps.enginePlugins.firstOrNull { it.id == engineId }
        if (engine is TunFdAcceptor) {
            val rawDupFd = fd.dup().detachFd()
            val result = try {
                engine.attachTun(rawDupFd)
            } catch (t: Throwable) {
                runCatching { ParcelFileDescriptor.adoptFd(rawDupFd).close() }
                runCatching { state.tunFdRef.getAndSet(null)?.close() }
                PersistentLoggers.error(TAG, "attachTun threw, fd closed: ${t.message}")
                runCatching { deps.chainOrchestrator.stop() }
                reportEngineFailure(engineId, "attachTun threw: ${t.message}", notifyFailure)
                return false
            }
            return when (result) {
                TunAttachResult.Success -> true
                is TunAttachResult.Failure -> {
                    runCatching { ParcelFileDescriptor.adoptFd(rawDupFd).close() }
                    runCatching { state.tunFdRef.getAndSet(null)?.close() }
                    PersistentLoggers.error(TAG, "attachTun failed: ${result.reason}")
                    runCatching { deps.chainOrchestrator.stop() }
                    reportEngineFailure(engineId, "attachTun: ${result.reason}", notifyFailure)
                    false
                }
            }
        }
        return startNativeTunnel(engineId, fd, socksPort, notifyFailure)
    }

    private suspend fun startNativeTunnel(
        engineId: EngineId,
        fd: ParcelFileDescriptor,
        socksPort: Int,
        notifyFailure: Boolean = true,
    ): Boolean {
        val code = try {
            deps.tunnelGateway.start(
                HevTunnelConfig(tunPfd = fd, socksAddress = "127.0.0.1", socksPort = socksPort),
            )
        } catch (t: Throwable) {
            PersistentLoggers.error(TAG, "tunnelGateway.start threw: ${t.message}")
            runCatching { deps.chainOrchestrator.stop() }
            reportEngineFailure(engineId, "tunnel threw: ${t.message}", notifyFailure)
            return false
        }
        if (code != 0) {
            PersistentLoggers.error(TAG, "tunnel start failed code=$code")
            runCatching { deps.chainOrchestrator.stop() }
            reportEngineFailure(engineId, "tunnel code=$code", notifyFailure)
            return false
        }
        return true
    }

    private fun reportEngineFailure(
        engineId: EngineId,
        reason: String,
        notifyFailure: Boolean,
    ) {
        if (notifyFailure) {
            deps.engineWatchdog.handleEngineFailure(engineId, reason)
        } else {
            PersistentLoggers.warn(TAG, "auto-mode candidate failed engine=$engineId reason=$reason")
        }
    }

    private suspend fun resetAfterAutoCandidateFailure(engineId: EngineId) {
        PersistentLoggers.warn(TAG, "auto-mode: retry next candidate after $engineId failure")
        runCatching { state.tunFdRef.getAndSet(null)?.close() }
        runCatching { deps.chainOrchestrator.stop() }
        deps.tunnelController.onDisconnecting()
        deps.tunnelController.reset()
    }

    companion object {
        private const val TAG = "StartSequenceCoordinator"
        const val SETTINGS_READ_TIMEOUT_MS = 1_500L
        const val CHAIN_START_TIMEOUT_MS = 30_000L
        const val PREFLIGHT_HARD_TIMEOUT_MS = 7_000L
    }
}
