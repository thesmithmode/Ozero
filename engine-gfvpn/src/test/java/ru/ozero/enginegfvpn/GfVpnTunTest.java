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
    @Test void rejectsInvalidAndInjectableCredentials() {
        assertThrows(IllegalArgumentException.class, () -> GfVpnTun.yaml(0, "a", "b"));
        assertThrows(IllegalArgumentException.class, () -> GfVpnTun.yaml(1080, "a\npassword: evil", "b"));
        assertThrows(IllegalArgumentException.class, () -> GfVpnTun.yaml(1080, "", "b"));
    }
}
