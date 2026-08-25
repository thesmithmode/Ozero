package ru.ozero.app.soak;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.ResultReceiver;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public final class SoakExternalProbeReceiver extends BroadcastReceiver {
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_EXPECTED_MARKER = "expected_marker";
    public static final String EXTRA_RECEIVER = "receiver";
    public static final String RESULT_VPN_TRANSPORT = "vpn_transport";
    public static final String RESULT_MARKER_MATCH = "marker_match";
    private static final int HTTP_TIMEOUT_MS = 10_000;
    private static final int HTTP_FAILED = -1;

    @Override
    public void onReceive(Context context, Intent intent) {
        PendingResult pendingResult = goAsync();
        new Thread(() -> {
            try {
                ResultReceiver receiver = intent.getParcelableExtra(EXTRA_RECEIVER);
                if (receiver == null) return;
                ProbeResponse response = request(
                    intent.getStringExtra(EXTRA_URL),
                    intent.getStringExtra(EXTRA_EXPECTED_MARKER)
                );
                Bundle result = new Bundle();
                result.putBoolean(RESULT_VPN_TRANSPORT, hasVpnTransport(context));
                result.putBoolean(RESULT_MARKER_MATCH, response.markerMatch);
                receiver.send(response.httpCode, result);
            } finally {
                pendingResult.finish();
            }
        }, "soak-external-http").start();
    }

    private static boolean hasVpnTransport(Context context) {
        ConnectivityManager connectivity = context.getSystemService(ConnectivityManager.class);
        if (connectivity == null || connectivity.getActiveNetwork() == null) return false;
        NetworkCapabilities capabilities = connectivity.getNetworkCapabilities(connectivity.getActiveNetwork());
        return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
    }

    private static ProbeResponse request(String targetUrl, String expectedMarker) {
        if (targetUrl == null || expectedMarker == null) return new ProbeResponse(HTTP_FAILED, false);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(targetUrl).openConnection();
            connection.setConnectTimeout(HTTP_TIMEOUT_MS);
            connection.setReadTimeout(HTTP_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) return new ProbeResponse(code, false);
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
            }
            return new ProbeResponse(code, body.toString().trim().equals(expectedMarker));
        } catch (Exception ignored) {
            return new ProbeResponse(HTTP_FAILED, false);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static final class ProbeResponse {
        final int httpCode;
        final boolean markerMatch;

        ProbeResponse(int httpCode, boolean markerMatch) {
            this.httpCode = httpCode;
            this.markerMatch = markerMatch;
        }
    }
}
