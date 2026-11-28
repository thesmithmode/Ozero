package ru.ozero.enginegfvpn;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GfVpnTunTest {
    @Test void matchesAuthenticatedTcpUdpConfiguration() {
        String yaml = GfVpnTun.yaml(1080, "alice's", "secret");
        assertTrue(yaml.contains("port: 1080\n"));
        assertTrue(yaml.contains("udp: 'tcp'\n"));
        assertTrue(yaml.contains("username: 'alice''s'\n"));
        assertTrue(yaml.contains("password: 'secret'\n"));
    }
    @Test void mapsDnsOnlyOnSupportedAndroidVersions() {
        assertFalse(GfVpnTun.supportsMapDns(28));
        assertTrue(GfVpnTun.supportsMapDns(29));
        assertEquals("1.1.1.1", GfVpnTun.dnsAddress(28));
        assertEquals("198.18.0.2", GfVpnTun.dnsAddress(29));
        assertFalse(GfVpnTun.yaml(1080, "alice", "secret", false).contains("mapdns:"));
        String mapped = GfVpnTun.yaml(1080, "alice", "secret", true);
        assertTrue(mapped.contains("mapdns:\n  address: '198.18.0.2'\n  port: 53\n"));
        assertEquals("198.19.0.0", GfVpnTun.MAP_DNS_NETWORK);
        assertEquals(16, GfVpnTun.MAP_DNS_PREFIX);
        assertTrue(mapped.contains("network: '198.19.0.0'\n  netmask: '255.255.0.0'\n"));
        assertTrue(mapped.contains("cache-size: 10000\n"));
        assertTrue(mapped.contains("username: 'alice'\n"));
    }

    @Test void rejectsInvalidAndInjectableCredentials() {
        assertThrows(IllegalArgumentException.class, () -> GfVpnTun.yaml(0, "a", "b"));
        assertThrows(IllegalArgumentException.class, () -> GfVpnTun.yaml(1080, "a\npassword: evil", "b"));
        assertThrows(IllegalArgumentException.class, () -> GfVpnTun.yaml(1080, "", "b"));
    }
}
