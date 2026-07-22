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
import ru.ozero.enginescore.PersistentLoggers
import java.net.Inet6Address
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

internal class DefaultInterfaceMonitor(private val connectivity: ConnectivityManager) {
    private val callbacks = ConcurrentHashMap<InterfaceUpdateListener, ConnectivityManager.NetworkCallback>()

    @Volatile
    private var lastPhysicalNetwork: Network? = null

    fun start(listener: InterfaceUpdateListener) {
        close(listener)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = publish(network, listener, this)
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) = publish(network, listener, this)
            override fun onLinkPropertiesChanged(
                network: Network,
                linkProperties: LinkProperties,
            ) = publish(network, listener, this)
            override fun onLost(network: Network) {
                if (!isCurrent(listener, this)) return
                if (lastPhysicalNetwork == network) lastPhysicalNetwork = null
                publish(bestPhysicalNetwork(exclude = network), listener, this)
            }
        }
        callbacks[listener] = callback
        register(callback)
        publish(bestPhysicalNetwork(), listener, callback)
    }

    fun close(listener: InterfaceUpdateListener) {
        callbacks.remove(listener)?.let { callback ->
            runCatching { connectivity.unregisterNetworkCallback(callback) }
                .onFailure {
                    PersistentLoggers.warn(TAG, "network callback unregister failed: ${it::class.java.simpleName}")
                }
        }
        if (callbacks.isEmpty()) lastPhysicalNetwork = null
    }

    private fun isCurrent(
        listener: InterfaceUpdateListener,
        callback: ConnectivityManager.NetworkCallback,
    ): Boolean = callbacks[listener] === callback

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

    private fun publish(
        network: Network?,
        listener: InterfaceUpdateListener,
        callback: ConnectivityManager.NetworkCallback,
    ) {
        if (!isCurrent(listener, callback)) return
        val physicalNetwork = network?.takeIf { it.isEligiblePhysical(connectivity) } ?: bestPhysicalNetwork()
        lastPhysicalNetwork = physicalNetwork
        val netIf = physicalNetwork?.toJavaInterface(connectivity)
        val capabilities = physicalNetwork?.let { connectivity.getNetworkCapabilities(it) }
        listener.updateDefaultInterface(
            netIf?.name.orEmpty(),
            netIf?.index ?: -1,
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) != true,
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED) != true,
        )
    }

    private fun bestPhysicalNetwork(exclude: Network? = null): Network? {
        val activeNetwork = runCatching { connectivity.activeNetwork }.getOrNull()
        val allNetworks = runCatching { connectivity.allNetworks.toList() }.getOrDefault(emptyList())
        val candidates = buildList {
            lastPhysicalNetwork?.let { network ->
                add(network.toCandidate(active = network == activeNetwork, source = NetworkCandidateSource.LAST))
            }
            activeNetwork?.let { network ->
                add(network.toCandidate(active = true, source = NetworkCandidateSource.ACTIVE))
            }
            allNetworks.forEach { network ->
                add(network.toCandidate(active = network == activeNetwork, source = NetworkCandidateSource.ALL))
            }
        }
        return selectDefaultNetwork(candidates, exclude)
    }

    private fun Network.toCandidate(
        active: Boolean,
        source: NetworkCandidateSource,
    ): NetworkCandidate {
        val capabilities = runCatching { connectivity.getNetworkCapabilities(this) }.getOrNull()
        return NetworkCandidate(
            network = this,
            eligible = capabilities.isEligiblePhysical(),
            active = active,
            validated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            source = source,
        )
    }
}

internal fun singboxNetworkInterfaces(connectivity: ConnectivityManager): NetworkInterfaceIterator {
    val javaInterfaces = NetworkInterface.getNetworkInterfaces()
        ?.let(Collections::list)
        .orEmpty()
    val values = connectivity.allNetworks
        .filter { network -> network.isEligiblePhysical(connectivity) }
        .mapNotNull { network -> network.toLibboxInterface(connectivity, javaInterfaces) }
    return object : NetworkInterfaceIterator {
        private var index = 0
        override fun hasNext(): Boolean = index < values.size
        override fun next(): io.nekohasekai.libbox.NetworkInterface = values[index++]
    }
}

private fun Network.isEligiblePhysical(connectivity: ConnectivityManager): Boolean =
    runCatching { connectivity.getNetworkCapabilities(this) }
        .getOrNull()
        .isEligiblePhysical()

private fun NetworkCapabilities?.isEligiblePhysical(): Boolean =
    this != null &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED) &&
        !hasTransport(NetworkCapabilities.TRANSPORT_VPN)

