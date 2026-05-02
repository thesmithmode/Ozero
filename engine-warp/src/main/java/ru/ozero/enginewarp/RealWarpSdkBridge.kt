package ru.ozero.enginewarp

import android.content.Context
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.ozero.enginescore.PersistentLoggers

class RealWarpSdkBridge(
    private val context: Context,
) : WarpSdkBridge {

    private val backend: GoBackend by lazy { GoBackend(context) }

    private val tunnel = object : Tunnel {
        override fun getName(): String = TUNNEL_NAME
        override fun onStateChange(newState: Tunnel.State) {
            PersistentLoggers.info(TAG, "tunnel state -> $newState")
            running = newState == Tunnel.State.UP
        }
    }

    @Volatile
    private var running: Boolean = false

    override suspend fun start(config: WarpConfig): WarpSdkBridge.StartResult =
        withContext(Dispatchers.IO) {
            try {
                val wgConfig = buildConfig(config)
                // TODO link via DI: backend lifecycle (single instance per process,
                // currently lazily owned here; should move to module-scope @Provides
                // когда RealWarpSdkBridge станет default).
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

    private fun buildConfig(config: WarpConfig): Config {
        val ifaceBuilder = Interface.Builder()
            .parsePrivateKey(config.privateKey)
            .parseAddresses("${config.interfaceAddressV4},${config.interfaceAddressV6}")
            .setMtu(config.mtu)
        // TODO link via DI: DNS provider (currently relies on system resolver via
        // VpnService; WARP advertises 1.1.1.1/2606:4700:4700::1111 — should plumb
        // через WarpAutoConfig если автоконфиг начнёт их возвращать).

        val peerEndpoint = parseEndpoint(config.peerEndpoint)
        val peerBuilder = Peer.Builder()
            .parsePublicKey(config.peerPublicKey)
            .setEndpoint(peerEndpoint)
            .parseAllowedIPs(config.allowedIps.joinToString(","))
        // TODO link via DI: persistent keepalive interval (WARP recommends 25s
        // на CGNAT/мобильных сетях; нужно поднять из WarpConfig когда добавится
        // поле keepaliveSeconds).

        return Config.Builder()
            .setInterface(ifaceBuilder.build())
            .addPeer(peerBuilder.build())
            .build()
    }

    private fun parseEndpoint(endpoint: String): com.wireguard.config.InetEndpoint {
        // peerEndpoint from WarpConfig имеет формат "host:port" (либо "[v6]:port").
        // com.wireguard.config.InetEndpoint.parse кидает ParseException на bad input —
        // верхний catch в start() конвертирует в Failed.
        return com.wireguard.config.InetEndpoint.parse(endpoint)
    }

    private companion object {
        const val TAG = "RealWarpSdkBridge"
        const val TUNNEL_NAME = "ozero-warp"
    }
}
