package ru.ozero.enginegfvpn;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import java.net.InetAddress;
import java.util.LinkedHashSet;
import java.util.List;

/** DNS bootstrap data is supplied only from an underlying non-VPN network. */
final class GfVpnNetworkDns {
    private GfVpnNetworkDns() { }

    static String current(Context context) {
        try {
            ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
            if (manager == null) return "";
            Network active = manager.getActiveNetwork();
            if (isPhysical(manager, active)) {
                return addresses(manager, active);
            }
            Network fallback = null;
            for (Network network : manager.getAllNetworks()) {
                if (!isPhysical(manager, network)) continue;
                NetworkCapabilities caps = manager.getNetworkCapabilities(network);
                if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                    String dns = addresses(manager, network);
                    if (!dns.isEmpty()) return dns;
                }
                if (fallback == null) fallback = network;
            }
            return fallback == null ? "" : addresses(manager, fallback);
        } catch (RuntimeException unavailable) {
            return "";
        }
    }

    private static boolean isPhysical(ConnectivityManager manager, Network network) {
        if (network == null) return false;
        NetworkCapabilities caps = manager.getNetworkCapabilities(network);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN);
    }

    private static String addresses(ConnectivityManager manager, Network network) {
        LinkProperties properties = manager.getLinkProperties(network);
        return properties == null ? "" : format(properties.getDnsServers());
    }

    static String format(List<InetAddress> addresses) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (InetAddress address : addresses) {
            if (address == null || address.isAnyLocalAddress() || address.isLoopbackAddress()) continue;
            String value = address.getHostAddress();
            if (value == null || value.isEmpty()) continue;
            int scope = value.indexOf('%');
            if (scope >= 0) value = value.substring(0, scope);
            if (!value.isEmpty()) unique.add(value);
        }
        return String.join(",", unique);
    }
}
