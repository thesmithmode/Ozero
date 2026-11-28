package ru.ozero.enginegfvpn;

import ru.ozero.enginescore.settings.GfVpnOptions;

/** Explicit SDK startup defaults verified against the supplied APK's public Java binding. */
final class GfVpnClientDefaults {
    private GfVpnClientDefaults() { }

    static void configure(Object config, String workDir, String country, String identifier) throws Exception {
        configure(config, workDir, country, identifier, GfVpnOptions.DEFAULT);
    }

    static void configure(Object config, String workDir, String country, String identifier,
                          GfVpnOptions requested) throws Exception {
        GfVpnOptions options = requested.effective();
        set(config, "setWorkDir", workDir);
        setLong(config, "setSimultaneousConnections", options.getSimultaneousConnections());
        set(config, "setLocaleCountryCode", country);
        // Reference release uses SDK geolocation; forced country is a debug-only option.
        set(config, "setForceConfigCountry", "");
        set(config, "setClientIdentifier", identifier);
        set(config, "setServerSelection", options.getServerSelection());
        set(config, "setTransport", options.getTransport());
        set(config, "setNodeRotationEnabled", options.getRotation());
        setLong(config, "setMaxConcurrentAttempts", options.getMaxConcurrentAttempts());
        set(config, "setUseRemoteConfig", options.getAutoUpdate() ? "true" : "false");
        set(config, "setUseCachedNodes", options.getCachedNodes() ? "" : "false");
        // SDK's local-service bypass is separate from Ozero's app-based routing.
        set(config, "setSplitTunnelEnabled", Boolean.toString(options.getBypassLocalServices()));
        set(config, "setSocksAuthEnabled", "true");
    }

    private static void set(Object config, String method, String value) throws Exception {
        config.getClass().getMethod(method, String.class).invoke(config, value);
    }

    private static void setLong(Object config, String method, long value) throws Exception {
        config.getClass().getMethod(method, long.class).invoke(config, value);
    }
}
