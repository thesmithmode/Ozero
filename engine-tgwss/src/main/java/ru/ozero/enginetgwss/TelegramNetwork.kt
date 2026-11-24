package ru.ozero.enginetgwss

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import okhttp3.Dns
import ru.ozero.enginescore.VpnSocketProtectorHolder
import java.net.Socket
import java.net.UnknownHostException

internal class TelegramNetwork(context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    val dns = Dns { hostname ->
        val manager = connectivity ?: throw UnknownHostException("No underlying network")
        val candidates = (listOfNotNull(manager.activeNetwork) + manager.allNetworks.toList()).distinct()
        val network = candidates.firstOrNull {
            val caps = manager.getNetworkCapabilities(it)
            val internet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            internet && caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) == true
        } ?: throw UnknownHostException("No underlying network")
        network.getAllByName(hostname).toList()
    }

    fun protect(socket: Socket): Boolean =
        VpnSocketProtectorHolder.protectSocket(socket)
}
