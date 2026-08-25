package ru.ozero.app.soak;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import javax.net.ssl.SSLException;

import ru.ozero.enginescore.LogSanitizer;

public final class SoakExternalProbeReceiver extends BroadcastReceiver {
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_EXPECTED_MARKER = "expected_marker";
    public static final String EXTRA_RESULT_ACTION = "result_action";
    public static final String EXTRA_RESULT_PACKAGE = "result_package";
    public static final String RESULT_HTTP_CODE = "http_code";
    public static final String RESULT_VPN_TRANSPORT = "vpn_transport";
    public static final String RESULT_MARKER_MATCH = "marker_match";
    public static final String RESULT_STAGE = "stage";
    public static final String RESULT_EXCEPTION_CLASS = "exception_class";
    public static final String RESULT_SANITIZED_MESSAGE = "sanitized_message";
    public static final String RESULT_ELAPSED_MS = "elapsed_ms";
    private static final int HTTP_TIMEOUT_MS = 10_000;
    private static final int HTTP_FAILED = -1;

    @Override
    public void onReceive(Context context, Intent intent) {
        PendingResult pendingResult = goAsync();
        new Thread(() -> {
            try {
                ProbeResponse response = request(
                    intent.getStringExtra(EXTRA_URL),
                    intent.getStringExtra(EXTRA_EXPECTED_MARKER)
                );
                String resultAction = intent.getStringExtra(EXTRA_RESULT_ACTION);
                String resultPackage = intent.getStringExtra(EXTRA_RESULT_PACKAGE);
                if (resultAction == null || resultPackage == null) return;
                Intent result = new Intent(resultAction).setPackage(resultPackage);
                result.putExtra(RESULT_HTTP_CODE, response.httpCode);
                result.putExtra(RESULT_VPN_TRANSPORT, hasVpnTransport(context));
                result.putExtra(RESULT_MARKER_MATCH, response.markerMatch);
                result.putExtra(RESULT_STAGE, response.stage);
                result.putExtra(RESULT_EXCEPTION_CLASS, response.exceptionClass);
                result.putExtra(RESULT_SANITIZED_MESSAGE, response.sanitizedMessage);
                result.putExtra(RESULT_ELAPSED_MS, response.elapsedMs);
                context.sendBroadcast(result);
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
        long startedAt = System.nanoTime();
        if (targetUrl == null || expectedMarker == null) {
            return ProbeResponse.failure("request", "IllegalArgumentException", "missing probe input", startedAt);
        }
        HttpURLConnection connection = null;
        String stage = "connect";
        try {
            connection = (HttpURLConnection) new URL(targetUrl).openConnection();
            connection.setConnectTimeout(HTTP_TIMEOUT_MS);
            connection.setReadTimeout(HTTP_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            stage = "response";
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) return ProbeResponse.success(code, false, startedAt);
            stage = "response";
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
            }
            return ProbeResponse.success(code, body.toString().trim().equals(expectedMarker), startedAt);
        } catch (Exception error) {
            String resolvedStage = classifyStage(stage, error);
            ProbeResponse failure = ProbeResponse.failure(
                resolvedStage,
                error.getClass().getSimpleName(),
                LogSanitizer.INSTANCE.sanitize(error.getMessage() == null ? "" : error.getMessage()),
                startedAt
            );
            Log.w("SoakExternalProbe", "stage=" + failure.stage + " exceptionClass=" + failure.exceptionClass
                + " sanitizedMessage=" + failure.sanitizedMessage + " elapsedMs=" + failure.elapsedMs);
            return failure;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String classifyStage(String stage, Exception error) {
        if (error instanceof UnknownHostException) return "dns";
        if (error instanceof SSLException) return "tls";
        return stage;
    }

    private static final class ProbeResponse {
        final int httpCode;
        final boolean markerMatch;
        final String stage;
        final String exceptionClass;
        final String sanitizedMessage;
        final long elapsedMs;

        ProbeResponse(int httpCode, boolean markerMatch, String stage, String exceptionClass, String sanitizedMessage, long elapsedMs) {
            this.httpCode = httpCode;
            this.markerMatch = markerMatch;
            this.stage = stage;
            this.exceptionClass = exceptionClass;
            this.sanitizedMessage = sanitizedMessage;
            this.elapsedMs = elapsedMs;
        }

        static ProbeResponse success(int code, boolean markerMatch, long startedAt) {
            return new ProbeResponse(code, markerMatch, "response", "none", "none", elapsed(startedAt));
        }

        static ProbeResponse failure(String stage, String exceptionClass, String message, long startedAt) {
            return new ProbeResponse(HTTP_FAILED, false, stage, exceptionClass, message, elapsed(startedAt));
        }

        private static long elapsed(long startedAt) {
            return (System.nanoTime() - startedAt) / 1_000_000L;
        }
    }
}
