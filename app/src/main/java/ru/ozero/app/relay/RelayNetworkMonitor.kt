package ru.ozero.app.relay

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import ru.ozero.engineurnetwork.UrnetworkProvideNetworkMode
import ru.ozero.engineurnetwork.UrnetworkSdkBridge
import ru.ozero.enginescore.PersistentLoggers

class RelayNetworkMonitor(
    private val connectivityManager: ConnectivityManager,
    private val bridge: UrnetworkSdkBridge,
) {

    private var callback: ConnectivityManager.NetworkCallback? = null

    fun start(networkMode: UrnetworkProvideNetworkMode) {
        stop()

        val cb = object : ConnectivityManager.NetworkCallback() {
            private var connectedNetwork: Network? = null

            override fun onAvailable(network: Network) {
                connectedNetwork = network
                bridge.setProvidePaused(false)
                PersistentLoggers.debug(TAG, "network available — providePaused=false")
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                val internet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                val isEthernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

                val networkReady = if (networkMode == UrnetworkProvideNetworkMode.WIFI) {
                    internet && (isWifi || isEthernet)
                } else {
                    internet
                }
                bridge.setProvidePaused(!networkReady)
            }

            override fun onUnavailable() {
                bridge.setProvidePaused(true)
                PersistentLoggers.debug(TAG, "network unavailable — providePaused=true")
            }

            override fun onLost(network: Network) {
                if (network == connectedNetwork) {
                    connectedNetwork = null
                    bridge.setProvidePaused(true)
                    PersistentLoggers.debug(TAG, "network lost — providePaused=true")
                }
            }
        }

        val requestBuilder = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        if (networkMode == UrnetworkProvideNetworkMode.WIFI) {
            requestBuilder
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
        }

        callback = cb
        runCatching { connectivityManager.requestNetwork(requestBuilder.build(), cb, 100) }
            .onFailure { PersistentLoggers.warn(TAG, "requestNetwork threw: ${it.message}") }
    }

    fun stop() {
        val cb = callback ?: return
        callback = null
        runCatching { connectivityManager.unregisterNetworkCallback(cb) }
            .onFailure { PersistentLoggers.warn(TAG, "unregisterNetworkCallback threw: ${it.message}") }
    }

    private companion object {
        const val TAG = "RelayNet"
    }
}
