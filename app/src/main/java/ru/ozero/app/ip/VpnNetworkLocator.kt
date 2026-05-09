package ru.ozero.app.ip

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.net.SocketFactory

interface VpnNetworkLocator {
    fun vpnSocketFactory(): SocketFactory?
}

class DefaultVpnNetworkLocator @Inject constructor(
    @ApplicationContext private val context: Context,
) : VpnNetworkLocator {
    override fun vpnSocketFactory(): SocketFactory? {
        val cm = runCatching {
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        }.getOrNull() ?: return null
        val vpnNet = vpnNetwork(cm) ?: return null
        return runCatching { vpnNet.socketFactory }.getOrNull()
    }

    private fun vpnNetwork(cm: ConnectivityManager): Network? = runCatching {
        cm.allNetworks.firstOrNull { net ->
            val caps = cm.getNetworkCapabilities(net)
            caps != null &&
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        }
    }.getOrNull()
}
