package ru.ozero.engineurnetwork

import android.app.Application
import android.util.Log
import com.bringyour.sdk.ConnectLocation
import com.bringyour.sdk.ConnectViewController
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.IoLoop
import com.bringyour.sdk.IoLoopDoneCallback
import com.bringyour.sdk.LocalState
import com.bringyour.sdk.LocationsViewController
import com.bringyour.sdk.PerformanceProfile
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.SubscriptionBalanceCallback
import com.bringyour.sdk.WalletViewController
import com.bringyour.sdk.WindowSizeSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ru.ozero.enginescore.PersistentLoggers
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

class SdkLocationToken(val sdk: ConnectLocation) : UrnetworkSdkBridge.LocationToken {
    override val countryCode: String? = runCatching { sdk.countryCode }.getOrNull()
    override val region: String? = runCatching { sdk.region.takeIf { it.isNotEmpty() } }.getOrNull()
    override val city: String? = runCatching { sdk.city.takeIf { it.isNotEmpty() } }.getOrNull()
}

@Suppress("TooManyFunctions", "LargeClass")
class RealUrnetworkSdkBridge(
    private val app: Application,
    private val appVersion: String = DEFAULT_APP_VERSION,
    private val onIoLoopDied: (String) -> Unit = {},
) : UrnetworkSdkBridge {

    private val deviceRef = AtomicReference<DeviceLocal?>(null)
    private val ioLoopRef = AtomicReference<IoLoop?>(null)
    private val connectVcRef = AtomicReference<ConnectViewController?>(null)
    private val walletVcRef = AtomicReference<WalletViewController?>(null)
    private val unpaidBytesRef = AtomicReference(0L)
    private val running = AtomicBoolean(false)
    private val preferredLocationRef = AtomicReference<UrnetworkLocationSelection?>(null)
    private val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val contractStatusListener = UrnetworkContractStatusListener()
    private val payoutWalletSetup = UrnetworkPayoutWalletSetup()
    private val preferredLocationConnector = UrnetworkPreferredLocationConnector(bridgeScope)
    private val apiHelper = UrnetworkApiHelper(deviceRef, running)
    private val subscriptionBalanceRef =
        AtomicReference<UrnetworkSdkBridge.SubscriptionBalanceSnapshot?>(null)
    private val lifecycleMutex = Mutex()
    private val startJobRef = AtomicReference<Job?>(null)
    private val attachJobRef = AtomicReference<Job?>(null)

    override suspend fun start(
        walletAddress: String,
        apiUrl: String,
        connectUrl: String,
        byClientJwt: String,
    ): UrnetworkSdkBridge.StartResult {
        if (byClientJwt.isBlank()) {
            return UrnetworkSdkBridge.StartResult.Failed("byClientJwt is blank")
        }
        val myJob = coroutineContext[Job]
            ?: return UrnetworkSdkBridge.StartResult.Failed("start called outside coroutine context")
        return lifecycleMutex.withLock {
            if (running.get()) {
                Log.i(TAG, "start: already running — idempotent success")
                return@withLock UrnetworkSdkBridge.StartResult.Success
            }
            startJobRef.set(myJob)
            try {
                withTimeoutOrNull(SDK_INIT_TIMEOUT_MS) {
                    withContext(Dispatchers.Main.immediate) {
                        runStartOnMain(byClientJwt, walletAddress)
                    }
                } ?: run {
                    PersistentLoggers.error(TAG, "SDK init timed out after ${SDK_INIT_TIMEOUT_MS}ms")
                    cleanupOnFailure()
                    UrnetworkSdkBridge.StartResult.Failed("URnetwork SDK init timeout (30s)")
                }
            } finally {
                startJobRef.compareAndSet(myJob, null)
            }
        }
    }

    private suspend fun runStartOnMain(
        byClientJwt: String,
        walletAddress: String,
    ): UrnetworkSdkBridge.StartResult {
        val existingDevice = deviceRef.get()
        val device: DeviceLocal = if (existingDevice != null) {
            Log.i(TAG, "start: reusing device from initDeviceForLocations")
            existingDevice
        } else {
            val space = try {
                UrnetworkRuntime.ensure(app)
            } catch (t: Throwable) {
                PersistentLoggers.error(TAG, "runtime ensure failed: ${t.message}")
                cleanupOnFailure()
                return UrnetworkSdkBridge.StartResult.Failed("runtime ensure failed: ${t.message}")
            }
            val localState = space.asyncLocalState?.localState
            if (localState == null) {
                PersistentLoggers.error(TAG, "asyncLocalState.localState is null — runtime not ready")
                cleanupOnFailure()
                return UrnetworkSdkBridge.StartResult.Failed("URnetwork localState not ready")
            }
            runCatching { localState.byClientJwt = byClientJwt }
                .onFailure { PersistentLoggers.warn(TAG, "set localState.byClientJwt threw: ${it.message}") }
            val instanceId = runCatching { localState.instanceId }.getOrNull()
            val d = try {
                Sdk.newDeviceLocalWithDefaults(
                    space,
                    byClientJwt,
                    UrnetworkDefaults.DEVICE_DESCRIPTION,
                    UrnetworkDefaults.DEVICE_SPEC,
                    appVersion,
                    instanceId,
                    false,
                )
            } catch (t: Throwable) {
                PersistentLoggers.error(TAG, "newDeviceLocalWithDefaults threw: ${t.message}")
                cleanupOnFailure()
                return UrnetworkSdkBridge.StartResult.Failed("DeviceLocal init failed: ${t.message}")
            }
            runCatching {
                val keys = localState.provideSecretKeys
                if (keys != null) d.loadProvideSecretKeys(keys) else d.initProvideSecretKeys()
            }.onFailure { PersistentLoggers.warn(TAG, "provideSecretKeys init threw: ${it.message}") }
            applyDeviceFields(d, localState)
            Log.i(TAG, "runStartOnMain: device created — 12 fields applied (паритет с initDeviceForLocations)")
            deviceRef.set(d)
            d
        }

        val cv = runCatching { device.openConnectViewController() }.getOrElse {
            PersistentLoggers.warn(TAG, "openConnectViewController threw: ${it.message}")
            null
        }
        if (cv != null) {
            connectVcRef.set(cv)
            Log.i(TAG, "ConnectViewController opened — locations available")
        } else {
            PersistentLoggers.error(TAG, "ConnectViewController is null — P2P connection unavailable")
        }

        val walletVcStarted = runCatching {
            val walletVc = device.openWalletViewController()
            walletVcRef.set(walletVc)
            walletVc?.addUnpaidByteCountListener { ubc -> unpaidBytesRef.set(ubc) }
            walletVc?.start()
            walletVc?.fetchTransferStats()
            Log.i(TAG, "WalletViewController opened — provider stats listener attached")
            walletVc
        }.onFailure {
            PersistentLoggers.warn(TAG, "WalletViewController init threw: ${it.message}")
        }.getOrNull()
        if (walletVcStarted != null) {
            payoutWalletSetup.configure(walletVcStarted, walletAddress)
        }
        contractStatusListener.attach(device)
        running.set(true)
        PersistentLoggers.info(TAG, "device created — awaiting attachTun(fd) before tunnelStarted")
        return UrnetworkSdkBridge.StartResult.Success
    }

    override suspend fun stop() {
        running.set(false)
        startJobRef.getAndSet(null)?.let { active ->
            if (active.isActive) {
                PersistentLoggers.warn(TAG, "stop: cancelling in-flight start() to release lifecycleMutex")
                active.cancel()
            }
        }
        attachJobRef.getAndSet(null)?.let { active ->
            if (active.isActive) {
                PersistentLoggers.warn(TAG, "stop: cancelling in-flight attachTun() to release lifecycleMutex")
                active.cancel()
            }
        }
        lifecycleMutex.withLock {
            stopUnderLock()
        }
    }

    private suspend fun stopUnderLock() {
        runCatching { bridgeScope.coroutineContext.cancelChildren() }
        contractStatusListener.detach()
        val completed = withTimeoutOrNull(STOP_TIMEOUT_MS) {
            withContext(Dispatchers.Main.immediate) {
                walletVcRef.getAndSet(null)?.also { vc ->
                    runCatching { vc.close() }
                        .onFailure { PersistentLoggers.warn(TAG, "walletVc.close threw: ${it.message}") }
                }
                connectVcRef.getAndSet(null)?.also { vc ->
                    runCatching { vc.disconnect() }
                        .onFailure { PersistentLoggers.warn(TAG, "connectVc.disconnect threw: ${it.message}") }
                    runCatching { vc.close() }
                        .onFailure { PersistentLoggers.warn(TAG, "connectVc.close threw: ${it.message}") }
                }
                val hadLoop = ioLoopRef.getAndSet(null)?.also { loop ->
                    runCatching { loop.close() }
                        .onFailure { PersistentLoggers.warn(TAG, "ioLoop.close threw: ${it.message}") }
                } != null
                if (!hadLoop) {
                    deviceRef.getAndSet(null)?.also { device -> closeDevice(device) }
                } else {
                    deviceRef.set(null)
                }
            }
        }
        if (completed == null) {
            PersistentLoggers.warn(TAG, "stop timed out after ${STOP_TIMEOUT_MS}ms — refs cleared")
        }
        val releaseOutcome = runCatching {
            withTimeoutOrNull(RUNTIME_RELEASE_TIMEOUT_MS) { UrnetworkRuntime.release() }
        }
        val released = when {
            releaseOutcome.isFailure -> {
                PersistentLoggers.warn(
                    TAG,
                    "runtime release threw: ${releaseOutcome.exceptionOrNull()?.message} — " +
                        "Go-runtime может удерживать UDP/file handles, URnetwork-app может крашиться",
                )
                false
            }
            releaseOutcome.getOrNull() == null -> {
                PersistentLoggers.warn(
                    TAG,
                    "runtime release timed out after ${RUNTIME_RELEASE_TIMEOUT_MS}ms — " +
                        "Sdk.freeMemory завис, ресурсы Go-runtime могут утечь",
                )
                false
            }
            else -> true
        }
        if (released) {
            Log.i(TAG, "stop complete — runtime released")
        } else {
            PersistentLoggers.warn(TAG, "stop complete — runtime release НЕ подтверждён")
        }
    }

    private fun closeDevice(device: DeviceLocal) {
        runCatching { device.setTunnelStarted(false) }
            .onFailure { PersistentLoggers.warn(TAG, "setTunnelStarted(false) threw: ${it.message}") }
        runCatching { device.close() }
            .onFailure { PersistentLoggers.warn(TAG, "device.close threw: ${it.message}") }
    }

    override fun isRunning(): Boolean = running.get()

    override fun connectTo(location: UrnetworkSdkBridge.LocationToken) {
        if (!running.get()) {
            PersistentLoggers.warn(TAG, "connectTo skipped — bridge not running")
            return
        }
        val sdkLoc = (location as? SdkLocationToken)?.sdk ?: return
        runCatching { connectVcRef.get()?.connect(sdkLoc) }
            .onFailure { PersistentLoggers.warn(TAG, "connect threw: ${it.message}") }
    }

    override fun connectBestAvailable() {
        if (!running.get()) {
            PersistentLoggers.warn(TAG, "connectBestAvailable skipped — bridge not running")
            return
        }
        runCatching { connectVcRef.get()?.connectBestAvailable() }
            .onFailure { PersistentLoggers.warn(TAG, "connectBestAvailable threw: ${it.message}") }
    }

    override fun selectedLocation(): UrnetworkSdkBridge.LocationToken? {
        if (!running.get()) return null
        val loc = runCatching { connectVcRef.get()?.selectedLocation }.getOrNull() ?: return null
        return SdkLocationToken(loc)
    }

    override fun selectedLocationInfo(): UrnetworkSdkBridge.LocationInfo? {
        if (!running.get()) return null
        val loc = runCatching { connectVcRef.get()?.selectedLocation }.getOrNull() ?: return null
        val country = runCatching { loc.country }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: runCatching { loc.name }.getOrNull()?.takeIf { it.isNotBlank() }
        val code = runCatching { loc.countryCode?.trim()?.uppercase() }.getOrNull()
            ?.takeIf { it.length == 2 }
        val name = runCatching { loc.name }.getOrNull()?.takeIf { it.isNotBlank() }
        return UrnetworkSdkBridge.LocationInfo(country = country, countryCode = code, name = name)
    }

    override fun setPreferredLocation(selection: UrnetworkLocationSelection?) {
        val cleaned = selection?.normalized()
        preferredLocationRef.set(cleaned)
        Log.i(TAG, "preferredLocation set to ${cleaned?.summary() ?: "<auto>"}")
    }

    override fun contractStatus(): StateFlow<UrnetworkSdkBridge.ContractStatusSnapshot> =
        contractStatusListener.status

    override fun openLocationsViewController(): LocationsViewController? {
        if (!running.get() && deviceRef.get() == null) return null
        return runCatching { deviceRef.get()?.openLocationsViewController() }.getOrElse {
            PersistentLoggers.warn(TAG, "openLocationsViewController threw: ${it.message}")
            null
        }
    }

    override fun isDeviceAvailable(): Boolean = deviceRef.get() != null

    override suspend fun initDeviceForLocations(byClientJwt: String, walletAddress: String): Boolean {
        if (byClientJwt.isBlank()) return false
        if (deviceRef.get() != null) return true
        return lifecycleMutex.withLock {
            if (deviceRef.get() != null) return@withLock true
            withContext(Dispatchers.Main.immediate) {
                ensureDeviceOnMain(byClientJwt)
            }
        }
    }

    private suspend fun ensureDeviceOnMain(byClientJwt: String): Boolean {
        val space = runCatching { UrnetworkRuntime.ensure(app) }.getOrNull() ?: run {
            PersistentLoggers.warn(TAG, "initDeviceForLocations: runtime ensure failed")
            return false
        }
        val localState = space.asyncLocalState?.localState ?: run {
            PersistentLoggers.warn(TAG, "initDeviceForLocations: localState null")
            return false
        }
        runCatching { localState.byClientJwt = byClientJwt }
        val instanceId = runCatching { localState.instanceId }.getOrNull()
        val device = runCatching {
            Sdk.newDeviceLocalWithDefaults(
                space,
                byClientJwt,
                UrnetworkDefaults.DEVICE_DESCRIPTION,
                UrnetworkDefaults.DEVICE_SPEC,
                appVersion,
                instanceId,
                false,
            )
        }.getOrNull() ?: run {
            PersistentLoggers.warn(TAG, "initDeviceForLocations: newDeviceLocalWithDefaults returned null")
            return false
        }
        runCatching {
            val keys = localState.provideSecretKeys
            if (keys != null) device.loadProvideSecretKeys(keys) else device.initProvideSecretKeys()
        }
        applyDeviceFields(device, localState)
        deviceRef.set(device)
        Log.i(TAG, "initDeviceForLocations: device ready for location browse — 12 fields applied")
        return true
    }

    private fun applyDeviceFields(device: DeviceLocal, localState: LocalState) {
        val rawControlMode = runCatching { localState.provideControlMode }.getOrNull().orEmpty()
        val normalizedControlMode = UrnetworkProvideControlMode.fromRaw(rawControlMode).rawValue
        val rawProvideMode = runCatching { localState.provideMode }.getOrDefault(Sdk.ProvideModeNone)
        val effectiveProvideMode = if (normalizedControlMode == UrnetworkProvideControlMode.ALWAYS.rawValue) {
            Sdk.ProvideModePublic
        } else {
            rawProvideMode
        }
        runCatching { device.providePaused = true }
            .onFailure { PersistentLoggers.warn(TAG, "providePaused threw: ${it.message}") }
        runCatching { device.routeLocal = localState.routeLocal }
        runCatching { device.provideMode = effectiveProvideMode }
        runCatching { device.connectLocation = localState.connectLocation }
        runCatching { device.defaultLocation = localState.defaultLocation }
        runCatching { device.canShowRatingDialog = localState.canShowRatingDialog }
        runCatching { device.provideControlMode = normalizedControlMode }
        runCatching { device.vpnInterfaceWhileOffline = localState.vpnInterfaceWhileOffline }
        runCatching { device.canRefer = localState.canRefer }
        runCatching { device.allowForeground = localState.allowForeground }
        runCatching { device.provideNetworkMode = localState.provideNetworkMode }
        runCatching { device.canPromptIntroFunnel = localState.canPromptIntroFunnel }
        runCatching { device.performanceProfile = localState.performanceProfile }
    }

    private inline fun guardedRun(label: String, block: () -> Unit) {
        if (!running.get()) {
            PersistentLoggers.warn(TAG, "$label skipped — bridge not running")
            return
        }
        runCatching(block).onFailure { PersistentLoggers.warn(TAG, "$label threw: ${it.message}") }
    }

    override fun setProvidePaused(paused: Boolean) = guardedRun("setProvidePaused($paused)") {
        deviceRef.get()?.providePaused = paused
        Log.i(TAG, "setProvidePaused paused=$paused OK")
    }

    override fun isProvidePaused(): Boolean {
        if (!running.get()) return true
        return runCatching { deviceRef.get()?.providePaused ?: true }.getOrDefault(true)
    }

    override fun setProvideControlMode(mode: UrnetworkProvideControlMode) =
        guardedRun("setProvideControlMode(${mode.rawValue})") {
            deviceRef.get()?.provideControlMode = mode.rawValue
            Log.i(TAG, "setProvideControlMode mode=${mode.rawValue} OK")
        }

    override fun setProvideNetworkMode(mode: UrnetworkProvideNetworkMode) =
        guardedRun("setProvideNetworkMode(${mode.rawValue})") {
            deviceRef.get()?.provideNetworkMode = mode.rawValue
            Log.i(TAG, "setProvideNetworkMode mode=${mode.rawValue} OK")
        }

    override fun applyPerformanceProfile(
        windowType: UrnetworkWindowType,
        fixedIpSize: Boolean,
        allowDirect: Boolean,
    ) {
        if (!running.get()) {
            PersistentLoggers.warn(TAG, "applyPerformanceProfile skipped — bridge not running")
            return
        }
        if (windowType == UrnetworkWindowType.AUTO && allowDirect) {
            Log.i(TAG, "applyPerformanceProfile skip — AUTO+allowDirect uses SDK defaults")
            return
        }
        val device = deviceRef.get() ?: return
        runCatching {
            val profile = PerformanceProfile()
            profile.windowType = when (windowType) {
                UrnetworkWindowType.QUALITY -> Sdk.WindowTypeQuality
                UrnetworkWindowType.SPEED -> Sdk.WindowTypeSpeed
                UrnetworkWindowType.AUTO -> Sdk.WindowTypeQuality
            }
            val sizes = WindowSizeSettings()
            sizes.windowSizeMin = if (fixedIpSize) 1 else 2
            sizes.windowSizeMax = if (fixedIpSize) 1L else WINDOW_SIZE_MAX_EXPERIMENTAL
            profile.windowSize = sizes
            profile.allowDirect = allowDirect
            device.performanceProfile = profile
            Log.i(
                TAG,
                "applyPerformanceProfile windowType=${windowType.rawValue} fixedIp=$fixedIpSize " +
                    "allowDirect=$allowDirect OK",
            )
        }.onFailure {
            PersistentLoggers.warn(TAG, "applyPerformanceProfile threw: ${it.message}")
        }
    }

    override fun peerCount(): Int {
        if (!running.get()) return 0
        return runCatching { connectVcRef.get()?.grid?.windowCurrentSize ?: 0 }.getOrDefault(0)
    }

    override fun unpaidByteCount(): Long = unpaidBytesRef.get()

    override fun fetchTransferStats() {
        if (!running.get()) return
        runCatching { walletVcRef.get()?.fetchTransferStats() }
            .onFailure { PersistentLoggers.warn(TAG, "fetchTransferStats threw: ${it.message}") }
    }

    @Suppress("LongMethod", "ReturnCount")
    override suspend fun fetchSubscriptionBalance(): UrnetworkSdkBridge.SubscriptionBalanceSnapshot? {
        if (!running.get()) return subscriptionBalanceRef.get()
        val device = deviceRef.get() ?: return null
        val api = runCatching { device.api }.getOrNull() ?: run {
            PersistentLoggers.warn(TAG, "device.api is null — cannot fetch subscription balance")
            return null
        }
        val cached = subscriptionBalanceRef.get()
        val snapshot = withTimeoutOrNull(SUBSCRIPTION_BALANCE_TIMEOUT_MS) {
            suspendCancellableCoroutine<UrnetworkSdkBridge.SubscriptionBalanceSnapshot?> { cont ->
                val resumed = AtomicBoolean(false)
                val callback = SubscriptionBalanceCallback { result, err ->
                    if (!resumed.compareAndSet(false, true)) return@SubscriptionBalanceCallback
                    if (err != null || result == null) {
                        PersistentLoggers.warn(TAG, "subscriptionBalance err=${err?.message}")
                        cont.resume(null)
                        return@SubscriptionBalanceCallback
                    }
                    val balance = runCatching { result.balanceByteCount }.getOrDefault(0L)
                    val pending = runCatching { result.openTransferByteCount }.getOrDefault(0L)
                    val startBalance = runCatching { result.startBalanceByteCount }.getOrDefault(0L)
                    val sub = runCatching { result.currentSubscription }.getOrNull()
                    val plan = runCatching { sub?.plan }.getOrNull()
                    val store = runCatching { sub?.store }.getOrNull()
                    val used = startBalance - balance - pending
                    val clientId = runCatching { device.clientId?.toString() }.getOrNull()
                    val activeLocation = runCatching { device.connectLocation?.name }.getOrNull()
                    val providePaused = runCatching { device.providePaused }.getOrNull()
                    val balanceGb = balance / 1_000_000_000.0
                    val startGb = startBalance / 1_000_000_000.0
                    val ratio = if (startBalance > 0) balance.toDouble() / startBalance else 0.0
                    Log.i(
                        TAG,
                        "subscriptionBalance SDK raw: start=$startBalance(${"%.1f".format(startGb)}GB) " +
                            "balance=$balance(${"%.1f".format(balanceGb)}GB) pending=$pending used=$used " +
                            "plan=$plan store=$store clientId=$clientId loc=$activeLocation " +
                            "providePaused=$providePaused balance/start=${"%.2f".format(ratio)}",
                    )
                    if (startBalance > DOUBLE_QUOTA_THRESHOLD_BYTES) {
                        PersistentLoggers.warn(
                            TAG,
                            "subscriptionBalance startBalance=${"%.1f".format(startGb)}GB > " +
                                "${DOUBLE_QUOTA_THRESHOLD_BYTES / 1_000_000_000L}GB",
                        )
                    }
                    cont.resume(
                        UrnetworkSdkBridge.SubscriptionBalanceSnapshot(
                            balanceBytes = balance,
                            pendingBytes = pending,
                            startBalanceBytes = startBalance,
                            usedBytes = used,
                            plan = plan,
                            store = store,
                        ),
                    )
                }
                runCatching { api.subscriptionBalance(callback) }.onFailure { t ->
                    if (resumed.compareAndSet(false, true)) {
                        PersistentLoggers.warn(TAG, "subscriptionBalance threw: ${t.message}")
                        cont.resume(null)
                    }
                }
            }
        }
        return when {
            snapshot != null -> {
                subscriptionBalanceRef.set(snapshot)
                snapshot
            }
            else -> {
                PersistentLoggers.warn(TAG, "subscriptionBalance timeout/null — using cached=${cached != null}")
                cached
            }
        }
    }
    override suspend fun fetchAccountPoints() = apiHelper.fetchAccountPoints()
    override suspend fun fetchNetworkReliability() = apiHelper.fetchNetworkReliability()
    override suspend fun fetchReferralCount() = apiHelper.fetchReferralCount()

    override suspend fun attachTun(tunFd: Int): UrnetworkSdkBridge.AttachResult {
        if (tunFd < 0) {
            return UrnetworkSdkBridge.AttachResult.Failed("invalid fd=$tunFd")
        }
        val myJob = coroutineContext[Job]
            ?: return UrnetworkSdkBridge.AttachResult.Failed("attachTun called outside coroutine context")
        attachJobRef.set(myJob)
        return try {
            lifecycleMutex.withLock { attachTunUnderLock(tunFd) }
        } finally {
            attachJobRef.compareAndSet(myJob, null)
        }
    }

    private suspend fun attachTunUnderLock(tunFd: Int): UrnetworkSdkBridge.AttachResult {
        if (!running.get()) {
            return UrnetworkSdkBridge.AttachResult.Failed("bridge stopped — attachTun aborted")
        }
        val device = deviceRef.get()
            ?: return UrnetworkSdkBridge.AttachResult.Failed("DeviceLocal not initialised — call start() first")
        if (ioLoopRef.get() != null) {
            return UrnetworkSdkBridge.AttachResult.Failed("IoLoop already attached")
        }
        return withContext(Dispatchers.Main.immediate) {
            try {
                val capturedDevice = device
                val callback = IoLoopDoneCallback {
                    PersistentLoggers.info(TAG, "IoLoop done — tunnel ended")
                    val wasRunning = running.compareAndSet(true, false)
                    closeDevice(capturedDevice)
                    if (wasRunning) {
                        PersistentLoggers.error(
                            TAG,
                            "IoLoop ended unexpectedly — Go runtime crash в URnetwork SDK",
                        )
                        runCatching { onIoLoopDied("io-loop-ended") }
                    }
                }
                val loop = Sdk.newIoLoop(capturedDevice, tunFd, callback)
                ioLoopRef.set(loop)
                runCatching { device.setTunnelStarted(true) }
                    .onFailure { PersistentLoggers.warn(TAG, "setTunnelStarted(true) threw: ${it.message}") }
                val cv = connectVcRef.get()
                if (cv != null) {
                    val preferred = preferredLocationRef.get()
                    if (preferred != null) {
                        Log.i(TAG, "IoLoop fd=$tunFd tunnelStarted preferredLocation=${preferred.summary()}")
                        preferredLocationConnector.connect(preferred, capturedDevice, cv)
                    } else {
                        runCatching { cv.connectBestAvailable() }
                            .onFailure { PersistentLoggers.warn(TAG, "connectBestAvailable threw: ${it.message}") }
                        Log.i(TAG, "IoLoop fd=$tunFd tunnelStarted connectBestAvailable called")
                    }
                } else {
                    PersistentLoggers.error(TAG, "No ConnectViewController — P2P connection will not be established")
                }
                UrnetworkSdkBridge.AttachResult.Success
            } catch (t: Throwable) {
                PersistentLoggers.error(TAG, "newIoLoop threw: ${t.message}")
                UrnetworkSdkBridge.AttachResult.Failed("newIoLoop failed: ${t.message}")
            }
        }
    }

    private fun cleanupOnFailure() {
        deviceRef.getAndSet(null)?.also { runCatching { it.close() } }
    }

    private companion object {
        const val TAG = "RealUrnetworkSdkBridge"
        const val SDK_INIT_TIMEOUT_MS = 30_000L
        const val STOP_TIMEOUT_MS = 3_000L
        const val RUNTIME_RELEASE_TIMEOUT_MS = 2_000L
        const val SUBSCRIPTION_BALANCE_TIMEOUT_MS = 10_000L
        const val DEFAULT_APP_VERSION = "0.0.2"
        const val WINDOW_SIZE_MAX_EXPERIMENTAL = 6L
        const val DOUBLE_QUOTA_THRESHOLD_BYTES = 50_000_000_000L
    }
}
