package ru.ozero.enginegfvpn;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Imports only the exact locally supplied APK; no SDK is included in Ozero's APK. */
public final class GfVpnImport {
    private static final String APK_SHA = "9309781c889578388c250651a9fa3c5c1d4a6e9a0b432dec7e7cc1f93abf57d6";
    private static final long APK_BYTES = 155155974L;

    private GfVpnImport() { }

    public static boolean isInstalled(Context context) {
        File dir = new File(context.getFilesDir(), "gfvpn-sdk");
        return Arrays.asList(Build.SUPPORTED_64_BIT_ABIS).contains("arm64-v8a")
            && new File(dir, "classes.dex").isFile() && new File(dir, "libgojni.so").isFile();
    }

    public static synchronized void install(Context context, Uri uri) throws Exception {
        if (!Arrays.asList(Build.SUPPORTED_64_BIT_ABIS).contains("arm64-v8a")) {
            throw new IOException("ARM64 device required");
        }
        File target = new File(context.getFilesDir(), "gfvpn-sdk");
        if (target.exists()) throw new IOException("SDK directory already exists");
        File stage = new File(context.getFilesDir(), "gfvpn-stage-" + UUID.randomUUID());
        if (!stage.mkdir()) throw new IOException("Cannot create staging directory");
        File apk = new File(stage, "source.apk");
        File dex = new File(stage, "classes.dex");
        File lib = new File(stage, "libgojni.so");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long bytes = 0;
            try (InputStream input = context.getContentResolver().openInputStream(uri);
                 FileOutputStream output = new FileOutputStream(apk)) {
                if (input == null) throw new IOException("Cannot open APK");
                byte[] buffer = new byte[65536];
                for (int count; (count = input.read(buffer)) != -1;) {
                    bytes += count;
                    if (bytes > APK_BYTES) throw new IOException("APK size mismatch");
                    digest.update(buffer, 0, count);
                    output.write(buffer, 0, count);
                }
            }
            if (bytes != APK_BYTES || !hex(digest.digest()).equals(APK_SHA)) {
                throw new IOException("Unsupported APK: SHA-256 mismatch");
            }
            try (ZipFile archive = new ZipFile(apk)) {
                extract(archive, "classes.dex", dex, 5377536L, GfVpnSdk.DEX_SHA);
                extract(archive, "lib/arm64-v8a/libgojni.so", lib, 75485264L, GfVpnSdk.LIB_SHA);
            }
            if (!apk.delete() || !stage.renameTo(target)) throw new IOException("Cannot publish SDK directory");
        } finally {
            apk.delete();
            dex.delete();
            lib.delete();
            stage.delete();
        }
    }

    private static void extract(ZipFile archive, String name, File target, long expectedSize, String hash) throws Exception {
        ZipEntry entry = archive.getEntry(name);
        if (entry == null || entry.getSize() != expectedSize) throw new IOException("SDK entry mismatch");
        long bytes = 0;
        try (InputStream input = archive.getInputStream(entry); FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[65536];
            for (int count; (count = input.read(buffer)) != -1;) {
                bytes += count;
                if (bytes > expectedSize) throw new IOException("SDK entry exceeds size");
                output.write(buffer, 0, count);
            }
        }
        if (bytes != expectedSize || !target.setReadOnly()) throw new IOException("SDK entry not immutable");
        GfVpnSdk.verify(target, hash);
    }

    private static String hex(byte[] value) {
        StringBuilder out = new StringBuilder(value.length * 2);
        for (byte b : value) {
            out.append(Character.forDigit((b & 255) >>> 4, 16));
            out.append(Character.forDigit(b & 15, 16));
        }
        return out.toString();
    }
}
