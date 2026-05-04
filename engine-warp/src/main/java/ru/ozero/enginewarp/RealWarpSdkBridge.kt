package ru.ozero.enginewarp

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.backend.TunnelActionHandler
import org.amnezia.awg.config.Config
import ru.ozero.enginescore.PersistentLoggers

class RealWarpSdkBridge(
    context: Context,
    private val backend: AwgBackend = GoBackendWrapper(context),
) : WarpSdkBridge {

    private val tunnel = object : Tunnel {
        override fun getName(): String = TUNNEL_NAME
        override fun onStateChange(newState: Tunnel.State) {
            PersistentLoggers.info(TAG, "tunnel state -> $newState")
            running = newState == Tunnel.State.UP
        }
        override fun isIpv4ResolutionPreferred(): Boolean = false
        override fun isMetered(): Boolean = false
    }

    @Volatile
    private var running: Boolean = false

    override suspend fun start(config: WarpConfig): WarpSdkBridge.StartResult =
        withContext(Dispatchers.IO) {
            try {
                val wgConfig = buildConfig(config)
                backend.setState(tunnel, Tunnel.State.UP, wgConfig)
                PersistentLoggers.info(TAG, "GoBackend.setState UP OK")
                WarpSdkBridge.StartResult.Success
            } catch (t: Throwable) {
                val msg = t.message ?: t.javaClass.simpleName
                PersistentLoggers.error(TAG, "GoBackend.setState UP failed: $msg")
                WarpSdkBridge.StartResult.Failed("WireGuard backend start failed: $msg")
            }
        }

    override suspend fun stop() {
        withContext(Dispatchers.IO) {
            try {
                backend.setState(tunnel, Tunnel.State.DOWN, null)
                PersistentLoggers.info(TAG, "GoBackend.setState DOWN OK")
            } catch (t: Throwable) {
                PersistentLoggers.error(TAG, "stop failed: ${t.message}")
            } finally {
                running = false
            }
        }
    }

    override fun isRunning(): Boolean = running

    private fun buildConfig(config: WarpConfig): Config =
        Config.parse(WarpIniBuilder.build(config).byteInputStream())

    private companion object {
        const val TAG = "RealWarpSdkBridge"
        const val TUNNEL_NAME = "ozero-warp"
    }
}

private class GoBackendWrapper(context: Context) : AwgBackend {
    private val goBackend: GoBackend by lazy { GoBackend(context, NoOpHandler) }

    override fun setState(tunnel: Tunnel, state: Tunnel.State, config: Config?): Tunnel.State =
        goBackend.setState(tunnel, state, config)

    private object NoOpHandler : TunnelActionHandler {
        override fun runPreUp(scripts: Collection<String>) = Unit
        override fun runPostUp(scripts: Collection<String>) = Unit
        override fun runPreDown(scripts: Collection<String>) = Unit
        override fun runPostDown(scripts: Collection<String>) = Unit
    }
}
