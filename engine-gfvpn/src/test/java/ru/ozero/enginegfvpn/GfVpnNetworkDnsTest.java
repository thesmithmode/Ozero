package ru.ozero.enginegfvpn;

import org.junit.jupiter.api.Test;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GfVpnNetworkDnsTest {
    @Test void formatsUniquePhysicalDnsAddresses() throws Exception {
        InetAddress one = InetAddress.getByName("9.9.9.9");
        InetAddress two = InetAddress.getByName("1.1.1.1");
        assertEquals("9.9.9.9,1.1.1.1", GfVpnNetworkDns.format(Arrays.asList(
            one, two, one, InetAddress.getByName("0.0.0.0"), InetAddress.getByName("127.0.0.1"))));
    }

    @Test void unavailableDnsDoesNotInventAddresses() {
        assertEquals("", GfVpnNetworkDns.format(Collections.emptyList()));
    }
}
