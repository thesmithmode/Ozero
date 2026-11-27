package ru.ozero.enginegfvpn;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GfVpnClientDefaultsTest {
    public static final class FakeBinding {
        final Map<String, Object> values = new HashMap<>();
        public void setWorkDir(String v) { values.put("work", v); }
        public void setSimultaneousConnections(long v) { values.put("connections", v); }
        public void setLocaleCountryCode(String v) { values.put("locale", v); }
        public void setForceConfigCountry(String v) { values.put("country", v); }
        public void setClientIdentifier(String v) { values.put("id", v); }
        public void setServerSelection(String v) { values.put("selection", v); }
        public void setTransport(String v) { values.put("transport", v); }
        public void setNodeRotationEnabled(String v) { values.put("rotation", v); }
        public void setMaxConcurrentAttempts(long v) { values.put("attempts", v); }
        public void setUseRemoteConfig(String v) { values.put("remote", v); }
        public void setUseCachedNodes(String v) { values.put("cached", v); }
        public void setSplitTunnelEnabled(String v) { values.put("split", v); }
        public void setSocksAuthEnabled(String v) { values.put("auth", v); }
    }

    @Test void usesReferenceDefaultsAndKeepsOzeroSecurityPolicy() throws Exception {
        FakeBinding binding = new FakeBinding();
        GfVpnClientDefaults.configure(binding, "/private/work", "RU", "client-id");
        assertEquals(13, binding.values.size());
        assertEquals("/private/work", binding.values.get("work"));
        assertEquals("RU", binding.values.get("locale"));
        assertEquals("RU", binding.values.get("country"));
        assertEquals("client-id", binding.values.get("id"));
        assertEquals(0L, binding.values.get("connections"));
        assertEquals(0L, binding.values.get("attempts"));
        assertEquals("auto", binding.values.get("selection"));
        assertEquals("", binding.values.get("transport"));
        assertEquals("", binding.values.get("rotation"));
        assertEquals("", binding.values.get("cached"));
        assertEquals("true", binding.values.get("remote"));
        assertEquals("false", binding.values.get("split"));
        assertEquals("true", binding.values.get("auth"));
    }
}
