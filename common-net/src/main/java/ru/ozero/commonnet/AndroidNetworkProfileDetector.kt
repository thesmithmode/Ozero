package ru.ozero.commonnet

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.security.MessageDigest

class AndroidNetworkProfileDetector(private val context: Context) : NetworkProfileDetector {

    override fun current(): NetworkProfile {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetworkProfile.NONE
        val active = cm.activeNetwork ?: return NetworkProfile.NONE
        val caps = cm.getNetworkCapabilities(active) ?: return NetworkProfile.NONE
        val transport = transportFrom(caps)
        if (transport == NetworkProfile.Transport.NONE) return NetworkProfile.NONE
        val link = cm.getLinkProperties(active)
        val gatewayHash = link?.routes
            ?.firstOrNull { runCatching { it.isDefaultRoute }.getOrDefault(false) }
            ?.gateway?.hostAddress.orEmpty()
        val interfaceName = link?.interfaceName.orEmpty()
        val seed = "${transport.name}|$gatewayHash|$interfaceName"
        return NetworkProfile(id = hash(seed), transport = transport, label = null)
    }

    private fun transportFrom(caps: NetworkCapabilities): NetworkProfile.Transport = when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkProfile.Transport.VPN
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkProfile.Transport.WIFI
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkProfile.Transport.MOBILE
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkProfile.Transport.ETHERNET
        else -> NetworkProfile.Transport.OTHER
    }

    private fun hash(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(ID_LENGTH)
    }

    companion object {
        private const val ID_LENGTH: Int = 16
    }
}
