package ru.ozero.enginegfvpn;

import android.content.Context;
import android.os.Build;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.UUID;

/** Install the pinned runtime from this signed APK, with no user file picker. */
public final class GfVpnBundledSdk {
    private static final String ASSETS = "gfvpn-sdk/";
    private static final String DIRECTORY = "gfvpn-sdk-bundled-v1";

    private GfVpnBundledSdk() { }

    public static boolean isAvailable(Context context) {
        if (!Arrays.asList(Build.SUPPORTED_64_BIT_ABIS).contains("arm64-v8a")) return false;
        try (InputStream dex = context.getAssets().open(ASSETS + "classes.dex");
             InputStream lib = context.getAssets().open(ASSETS + "libgojni.so")) {
            return dex.read() != -1 && lib.read() != -1;
        } catch (IOException missing) {
            return false;
        }
    }

    static synchronized File prepare(Context context) throws Exception {
        if (!isAvailable(context)) throw new IOException("Bundled gfVPN SDK unavailable");
        File target = new File(context.getFilesDir(), DIRECTORY);
        if (target.exists()) {
            try {
                GfVpnSdk.verify(new File(target, "classes.dex"), GfVpnSdk.DEX_SHA);
                GfVpnSdk.verify(new File(target, "libgojni.so"), GfVpnSdk.LIB_SHA);
                return target;
            } catch (IOException invalid) {
                deleteKnownDirectory(target);
            }
        }
        // Only the private gfVPN worker calls prepare(). Reap staging directories
        // left by a process killed during extraction, before making a fresh one.
        File[] stale = context.getFilesDir().listFiles((dir, name) ->
            name.startsWith(DIRECTORY + "-stage-"));
        if (stale != null) {
            for (File old : stale) deleteKnownDirectory(old);
        }
        File stage = new File(context.getFilesDir(), DIRECTORY + "-stage-" + UUID.randomUUID());
        if (!stage.mkdir()) throw new IOException("Cannot create gfVPN SDK staging directory");
        try {
            copy(context, "classes.dex", stage, 5377536L, GfVpnSdk.DEX_SHA);
            copy(context, "libgojni.so", stage, 75485264L, GfVpnSdk.LIB_SHA);
            if (!stage.renameTo(target)) throw new IOException("Cannot publish gfVPN SDK");
            return target;
        } finally {
            if (stage.exists()) deleteKnownDirectory(stage);
        }
    }

    private static void deleteKnownDirectory(File directory) throws IOException {
        for (String name : new String[]{"classes.dex", "libgojni.so"}) {
            File file = new File(directory, name);
            if (file.exists() && !file.delete()) throw new IOException("Cannot remove stale SDK file");
        }
        if (!directory.delete()) throw new IOException("Cannot remove stale SDK directory");
    }

    private static void copy(Context context, String name, File stage, long size, String hash) throws Exception {
        File file = new File(stage, name);
        long total = 0;
        try (InputStream input = context.getAssets().open(ASSETS + name);
             FileOutputStream output = new FileOutputStream(file)) {
            // Android 14 requires dynamically loaded code to be read-only before writing.
            if (!file.setReadOnly()) throw new IOException("Cannot protect bundled SDK file");
            byte[] buffer = new byte[65536];
            for (int n; (n = input.read(buffer)) != -1;) {
                total += n;
                if (total > size) throw new IOException("Invalid bundled SDK size");
                output.write(buffer, 0, n);
            }
            output.getFD().sync();
        }
        if (total != size) throw new IOException("Invalid bundled SDK size");
        GfVpnSdk.verify(file, hash);
    }
}
