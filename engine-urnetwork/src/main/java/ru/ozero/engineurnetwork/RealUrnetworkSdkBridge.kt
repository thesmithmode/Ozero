package ru.ozero.engineurnetwork

import android.content.Context
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.IoLoop
import com.bringyour.sdk.IoLoopDoneCallback
import com.bringyour.sdk.NetworkSpace
import com.bringyour.sdk.NetworkSpaceManager
import com.bringyour.sdk.Sdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ru.ozero.enginescore.PersistentLoggers
import java.util.concurrent.atomic.AtomicReference

class RealUrnetworkSdkBridge(
    private val context: Context,
) : UrnetworkSdkBridge {

    private val managerRef = AtomicReference<NetworkSpaceManager?>(null)
    private val deviceRef = AtomicReference<DeviceLocal?>(null)
    private val ioLoopRef = AtomicReference<IoLoop?>(null)
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

    private fun runStartOnMain(byClientJwt: String): UrnetworkSdkBridge.StartResult {
        val storageDir = context.filesDir.absolutePath
        val manager = try {
            Sdk.newNetworkSpaceManager(storageDir)
        } catch (t: Throwable) {
            PersistentLoggers.error(TAG, "newNetworkSpaceManager threw: ${t.message}")
            return UrnetworkSdkBridge.StartResult.Failed(
                "NetworkSpaceManager init failed: ${t.message}",
            )
        }
        managerRef.set(manager)

        val space: NetworkSpace = try {
            resolveNetworkSpace(manager) ?: run {
                PersistentLoggers.error(TAG, "NetworkSpace null after active/get/import fallback")
                cleanupOnFailure()
                return UrnetworkSdkBridge.StartResult.Failed(
                    "NetworkSpace resolve failed: SDK returned null",
                )
            }
        } catch (t: Throwable) {
            PersistentLoggers.error(TAG, "NetworkSpace resolve failed: ${t.message}\n${t.stackTraceToString()}")
            cleanupOnFailure()
            return UrnetworkSdkBridge.StartResult.Failed(
                "NetworkSpace resolve failed: ${t.message}",
            )
        }

        val device: DeviceLocal = try {
            Sdk.newDeviceLocalWithDefaults(
                space,
                byClientJwt,
                DEVICE_DESCRIPTION,
                DEVICE_SPEC,
                APP_VERSION,
                null,
                false,
            )
        } catch (t: Throwable) {
            PersistentLoggers.error(TAG, "newDeviceLocalWithDefaults threw: ${t.message}")
            cleanupOnFailure()
            return UrnetworkSdkBridge.StartResult.Failed(
                "DeviceLocal init failed: ${t.message}",
            )
        }
        deviceRef.set(device)
        running.set(true)
        PersistentLoggers.info(TAG, "device created — awaiting attachTun(fd) before tunnelStarted")
        return UrnetworkSdkBridge.StartResult.Success
    }

    override suspend fun stop() {
        running.set(false)
        withContext(Dispatchers.Main.immediate) {
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
            managerRef.getAndSet(null)?.also { manager ->
                runCatching { manager.close() }
                    .onFailure { PersistentLoggers.warn(TAG, "manager.close threw: ${it.message}") }
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
            ?: return UrnetworkSdkBridge.AttachResult.Failed(
                "DeviceLocal not initialised — call start() first",
            )
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
                PersistentLoggers.info(TAG, "IoLoop attached on fd=$tunFd, tunnelStarted=true")
                UrnetworkSdkBridge.AttachResult.Success
            } catch (t: Throwable) {
                PersistentLoggers.error(TAG, "newIoLoop threw: ${t.message}")
                UrnetworkSdkBridge.AttachResult.Failed("newIoLoop failed: ${t.message}")
            }
        }
    }

    private fun resolveNetworkSpace(manager: NetworkSpaceManager): NetworkSpace? {
        manager.activeNetworkSpace?.let {
            PersistentLoggers.info(TAG, "using active NetworkSpace")
            return it
        }
        val key = Sdk.newNetworkSpaceKey(DEFAULT_HOST, DEFAULT_ENV)
        manager.getNetworkSpace(key)?.let {
            PersistentLoggers.info(TAG, "using stored NetworkSpace")
            runCatching { manager.setActiveNetworkSpace(it) }
                .onFailure { e -> PersistentLoggers.warn(TAG, "setActiveNetworkSpace(stored) failed: ${e.message}") }
            return it
        }
        val updated = manager.updateNetworkSpace(key) { values ->
            values.envSecret = ""
            values.bundled = true
            values.netExposeServerIps = true
            values.netExposeServerHostNames = true
            values.linkHostName = LINK_HOST_NAME
            values.migrationHostName = MIGRATION_HOST_NAME
            values.store = ""
            values.wallet = WALLET
            values.ssoGoogle = false
        } ?: return null
        manager.setActiveNetworkSpace(updated)
        PersistentLoggers.info(TAG, "updated bundled NetworkSpace host=$DEFAULT_HOST env=$DEFAULT_ENV")
        return updated
    }

    private fun cleanupOnFailure() {
        deviceRef.getAndSet(null)?.also { runCatching { it.close() } }
        managerRef.getAndSet(null)?.also { runCatching { it.close() } }
    }

    private companion object {
        const val TAG = "RealUrnetworkSdkBridge"
        const val SDK_INIT_TIMEOUT_MS = 30_000L
        const val DEFAULT_HOST = "ur.network"
        const val DEFAULT_ENV = "main"
        const val LINK_HOST_NAME = "ur.io"
        const val MIGRATION_HOST_NAME = "bringyour.com"
        const val WALLET = "solana"
        const val DEVICE_DESCRIPTION = "Ozero VPN Android"
        const val DEVICE_SPEC = "android"
        const val APP_VERSION = "0.0.2"
    }
}
