package ru.ozero.enginegfvpn;

/** Explicit SDK startup defaults verified against the supplied APK's public Java binding. */
final class GfVpnClientDefaults {
    private GfVpnClientDefaults() { }

    static void configure(Object config, String workDir, String country, String identifier) throws Exception {
        set(config, "setWorkDir", workDir);
        setLong(config, "setSimultaneousConnections", 0);
        set(config, "setLocaleCountryCode", country);
        set(config, "setForceConfigCountry", country);
        set(config, "setClientIdentifier", identifier);
        set(config, "setServerSelection", "auto");
        // Empty transport and zero concurrency select the SDK's own defaults.
        set(config, "setTransport", "");
        set(config, "setNodeRotationEnabled", "");
        setLong(config, "setMaxConcurrentAttempts", 0);
        set(config, "setUseRemoteConfig", "true");
        set(config, "setUseCachedNodes", "");
        // Ozero controls split routing; require authenticated local SOCKS.
        set(config, "setSplitTunnelEnabled", "false");
        set(config, "setSocksAuthEnabled", "true");
    }

    private static void set(Object config, String method, String value) throws Exception {
        config.getClass().getMethod(method, String.class).invoke(config, value);
    }

    private static void setLong(Object config, String method, long value) throws Exception {
        config.getClass().getMethod(method, long.class).invoke(config, value);
    }
}
