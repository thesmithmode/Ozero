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
        val killswitch = settings?.killswitchEnabled ?: false
        killswitchSetter(killswitch)
        if (killswitch) {
            val startupIpv6 = settings?.ipv6Enabled ?: false
            val startupDns = settings?.customDnsServers.orEmpty()
            runCatching {
                deps.tunBuilderHelper.buildTunBuilder(
                    splitConfig = splitConfig,
                    ipv6Enabled = startupIpv6,
                    customDnsServers = startupDns,
                    applyUnderlying = false,
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
        val pick = if (manualEngine != null) {
            val cfg = buildEngineConfig(manualEngine, settings)
            if (cfg == null) null else manualEngine to cfg
        } else {
            pickAutoCandidateWithPreflight(settings)
        }
        if (pick == null) {
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
        val activeEngineId = pick.first
        val activeConfig = pick.second
        deps.tunnelController.onProbing(activeEngineId)
        if (state.stopping.get()) return
        val usesCustomTun = engineNeedsCustomTun(activeEngineId)
        val ipv6Enabled = settings?.ipv6Enabled ?: false
        val established = establishTunAndChain(
            activeEngineId = activeEngineId,
            activeConfig = activeConfig,
            splitConfig = splitConfig,
            ipv6Enabled = ipv6Enabled,
            customDns = settings?.customDnsServers.orEmpty(),
            usesCustomTun = usesCustomTun,
        ) ?: return
        val fd = established.first
        val chainResult = established.second
        if (!routeTrafficForEngine(activeEngineId, fd, chainResult.finalSocksPort)) return

        if (!awaitEngineReady(activeEngineId)) {
            runCatching { deps.chainOrchestrator.stop() }
            deps.engineWatchdog.handleEngineFailure(
                activeEngineId,
                "awaitReady fail — handshake/probe не подтверждён",
            )
            return
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
    ): Pair<ParcelFileDescriptor, ChainResult.Success>? {
        if (usesCustomTun) {
            val tun = establishTunForEngine(activeEngineId, splitConfig, ipv6Enabled) ?: return null
            state.lockdownStartupFdRef.getAndSet(null)?.runCatching { close() }
            if (state.stopping.get()) {
                runCatching { tun.close() }
                state.tunFdRef.compareAndSet(tun, null)
                return null
            }
            val chain = startChain(activeEngineId, activeConfig) ?: return null
            return tun to chain
        }
        val chain = startChain(activeEngineId, activeConfig) ?: return null
        val tun = establishTun(splitConfig, ipv6 = ipv6Enabled, customDns = customDns) ?: run {
            runCatching { deps.chainOrchestrator.stop() }
            deps.engineWatchdog.handleEngineFailure(
                activeEngineId,
                "establishTun fail после startChain — UI не должен застрять в Connecting",
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

    private fun buildEngineConfig(engineId: EngineId, settings: SettingsModel?): EngineConfig? =
        deps.enginePlugins.firstOrNull { it.id == engineId }?.buildManualConfig(settings)

    private fun resolveTargetForUi(manualEngine: EngineId?, settings: SettingsModel?): EngineId? = manualEngine
        ?: settings?.engineAutoPriority?.firstOrNull()
        ?: deps.enginePlugins.firstOrNull()?.id

    private fun autoCandidates(settings: SettingsModel?): List<Pair<EngineId, EngineConfig>> {
        val priority = settings?.engineAutoPriority ?: SettingsModel.DEFAULT_ENGINE_AUTO_PRIORITY
        return priority.mapNotNull { id ->
            val cfg = buildEngineConfig(id, settings) ?: return@mapNotNull null
            id to cfg
        }
    }

    private suspend fun pickAutoCandidateWithPreflight(
        settings: SettingsModel?,
    ): Pair<EngineId, EngineConfig>? {
        val candidates = autoCandidates(settings)
        if (candidates.isEmpty()) {
            PersistentLoggers.warn(TAG, "auto-mode: нет валидных кандидатов")
            return null
        }
        val protector = SocketProtector { _ -> true }
        for ((id, cfg) in candidates) {
            val plugin = deps.enginePlugins.firstOrNull { it.id == id }
            val preflight = plugin?.preflight()
            if (preflight == null) {
                Log.i(TAG, "auto-mode preflight skip (null) engine=$id — берём как кандидата")
                return id to cfg
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
                    return id to cfg
                }
                is EnginePreflight.Result.Fail -> {
                    PersistentLoggers.warn(TAG, "auto-mode preflight FAIL engine=$id reason=${result.reason}")
                }
            }
        }
        return null
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
        PersistentLoggers.info(
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

    private suspend fun startChain(engineId: EngineId, config: EngineConfig): ChainResult.Success? {
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
            deps.engineWatchdog.handleEngineFailure(engineId, chainResult?.toString() ?: "timeout")
            return null
        }
        deps.tunnelController.onConnecting(engineId)
        return chainResult
    }

    private suspend fun routeTrafficForEngine(
        engineId: EngineId,
        fd: ParcelFileDescriptor,
        socksPort: Int,
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
                deps.engineWatchdog.handleEngineFailure(engineId, "attachTun threw: ${t.message}")
                return false
            }
            return when (result) {
                TunAttachResult.Success -> true
                is TunAttachResult.Failure -> {
                    runCatching { ParcelFileDescriptor.adoptFd(rawDupFd).close() }
                    runCatching { state.tunFdRef.getAndSet(null)?.close() }
                    PersistentLoggers.error(TAG, "attachTun failed: ${result.reason}")
                    runCatching { deps.chainOrchestrator.stop() }
                    deps.engineWatchdog.handleEngineFailure(engineId, "attachTun: ${result.reason}")
                    false
                }
            }
        }
        return startNativeTunnel(engineId, fd, socksPort)
    }

    private suspend fun startNativeTunnel(
        engineId: EngineId,
        fd: ParcelFileDescriptor,
        socksPort: Int,
    ): Boolean {
        val code = try {
            deps.tunnelGateway.start(
                HevTunnelConfig(tunPfd = fd, socksAddress = "127.0.0.1", socksPort = socksPort),
            )
        } catch (t: Throwable) {
            PersistentLoggers.error(TAG, "tunnelGateway.start threw: ${t.message}")
            runCatching { deps.chainOrchestrator.stop() }
            stopVpnRequest()
            return false
        }
        if (code != 0) {
            PersistentLoggers.error(TAG, "tunnel start failed code=$code")
            runCatching { deps.chainOrchestrator.stop() }
            deps.engineWatchdog.handleEngineFailure(engineId, "tunnel code=$code")
            return false
        }
        return true
    }

    companion object {
        private const val TAG = "StartSequenceCoordinator"
        const val SETTINGS_READ_TIMEOUT_MS = 1_500L
        const val CHAIN_START_TIMEOUT_MS = 30_000L
        const val PREFLIGHT_HARD_TIMEOUT_MS = 7_000L
    }
}
