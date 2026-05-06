package ru.ozero.engineurnetwork

import android.app.Application
import com.bringyour.sdk.ConnectViewController
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.IoLoop
import com.bringyour.sdk.IoLoopDoneCallback
import com.bringyour.sdk.Sdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ru.ozero.enginescore.PersistentLoggers
import java.util.concurrent.atomic.AtomicReference

class RealUrnetworkSdkBridge(
    private val app: Application,
) : UrnetworkSdkBridge {

    private val deviceRef = AtomicReference<DeviceLocal?>(null)
    private val ioLoopRef = AtomicReference<IoLoop?>(null)
    private val connectVcRef = AtomicReference<ConnectViewController?>(null)
    private val running = AtomicReference(false)

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
                DEVICE_DESCRIPTION,
                DEVICE_SPEC,
                APP_VERSION,
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

        deviceRef.set(device)
        running.set(true)
        PersistentLoggers.info(TAG, "device created — awaiting attachTun(fd) before tunnelStarted")
        return UrnetworkSdkBridge.StartResult.Success
    }

    override suspend fun stop() {
        running.set(false)
        withContext(Dispatchers.Main.immediate) {
            connectVcRef.getAndSet(null)?.also { vc ->
                runCatching { vc.disconnect() }
                    .onFailure { PersistentLoggers.warn(TAG, "connectVc.disconnect threw: ${it.message}") }
                runCatching { vc.close() }
                    .onFailure { PersistentLoggers.warn(TAG, "connectVc.close threw: ${it.message}") }
            }
            ioLoopRef.getAndSet(null)?.also { loop ->
                runCatching { loop.close() }
                    .onFailure { PersistentLoggers.warn(TAG, "ioLoop.close threw: ${it.message}") }
            }
            deviceRef.getAndSet(null)?.also { device ->
                runCatching { device.setTunnelStarted(false) }
                    .onFailure { PersistentLoggers.warn(TAG, "setTunnelStarted(false) threw: ${it.message}") }
                runCatching { device.close() }
                    .onFailure { PersistentLoggers.warn(TAG, "device.close threw: ${it.message}") }
            }
        }
        PersistentLoggers.info(TAG, "stop complete")
    }

    override fun isRunning(): Boolean = running.get()

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
                val callback = IoLoopDoneCallback {
                    PersistentLoggers.info(TAG, "IoLoop done — tunnel ended")
                    running.set(false)
                }
                val loop = Sdk.newIoLoop(device, tunFd, callback)
                ioLoopRef.set(loop)
                runCatching { device.setTunnelStarted(true) }
                    .onFailure { PersistentLoggers.warn(TAG, "setTunnelStarted(true) threw: ${it.message}") }
                val cv = runCatching { device.openConnectViewController() }.getOrElse {
                    PersistentLoggers.warn(TAG, "openConnectViewController threw: ${it.message}")
                    null
                }
                if (cv != null) {
                    connectVcRef.set(cv)
                    runCatching { cv.connectBestAvailable() }
                        .onFailure { PersistentLoggers.warn(TAG, "connectBestAvailable threw: ${it.message}") }
                    PersistentLoggers.info(TAG, "IoLoop fd=$tunFd tunnelStarted=true connectBestAvailable called")
                } else {
                    PersistentLoggers.error(TAG, "ConnectViewController is null — P2P connection will not be established")
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
        const val DEVICE_DESCRIPTION = "Ozero VPN Android"
        const val DEVICE_SPEC = "android"
        const val APP_VERSION = "0.0.2"
    }
}
