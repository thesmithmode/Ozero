package ru.ozero.enginegfvpn;

import android.content.Context;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import hev.TProxyService;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Uses a dedicated mapdns-capable HEV on Android 10+, or the legacy HEV on older Android. */
final class GfVpnTun {
    private final File directory;
    private ParcelFileDescriptor attached;
    private File configuration;
    private boolean started;
    private final boolean mapDns = supportsMapDns(Build.VERSION.SDK_INT);

    GfVpnTun(Context context) {
        directory = new File(context.getCacheDir(), "gfvpn-tun");
    }

    void start(ParcelFileDescriptor descriptor, GfVpnSdk.Session session) throws IOException {
        if (attached != null || started) throw new IOException("TUN already attached");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create TUN directory");
        // A previous isolated process may have been killed by the watchdog.
        // No runtime is attached in this process, so its stale configs can be removed.
        File[] stale = directory.listFiles((dir, name) -> name.startsWith("hev-") && name.endsWith(".yaml"));
        if (stale != null) {
            for (File old : stale) {
                if (!old.delete()) throw new IOException("Cannot clear stale HEV configuration");
            }
        }
        String yaml = yaml(session.port, session.username, session.password, mapDns);
        if (!mapDns) {
            TProxyService.loadOnce();
            if (!TProxyService.getLibraryLoaded()) throw new IOException("Ozero HEV unavailable");
        }
        File path = File.createTempFile("hev-", ".yaml", directory);
        configuration = path;
        try {
            try (FileOutputStream output = new FileOutputStream(path)) {
                output.write(yaml.getBytes(StandardCharsets.UTF_8));
            }
            attached = descriptor;
            // Preserve the received fd until the native runtime is stopped.
            if (mapDns) {
                if (!hev.htproxy.TProxyService.TProxyStartService(path.getAbsolutePath(), descriptor.getFd())) {
                    throw new IOException("gfVPN HEV refused to start");
                }
            } else {
                TProxyService.TProxyStartService(path.getAbsolutePath(), descriptor.getFd());
            }
            started = true;
        } catch (Exception | LinkageError failure) {
            // JNI may have started partially. The service terminates the isolated process on failure.
            throw new IOException("HEV startup failed", failure);
        }
        // Both HEV versions start asynchronously. Keep the private YAML until native stop joins.
    }

    boolean isAttached() { return attached != null; }

    long[] counters() throws IOException {
        if (!started || attached == null) throw new IOException("HEV not attached");
        try {
            if (mapDns && !hev.htproxy.TProxyService.TProxyIsRunning()) {
                throw new IOException("gfVPN HEV stopped");
            }
            return GfVpnTunCounters.validate(mapDns
                ? hev.htproxy.TProxyService.TProxyGetStats() : TProxyService.TProxyGetStats());
        } catch (RuntimeException | LinkageError unavailable) {
            throw new IOException("HEV unavailable", unavailable);
        }
    }

    void stop() throws IOException {
        try {
            if (started) {
                if (mapDns) {
                    if (!hev.htproxy.TProxyService.TProxyStopService()) {
                        throw new IOException("gfVPN HEV failed to stop");
                    }
                } else {
                    TProxyService.TProxyStopService();
                }
            }
        } finally {
            started = false;
            try {
                if (attached != null) attached.close();
            } finally {
                attached = null;
                if (configuration != null) configuration.delete();
                configuration = null;
            }
        }
    }

    static boolean supportsMapDns(int sdk) { return sdk >= 29; }

    static String dnsAddress(int sdk) { return supportsMapDns(sdk) ? "198.18.0.2" : "1.1.1.1"; }

    static String yaml(int port, String username, String password) {
        return yaml(port, username, password, false);
    }

    static String yaml(int port, String username, String password, boolean mapDns) {
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid SOCKS port");
        String base = "misc:\n  task-stack-size: 81920\n  connect-timeout: 10000\n  read-write-timeout: 300000\n" +
            "tunnel:\n  mtu: 8500\nsocks5:\n  address: '127.0.0.1'\n  port: " + port +
            "\n  udp: 'tcp'\n  pipeline: 'true'\n  username: " + quote(username) +
            "\n  password: " + quote(password) + "\n";
        // HEV resolves synthetic IPv4 destinations by domain through the authenticated SOCKS route.
        return mapDns ? base + "mapdns:\n  address: '198.18.0.2'\n  port: 53\n" +
            "  network: '100.64.0.0'\n  netmask: '255.192.0.0'\n  cache-size: 10000\n" : base;
    }

    private static String quote(String value) {
        if (value == null || value.isEmpty() || value.getBytes(StandardCharsets.UTF_8).length > 255 ||
            value.codePoints().anyMatch(c -> Character.isISOControl(c) || c == 0x2028 || c == 0x2029)) {
            throw new IllegalArgumentException("Invalid SOCKS credential");
        }
        return "'" + value.replace("'", "''") + "'";
    }
}
