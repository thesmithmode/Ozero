package ru.ozero.engineurnetwork

import android.app.Application
import com.bringyour.sdk.ConnectLocation
import com.bringyour.sdk.ConnectViewController
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.IoLoop
import com.bringyour.sdk.IoLoopDoneCallback
import com.bringyour.sdk.LocationsViewController
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.SubscriptionBalanceCallback
import com.bringyour.sdk.WalletViewController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ru.ozero.enginescore.PersistentLoggers
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

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

    override suspend fun start(
        walletAddress: String,
        apiUrl: String,
        connectUrl: String,
        byClientJwt: String,
    ): UrnetworkSdkBridge.StartResult {
        if (running.get()) {
            PersistentLoggers.warn(TAG, "start called while already running")
            return UrnetworkSdkBridge.StartResult.Failed("already running")
        }
        if (byClientJwt.isBlank()) {
            return UrnetworkSdkBridge.StartResult.Failed("byClientJwt is blank")
        }

        return withTimeoutOrNull(SDK_INIT_TIMEOUT_MS) {
            withContext(Dispatchers.Main.immediate) {
                runStartOnMain(byClientJwt)
            }
        } ?: run {
            PersistentLoggers.error(TAG, "SDK init timed out after ${SDK_INIT_TIMEOUT_MS}ms")
            cleanupOnFailure()
            UrnetworkSdkBridge.StartResult.Failed("URnetwork SDK init timeout (30s)")
        }
    }

    private suspend fun runStartOnMain(byClientJwt: String): UrnetworkSdkBridge.StartResult {
        val space = try {
            UrnetworkRuntime.ensure(app)
        } catch (t: Throwable) {
            PersistentLoggers.error(TAG, "runtime ensure failed: ${t.message}")
            return UrnetworkSdkBridge.StartResult.Failed("runtime ensure failed: ${t.message}")
        }

        val localState = space.asyncLocalState?.localState
        if (localState == null) {
            PersistentLoggers.error(TAG, "asyncLocalState.localState is null — runtime not ready")
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
            PersistentLoggers.info(TAG, "ConnectViewController opened — locations available")
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
            PersistentLoggers.info(TAG, "WalletViewController opened — provider stats listener attached")
        }.onFailure {
            PersistentLoggers.warn(TAG, "WalletViewController init threw: ${it.message}")
        }
        running.set(true)
        PersistentLoggers.info(TAG, "device created — awaiting attachTun(fd) before tunnelStarted")
        return UrnetworkSdkBridge.StartResult.Success
    }

    override suspend fun stop() {
        running.set(false)
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
        PersistentLoggers.info(TAG, "stop complete")
    }

    private fun closeDevice(device: DeviceLocal) {
        runCatching { device.setTunnelStarted(false) }
            .onFailure { PersistentLoggers.warn(TAG, "setTunnelStarted(false) threw: ${it.message}") }
        runCatching { device.close() }
            .onFailure { PersistentLoggers.warn(TAG, "device.close threw: ${it.message}") }
    }

    override fun isRunning(): Boolean = running.get()

    override fun connectTo(location: ConnectLocation) {
        runCatching { connectVcRef.get()?.connect(location) }
            .onFailure { PersistentLoggers.warn(TAG, "connect threw: ${it.message}") }
    }

    override fun connectBestAvailable() {
        runCatching { connectVcRef.get()?.connectBestAvailable() }
            .onFailure { PersistentLoggers.warn(TAG, "connectBestAvailable threw: ${it.message}") }
    }

    override fun selectedLocation(): ConnectLocation? =
        runCatching { connectVcRef.get()?.selectedLocation }.getOrNull()

    override fun openLocationsViewController(): LocationsViewController? =
        runCatching { deviceRef.get()?.openLocationsViewController() }.getOrElse {
            PersistentLoggers.warn(TAG, "openLocationsViewController threw: ${it.message}")
            null
        }

    override fun setProvidePaused(paused: Boolean) {
        runCatching {
            deviceRef.get()?.providePaused = paused
            PersistentLoggers.info(TAG, "setProvidePaused paused=$paused OK")
        }.onFailure { PersistentLoggers.warn(TAG, "setProvidePaused($paused) threw: ${it.message}") }
    }

    override fun isProvidePaused(): Boolean =
        runCatching { deviceRef.get()?.providePaused ?: true }.getOrDefault(true)

    override fun peerCount(): Int =
        runCatching { connectVcRef.get()?.grid?.windowCurrentSize ?: 0 }.getOrDefault(0)

    override fun unpaidByteCount(): Long = unpaidBytesRef.get()

    override fun fetchTransferStats() {
        runCatching { walletVcRef.get()?.fetchTransferStats() }
            .onFailure { PersistentLoggers.warn(TAG, "fetchTransferStats threw: ${it.message}") }
    }

    override suspend fun fetchSubscriptionBalance(): UrnetworkSdkBridge.SubscriptionBalanceSnapshot? {
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
                    runCatching { cv.connectBestAvailable() }
                        .onFailure { PersistentLoggers.warn(TAG, "connectBestAvailable threw: ${it.message}") }
                    PersistentLoggers.info(TAG, "IoLoop fd=$tunFd tunnelStarted=true connectBestAvailable called")
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
        const val SUBSCRIPTION_BALANCE_TIMEOUT_MS = 10_000L
        const val STOP_TIMEOUT_MS = 3_000L
        const val DEFAULT_APP_VERSION = "0.0.2"
    }
}
