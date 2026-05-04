package ru.ozero.engineurnetwork

import android.content.Context
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.IoLoop
import com.bringyour.sdk.IoLoopDoneCallback
import com.bringyour.sdk.NetworkSpace
import com.bringyour.sdk.NetworkSpaceManager
import com.bringyour.sdk.Sdk
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
        byJwt: String?,
    ): UrnetworkSdkBridge.StartResult {
        if (running.get()) {
            PersistentLoggers.warn(TAG, "start called while already running")
            return UrnetworkSdkBridge.StartResult.Failed("already running")
        }

        val storageDir = context.filesDir.resolve(URN_STORAGE_DIR).apply { mkdirs() }.absolutePath
        val manager = try {
            Sdk.newNetworkSpaceManager(storageDir)
        } catch (t: Throwable) {
            PersistentLoggers.error(TAG, "newNetworkSpaceManager threw: ${t.message}")
            return UrnetworkSdkBridge.StartResult.Failed("NetworkSpaceManager init failed: ${t.message}")
        }
        managerRef.set(manager)

        val space: NetworkSpace = try {
            resolveNetworkSpace(manager) ?: run {
                PersistentLoggers.error(TAG, "NetworkSpace null after active/get/import fallback")
                cleanupOnFailure()
                return UrnetworkSdkBridge.StartResult.Failed("NetworkSpace resolve failed: SDK returned null")
            }
        } catch (t: Throwable) {
            PersistentLoggers.error(TAG, "NetworkSpace resolve failed: ${t.message}\n${t.stackTraceToString()}")
            cleanupOnFailure()
            return UrnetworkSdkBridge.StartResult.Failed("NetworkSpace resolve failed: ${t.message}")
        }

        val device: DeviceLocal = try {
            Sdk.newDeviceLocalWithDefaults(
                space,
                byJwt.orEmpty(),
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
                "DeviceLocal init failed (likely needs JWT auth): ${t.message}",
            )
        }
        deviceRef.set(device)

        return try {
            device.setTunnelStarted(true)
            running.set(true)
            PersistentLoggers.info(TAG, "tunnel signal sent — awaiting attachTun(fd)")
            UrnetworkSdkBridge.StartResult.Success
        } catch (t: Throwable) {
            PersistentLoggers.error(TAG, "setTunnelStarted threw: ${t.message}")
            cleanupOnFailure()
            UrnetworkSdkBridge.StartResult.Failed("setTunnelStarted failed: ${t.message}")
        }
    }

    override suspend fun stop() {
        running.set(false)
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
        return try {
            val callback = IoLoopDoneCallback {
                PersistentLoggers.info(TAG, "IoLoop done — tunnel ended")
                running.set(false)
            }
            val loop = Sdk.newIoLoop(device, tunFd, callback)
            ioLoopRef.set(loop)
            PersistentLoggers.info(TAG, "IoLoop attached on fd=$tunFd")
            UrnetworkSdkBridge.AttachResult.Success
        } catch (t: Throwable) {
            PersistentLoggers.error(TAG, "newIoLoop threw: ${t.message}")
            UrnetworkSdkBridge.AttachResult.Failed("newIoLoop failed: ${t.message}")
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
        val imported = manager.importNetworkSpaceFromJson(
            """{"host_name":"$DEFAULT_HOST","env_name":"$DEFAULT_ENV"}""",
        ) ?: return null
        manager.setActiveNetworkSpace(imported)
        PersistentLoggers.info(TAG, "imported default NetworkSpace")
        return imported
    }

    private fun cleanupOnFailure() {
        deviceRef.getAndSet(null)?.also { runCatching { it.close() } }
        managerRef.getAndSet(null)?.also { runCatching { it.close() } }
    }

    private companion object {
        const val TAG = "RealUrnetworkSdkBridge"
        const val URN_STORAGE_DIR = "urnetwork"
        const val DEFAULT_HOST = "ur.network"
        const val DEFAULT_ENV = "prod"
        const val DEVICE_DESCRIPTION = "Ozero VPN Android"
        const val DEVICE_SPEC = "android"
        const val APP_VERSION = "0.0.2"
    }
}