private fun Network.toJavaInterface(connectivity: ConnectivityManager): NetworkInterface? =
    connectivity.getLinkProperties(this)?.interfaceName?.let { name ->
        runCatching { NetworkInterface.getByName(name) }
            .onFailure { PersistentLoggers.warn(TAG, "default interface unavailable: ${it::class.java.simpleName}") }
            .getOrNull()
    }

private fun Network.toLibboxInterface(
    connectivity: ConnectivityManager,
    javaInterfaces: List<NetworkInterface>,
): io.nekohasekai.libbox.NetworkInterface? {
    val linkProperties = connectivity.getLinkProperties(this) ?: return null
    val capabilities = connectivity.getNetworkCapabilities(this) ?: return null
    val interfaceName = linkProperties.interfaceName
        ?.takeIf { it.isNotBlank() }
        ?: return null
    val javaInterface = javaInterfaces.firstOrNull { it.name == interfaceName }
        ?: runCatching { NetworkInterface.getByName(interfaceName) }
            .onFailure { PersistentLoggers.warn(TAG, "physical interface lookup failed: ${it::class.java.simpleName}") }
            .getOrNull()
    if (javaInterface == null) {
        PersistentLoggers.warn(TAG, "skip physical network: Java interface unavailable")
        return null
    }
    return javaInterface.toLibboxInterface(linkProperties, capabilities)
}

private fun NetworkInterface.toLibboxInterface(
    linkProperties: LinkProperties,
    capabilities: NetworkCapabilities,
): io.nekohasekai.libbox.NetworkInterface? {
    val interfaceName = linkProperties.interfaceName
    if (interfaceName.isNullOrBlank()) {
        PersistentLoggers.warn(TAG, "skip physical network without interface name")
        return null
    }
    val interfaceIndex = index
    if (interfaceIndex <= 0) {
        PersistentLoggers.warn(TAG, "skip physical network with invalid interface index")
        return null
    }
    val value = io.nekohasekai.libbox.NetworkInterface()
    value.name = interfaceName
    value.index = interfaceIndex
    value.mtu = runCatching { mtu }
        .onFailure { PersistentLoggers.warn(TAG, "interface mtu unavailable: ${it::class.java.simpleName}") }
        .getOrDefault(0)
    value.addresses = StringListIterator(
        runCatching { interfaceAddresses.map { it.toPrefix() } }
            .onFailure { PersistentLoggers.warn(TAG, "interface addresses unavailable: ${it::class.java.simpleName}") }
            .getOrDefault(emptyList()),
    )
    value.dnsServer = StringListIterator(linkProperties.dnsServers.mapNotNull { it.hostAddress })
    value.type = capabilities.interfaceType()
    value.flags = interfaceFlags(capabilities)
    value.metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    return value
}

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
    runCatching { isLoopback }
        .onSuccess { if (it) flags = flags or OsConstants.IFF_LOOPBACK }
        .onFailure { PersistentLoggers.warn(TAG, "loopback flag unavailable: ${it::class.java.simpleName}") }
    runCatching { isPointToPoint }
        .onSuccess { if (it) flags = flags or OsConstants.IFF_POINTOPOINT }
        .onFailure { PersistentLoggers.warn(TAG, "point-to-point flag unavailable: ${it::class.java.simpleName}") }
    runCatching { supportsMulticast() }
        .onSuccess { if (it) flags = flags or OsConstants.IFF_MULTICAST }
        .onFailure { PersistentLoggers.warn(TAG, "multicast flag unavailable: ${it::class.java.simpleName}") }
    return flags
}

internal data class NetworkCandidate(
    val network: Network,
    val eligible: Boolean,
    val active: Boolean,
    val validated: Boolean,
    val source: NetworkCandidateSource,
)

internal enum class NetworkCandidateSource {
    LAST,
    ACTIVE,
    ALL,
}

internal fun selectDefaultNetwork(
    candidates: List<NetworkCandidate>,
    exclude: Network? = null,
): Network? = candidates
    .asSequence()
    .filter { it.network != exclude && it.eligible }
    .distinctBy { it.network }
    .sortedWith(
        compareByDescending<NetworkCandidate> { it.source == NetworkCandidateSource.LAST }
            .thenByDescending { it.active }
            .thenByDescending { it.validated },
    )
    .firstOrNull()
    ?.network

private fun InterfaceAddress.toPrefix(): String = if (address is Inet6Address) {
    "${Inet6Address.getByAddress(address.address).hostAddress}/$networkPrefixLength"
} else {
    "${address.hostAddress}/$networkPrefixLength"
}

private const val TAG = "SingboxNetwork"

private class StringListIterator(private val values: List<String>) : StringIterator {
    private var index = 0
    override fun hasNext(): Boolean = index < values.size
    override fun len(): Int = values.size
    override fun next(): String = values[index++]
}
