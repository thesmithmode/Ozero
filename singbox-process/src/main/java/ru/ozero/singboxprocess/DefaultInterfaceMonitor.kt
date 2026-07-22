package ru.ozero.singboxprocess

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.system.OsConstants
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.StringIterator
import java.net.Inet6Address
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

internal class DefaultInterfaceMonitor(private val connectivity: ConnectivityManager) {
    private val callbacks = ConcurrentHashMap<InterfaceUpdateListener, ConnectivityManager.NetworkCallback>()

    fun start(listener: InterfaceUpdateListener) {
        close(listener)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = publish(network, listener)
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) = publish(network, listener)
            override fun onLinkPropertiesChanged(
                network: Network,
                linkProperties: LinkProperties,
            ) = publish(network, listener)
            override fun onLost(network: Network) = publish(connectivity.activeNetwork, listener)
        }
        callbacks[listener] = callback
        register(callback)
        publish(connectivity.activeNetwork, listener)
    }

    fun close(listener: InterfaceUpdateListener) {
        callbacks.remove(listener)?.let { runCatching { connectivity.unregisterNetworkCallback(it) } }
    }

    private fun register(callback: ConnectivityManager.NetworkCallback) {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .build()
        when (Build.VERSION.SDK_INT) {
            in Build.VERSION_CODES.S..Int.MAX_VALUE -> {
                connectivity.registerBestMatchingNetworkCallback(request, callback, Handler(Looper.getMainLooper()))
            }
            in Build.VERSION_CODES.P until Build.VERSION_CODES.S -> {
                connectivity.requestNetwork(request, callback, Handler(Looper.getMainLooper()))
            }
            in Build.VERSION_CODES.O until Build.VERSION_CODES.P -> {
                connectivity.registerDefaultNetworkCallback(callback, Handler(Looper.getMainLooper()))
            }
            in Build.VERSION_CODES.N until Build.VERSION_CODES.O -> {
                connectivity.registerDefaultNetworkCallback(callback)
            }
            else -> {
                runCatching { connectivity.requestNetwork(request, callback) }
            }
        }
    }

    private fun publish(network: Network?, listener: InterfaceUpdateListener) {
        val netIf = network?.toJavaInterface(connectivity)
        val capabilities = network?.let { connectivity.getNetworkCapabilities(it) }
        listener.updateDefaultInterface(
            netIf?.name.orEmpty(),
            netIf?.index ?: -1,
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) != true,
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED) != true,
        )
    }
}

internal fun singboxNetworkInterfaces(connectivity: ConnectivityManager): NetworkInterfaceIterator {
    val javaInterfaces = NetworkInterface.getNetworkInterfaces().toList()
    val values = connectivity.allNetworks.mapNotNull { network ->
        network.toLibboxInterface(connectivity, javaInterfaces)
    }
    return object : NetworkInterfaceIterator {
        private var index = 0
        override fun hasNext(): Boolean = index < values.size
        override fun next(): io.nekohasekai.libbox.NetworkInterface = values[index++]
    }
}

private fun Network.toJavaInterface(connectivity: ConnectivityManager): NetworkInterface? =
    connectivity.getLinkProperties(this)?.interfaceName?.let { NetworkInterface.getByName(it) }

private fun Network.toLibboxInterface(
    connectivity: ConnectivityManager,
    javaInterfaces: List<NetworkInterface>,
): io.nekohasekai.libbox.NetworkInterface? {
    val linkProperties = connectivity.getLinkProperties(this) ?: return null
    val capabilities = connectivity.getNetworkCapabilities(this) ?: return null
    val javaInterface = javaInterfaces.find { it.name == linkProperties.interfaceName } ?: return null
    return javaInterface.toLibboxInterface(linkProperties, capabilities)
}

private fun NetworkInterface.toLibboxInterface(
    linkProperties: LinkProperties,
    capabilities: NetworkCapabilities,
): io.nekohasekai.libbox.NetworkInterface? = runCatching {
    val value = io.nekohasekai.libbox.NetworkInterface()
    value.name = linkProperties.interfaceName
    value.index = index
    value.mtu = mtu
    value.addresses = StringListIterator(interfaceAddresses.map { it.toPrefix() })
    value.dnsServer = StringListIterator(linkProperties.dnsServers.mapNotNull { it.hostAddress })
    value.type = capabilities.interfaceType()
    value.flags = interfaceFlags(capabilities)
    value.metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    value
}.getOrNull()

private fun NetworkCapabilities.interfaceType(): Long = when {
    hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Libbox.InterfaceTypeWIFI
    hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Libbox.InterfaceTypeCellular
    hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Libbox.InterfaceTypeEthernet
    else -> Libbox.InterfaceTypeOther
}

private fun NetworkInterface.interfaceFlags(capabilities: NetworkCapabilities): Int {
    var flags = 0
    if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
        flags = flags or OsConstants.IFF_UP or OsConstants.IFF_RUNNING
    }
    if (isLoopback) flags = flags or OsConstants.IFF_LOOPBACK
    if (isPointToPoint) flags = flags or OsConstants.IFF_POINTOPOINT
    if (supportsMulticast()) flags = flags or OsConstants.IFF_MULTICAST
    return flags
}

private fun InterfaceAddress.toPrefix(): String = if (address is Inet6Address) {
    "${Inet6Address.getByAddress(address.address).hostAddress}/$networkPrefixLength"
} else {
    "${address.hostAddress}/$networkPrefixLength"
}

private fun <T> java.util.Enumeration<T>.toList(): List<T> = Collections.list(this)

private class StringListIterator(private val values: List<String>) : StringIterator {
    private var index = 0
    override fun hasNext(): Boolean = index < values.size
    override fun len(): Int = values.size
    override fun next(): String = values[index++]
}
