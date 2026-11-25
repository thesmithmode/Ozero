package ru.ozero.enginegfvpn;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import hev.TProxyService;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Uses Ozero's existing HEV binary within the gfVPN process only. */
final class GfVpnTun {
    private final File directory;
    private ParcelFileDescriptor attached;
    private File configuration;
    private boolean started;

    GfVpnTun(Context context) {
        directory = new File(context.getCacheDir(), "gfvpn-tun");
    }

    void start(ParcelFileDescriptor descriptor, GfVpnSdk.Session session) throws IOException {
        if (attached != null || started) throw new IOException("TUN already attached");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create TUN directory");
        String yaml = yaml(session.port, session.username, session.password);
        TProxyService.loadOnce();
        if (!TProxyService.getLibraryLoaded()) throw new IOException("Ozero HEV unavailable");
        File path = File.createTempFile("hev-", ".yaml", directory);
        configuration = path;
        try {
            try (FileOutputStream output = new FileOutputStream(path)) {
                output.write(yaml.getBytes(StandardCharsets.UTF_8));
            }
            attached = descriptor;
            // Preserve the received fd until the native runtime is stopped.
            TProxyService.TProxyStartService(path.getAbsolutePath(), descriptor.getFd());
            started = true;
        } catch (Exception | LinkageError failure) {
            // JNI may have started partially. The service terminates the isolated process on failure.
            throw new IOException("HEV startup failed", failure);
        } finally {
            // HEV parses the configuration before the start call returns.
            if (path.delete()) configuration = null;
        }
    }

    boolean isAttached() { return attached != null; }

    boolean isRunning() {
        if (!started || attached == null) return false;
        try {
            long[] stats = TProxyService.TProxyGetStats();
            return stats != null && stats.length >= 4;
        } catch (RuntimeException | LinkageError unavailable) {
            return false;
        }
    }

    void stop() throws IOException {
        try {
            if (started) TProxyService.TProxyStopService();
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

    static String yaml(int port, String username, String password) {
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid SOCKS port");
        return "misc:\n  task-stack-size: 81920\n  connect-timeout: 10000\n  read-write-timeout: 300000\n" +
            "tunnel:\n  mtu: 8500\nsocks5:\n  address: '127.0.0.1'\n  port: " + port +
            "\n  udp: 'tcp'\n  pipeline: 'true'\n  username: " + quote(username) +
            "\n  password: " + quote(password) + "\n";
    }

    private static String quote(String value) {
        if (value == null || value.isEmpty() || value.getBytes(StandardCharsets.UTF_8).length > 255 ||
            value.codePoints().anyMatch(c -> Character.isISOControl(c) || c == 0x2028 || c == 0x2029)) {
            throw new IllegalArgumentException("Invalid SOCKS credential");
        }
        return "'" + value.replace("'", "''") + "'";
    }
}
