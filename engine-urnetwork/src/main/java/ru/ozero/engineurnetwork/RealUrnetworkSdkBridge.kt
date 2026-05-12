package ru.ozero.engineurnetwork

import android.app.Application
import android.util.Log
import com.bringyour.sdk.ConnectLocation
import com.bringyour.sdk.ConnectViewController
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.IoLoop
import com.bringyour.sdk.IoLoopDoneCallback
import com.bringyour.sdk.LocationsViewController
import com.bringyour.sdk.PerformanceProfile
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.WindowSizeSettings
import com.bringyour.sdk.SubscriptionBalanceCallback
import com.bringyour.sdk.WalletViewController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ru.ozero.enginescore.PersistentLoggers
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

class SdkLocationToken(val sdk: ConnectLocation) : UrnetworkSdkBridge.LocationToken {
    override val countryCode: String? = runCatching { sdk.countryCode }.getOrNull()
}

@Suppress("TooManyFunctions")
class RealUrnetworkSdkBridge(
    private val app: Application,
    private val appVersion: String = DEFAULT_APP_VERSION,
) : UrnetworkSdkBridge {

    private val deviceRef = AtomicReference<DeviceLocal?>(null)
    private val ioLoopRef = AtomicReference<IoLoop?>(null)
    private val connectVcRef = AtomicReference<ConnectViewController?>(null)
    private val walletVcRef = AtomicReference<WalletViewController?>(null)
    private val unpaidBytesRef = AtomicReference(0L)
    private val subscriptionBalanceRef =
        AtomicReference<UrnetworkSdkBridge.SubscriptionBalanceSnapshot?>(null)
    private val running = AtomicBoolean(false)
    private val preferredCountryRef = AtomicReference<String?>(null)
    private val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycleMutex = Mutex()

    override suspend fun start(
        walletAddress: String,
        apiUrl: String,
        connectUrl: String,
        byClientJwt: String,
    ): UrnetworkSdkBridge.StartResult {
        if (byClientJwt.isBlank()) {
            return UrnetworkSdkBridge.StartResult.Failed("byClientJwt is blank")
        }
        return lifecycleMutex.withLock {
            if (running.get()) {
                PersistentLoggers.warn(TAG, "start called while already running")
                return@withLock UrnetworkSdkBridge.StartResult.Failed("already running")
            }
            withTimeoutOrNull(SDK_INIT_TIMEOUT_MS) {
                withContext(Dispatchers.Main.immediate) {
                    runStartOnMain(byClientJwt)
                }
            } ?: run {
                PersistentLoggers.error(TAG, "SDK init timed out after ${SDK_INIT_TIMEOUT_MS}ms")
                cleanupOnFailure()
                UrnetworkSdkBridge.StartResult.Failed("URnetwork SDK init timeout (30s)")
            }
        }
    }

    private suspend fun runStartOnMain(byClientJwt: String): UrnetworkSdkBridge.StartResult {
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

        val device: DeviceLocal = try {
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
            if (keys != null) device.loadProvideSecretKeys(keys) else device.initProvideSecretKeys()
        }.onFailure { PersistentLoggers.warn(TAG, "provideSecretKeys init threw: ${it.message}") }
        runCatching { device.providePaused = true }
            .onFailure { PersistentLoggers.warn(TAG, "providePaused threw: ${it.message}") }

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

        deviceRef.set(device)
        runCatching {
            val walletVc = device.openWalletViewController()
            walletVcRef.set(walletVc)
            walletVc?.addUnpaidByteCountListener { ubc -> unpaidBytesRef.set(ubc) }
            walletVc?.start()
            walletVc?.fetchTransferStats()
            Log.i(TAG, "WalletViewController opened — provider stats listener attached")
        }.onFailure {
            PersistentLoggers.warn(TAG, "WalletViewController init threw: ${it.message}")
        }
        running.set(true)
        PersistentLoggers.info(TAG, "device created — awaiting attachTun(fd) before tunnelStarted")
        return UrnetworkSdkBridge.StartResult.Success
    }

    override suspend fun stop(): Unit = lifecycleMutex.withLock {
        running.set(false)
        runCatching { bridgeScope.coroutineContext.cancelChildren() }
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
        Log.i(TAG, "stop complete")
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

    override fun setPreferredCountry(code: String?) {
        val cleaned = code?.trim()?.uppercase()?.takeIf { it.length == 2 && it.all { it.isLetter() } }
        preferredCountryRef.set(cleaned)
        Log.i(TAG, "preferredCountry set to ${cleaned ?: "<auto>"}")
    }

    override fun openLocationsViewController(): LocationsViewController? {
        if (!running.get()) return null
        return runCatching { deviceRef.get()?.openLocationsViewController() }.getOrElse {
            PersistentLoggers.warn(TAG, "openLocationsViewController threw: ${it.message}")
            null
        }
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

    override fun applyPerformanceProfile(windowType: UrnetworkWindowType, fixedIpSize: Boolean) {
        if (!running.get()) {
            PersistentLoggers.warn(TAG, "applyPerformanceProfile skipped — bridge not running")
            return
        }
        if (windowType == UrnetworkWindowType.AUTO) {
            Log.i(TAG, "applyPerformanceProfile skip — AUTO uses SDK defaults")
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
            sizes.windowSizeMax = if (fixedIpSize) 1 else 4
            profile.windowSize = sizes
            device.performanceProfile = profile
            Log.i(TAG, "applyPerformanceProfile windowType=${windowType.rawValue} fixedIp=$fixedIpSize OK")
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

    override suspend fun attachTun(tunFd: Int): UrnetworkSdkBridge.AttachResult {
        if (tunFd < 0) {
            return UrnetworkSdkBridge.AttachResult.Failed("invalid fd=$tunFd")
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
                    running.set(false)
                    closeDevice(capturedDevice)
                }
                val loop = Sdk.newIoLoop(capturedDevice, tunFd, callback)
                ioLoopRef.set(loop)
                runCatching { device.setTunnelStarted(true) }
                    .onFailure { PersistentLoggers.warn(TAG, "setTunnelStarted(true) threw: ${it.message}") }
                val cv = connectVcRef.get()
                if (cv != null) {
                    val preferredCountry = preferredCountryRef.get()
                    if (preferredCountry != null) {
                        Log.i(TAG, "IoLoop fd=$tunFd tunnelStarted preferredCountry=$preferredCountry")
                        connectByPreferredCountry(preferredCountry, cv)
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

    private fun connectByPreferredCountry(countryCode: String, cv: ConnectViewController) {
        val device = deviceRef.get() ?: run {
            runCatching { cv.connectBestAvailable() }
            return
        }
        val locVc = runCatching { device.openLocationsViewController() }.getOrNull()
        if (locVc == null) {
            PersistentLoggers.warn(TAG, "openLocationsViewController null — fallback connectBestAvailable")
            runCatching { cv.connectBestAvailable() }
            return
        }
        val attached = AtomicBoolean(false)
        val timeoutJob = bridgeScope.launch {
            delay(PREFERRED_COUNTRY_TIMEOUT_MS)
            if (attached.compareAndSet(false, true)) {
                runCatching { cv.connectBestAvailable() }
                runCatching { locVc.stop() }
                runCatching { locVc.close() }
                PersistentLoggers.warn(
                    TAG,
                    "preferred country $countryCode timeout (${PREFERRED_COUNTRY_TIMEOUT_MS}ms) → fallback connectBestAvailable",
                )
            }
        }
        runCatching {
            locVc.addFilteredLocationsListener { filtered, _ ->
                val list = filtered?.countries ?: return@addFilteredLocationsListener
                if (list.len() == 0L) return@addFilteredLocationsListener
                var match: ConnectLocation? = null
                for (i in 0 until list.len()) {
                    val loc = list.get(i) ?: continue
                    if (loc.countryCode?.uppercase() == countryCode) {
                        match = loc
                        break
                    }
                }
                if (attached.compareAndSet(false, true)) {
                    timeoutJob.cancel()
                    if (match != null) {
                        runCatching { cv.connect(match) }
                            .onFailure { PersistentLoggers.warn(TAG, "connect(match) threw: ${it.message}") }
                        Log.i(TAG, "preferred country $countryCode matched → connected")
                    } else {
                        runCatching { cv.connectBestAvailable() }
                        PersistentLoggers.warn(
                            TAG,
                            "preferred country $countryCode not in locations → fallback connectBestAvailable",
                        )
                    }
                    runCatching { locVc.stop() }
                    runCatching { locVc.close() }
                }
            }
            locVc.start()
            locVc.filterLocations("")
        }.onFailure { t ->
            if (attached.compareAndSet(false, true)) {
                timeoutJob.cancel()
                PersistentLoggers.warn(TAG, "locVc setup failed: ${t.message} → fallback connectBestAvailable")
                runCatching { cv.connectBestAvailable() }
                runCatching { locVc.stop() }
                runCatching { locVc.close() }
            }
        }
    }

    private fun cleanupOnFailure() {
        deviceRef.getAndSet(null)?.also { runCatching { it.close() } }
    }

    private companion object {
        const val TAG = "RealUrnetworkSdkBridge"
        const val SDK_INIT_TIMEOUT_MS = 30_000L
        const val SUBSCRIPTION_BALANCE_TIMEOUT_MS = 10_000L
        const val STOP_TIMEOUT_MS = 3_000L
        const val DEFAULT_APP_VERSION = "0.0.2"
        const val PREFERRED_COUNTRY_TIMEOUT_MS = 8_000L
    }
}
