package ru.ozero.enginegfvpn;

import android.content.Context;
import android.os.Build;
import dalvik.system.DexClassLoader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;

/** Runtime bridge for the exact locally supplied SDK. Never bundles third-party code. */
final class GfVpnSdk {
    private static final String DEX_SHA = "103f8155e3bd33b8f13d8ff7d1f63cb5ec1fe7acc1aa1c395eaedb83d336c829";
    private static final String LIB_SHA = "7fdffc628230ce188c474c42bfc5fef5b802ace74bd9e9b249f0272f9090e404";
    private final Context context;
    private Class<?> api;
    private Class<?> configType;
    private boolean active;
    private Session session;

    static final class Session {
        final int port;
        final String username;
        final String password;
        Session(int port, String username, String password) {
            this.port = port;
            this.username = username;
            this.password = password;
        }
    }

    GfVpnSdk(Context context) {
        this.context = context.getApplicationContext();
    }

    private static void verify(File file, String expected) throws Exception {
        if (!file.isFile() || file.canWrite()) {
            throw new IOException("SDK file missing or writable");
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[65536];
            for (int n; (n = input.read(buffer)) != -1;) digest.update(buffer, 0, n);
        }
        byte[] actual = digest.digest();
        for (int i = 0; i < actual.length; i++) {
            if ((actual[i] & 255) != Integer.parseInt(expected.substring(i * 2, i * 2 + 2), 16)) {
                throw new IOException("SDK SHA-256 mismatch");
            }
        }
    }

    private void load() throws Exception {
        if (api != null) return;
        if (!java.util.Arrays.asList(Build.SUPPORTED_64_BIT_ABIS).contains("arm64-v8a")) {
            throw new IOException("gfVPN SDK requires ARM64");
        }
        File directory = new File(context.getFilesDir(), "gfvpn-sdk");
        File dex = new File(directory, "classes.dex");
        File lib = new File(directory, "libgojni.so");
        verify(dex, DEX_SHA);
        verify(lib, LIB_SHA);
        // Do not resolve the application's go.Seq (belongs to another Go SDK).
        ClassLoader loader = new DexClassLoader(dex.getAbsolutePath(),
            context.getCodeCacheDir().getAbsolutePath(), directory.getAbsolutePath(),
            context.getClassLoader().getParent());
        Class<?> seq = Class.forName("go.Seq", true, loader);
        seq.getMethod("setContext", Context.class).invoke(null, context);
        Class<?> loadedConfig = Class.forName("gfvpnclient.ClientConfig", true, loader);
        Class<?> loadedApi = Class.forName("gfvpnclient.Gfvpnclient", true, loader);
        configType = loadedConfig;
        api = loadedApi;
    }

    synchronized Session start(String country, String clientIdentifier) throws Exception {
        if (active) throw new IllegalStateException("Already started");
        load();
        if (!java.util.Arrays.asList("RU", "CN", "IR", "TM", "INT").contains(country)) {
            throw new IllegalArgumentException("Unsupported country");
        }
        File work = new File(context.getFilesDir(), "gfvpn-state");
        if (!work.isDirectory() && !work.mkdirs()) throw new IOException("Cannot create work directory");
        Object config = configType.getConstructor().newInstance();
        set(config, "setWorkDir", work.getAbsolutePath());
        set(config, "setLocaleCountryCode", country);
        set(config, "setForceConfigCountry", country);
        set(config, "setClientIdentifier", clientIdentifier);
        set(config, "setServerSelection", "auto");
        set(config, "setNodeRotationEnabled", "true");
        set(config, "setUseCachedNodes", "true");
        set(config, "setUseRemoteConfig", "true");
        set(config, "setSplitTunnelEnabled", "false");
        // Unauthenticated local SOCKS is not acceptable as a default.
        set(config, "setSocksAuthEnabled", "true");
        try {
            active = true;
            Object result = api.getMethod("start", configType).invoke(null, config);
            if (result == null) throw new IOException("SDK returned no result");
            Object error = result.getClass().getMethod("getError").invoke(result);
            if (error != null) throw new IOException("SDK startup failed");
            long port = (Long) result.getClass().getMethod("getPort").invoke(result);
            if (port < 1 || port > 65535) throw new IOException("SDK returned invalid SOCKS port");
            String username = (String) result.getClass().getMethod("getSocksUsername").invoke(result);
            String password = (String) result.getClass().getMethod("getSocksPassword").invoke(result);
            if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
                throw new IOException("SDK returned no SOCKS authentication credentials");
            }
            session = new Session((int) port, username, password);
            return session;
        } catch (Exception | LinkageError failure) {
            try { stop(); } catch (Exception | LinkageError ignored) {
                android.os.Process.killProcess(android.os.Process.myPid());
            }
            throw failure;
        }
    }

    private void set(Object config, String name, String value) throws Exception {
        configType.getMethod(name, String.class).invoke(config, value);
    }

    synchronized void stop() throws Exception {
        if (!active) return;
        api.getMethod("stop").invoke(null);
        active = false;
        session = null;
    }

    synchronized Session requireSession() throws java.io.IOException {
        if (!active || session == null) throw new java.io.IOException("SDK not started");
        return session;
    }

    synchronized long probe() throws Exception {
        if (!active || session == null) throw new IllegalStateException("Not started");
        return GfVpnSocksProbe.probe(session.port, session.username, session.password);
    }

    synchronized void networkChanged() throws Exception {
        if (active) api.getMethod("networkChanged").invoke(null);
    }
}
