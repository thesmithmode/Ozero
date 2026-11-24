package ru.ozero.commonnet

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build

class AndroidNetworkDiagnostics(
    context: Context,
    private val diagnostics: NetworkDiagnostics = NetworkDiagnostics(),
) {
    private val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    suspend fun run(isAllowed: () -> Boolean): NetworkDiagnosticsReport? {
        if (!isAllowed()) return null
        val manager = connectivity ?: return null
        val network = manager.activeNetwork ?: return null
        if (!isDirectNetwork(manager, network)) return null
        val mtu = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            manager.getLinkProperties(network)?.mtu?.takeIf { it > 0 }
        } else {
            null
        }
        return diagnostics.run(mtu) {
            isAllowed() && isDirectNetwork(manager, network)
        }
    }

    private fun isDirectNetwork(manager: ConnectivityManager, network: Network): Boolean =
        manager.activeNetwork == network &&
            manager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == false
}
