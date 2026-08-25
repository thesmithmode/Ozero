package ru.ozero.app.soak

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.ResultReceiver
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class SoakExternalProbeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        thread(name = "soak-external-http") {
            try {
                val targetUrl = requireNotNull(intent.getStringExtra(EXTRA_URL))
                val expectedMarker = requireNotNull(intent.getStringExtra(EXTRA_EXPECTED_MARKER))
                @Suppress("DEPRECATION")
                val receiver = requireNotNull(intent.getParcelableExtra<ResultReceiver>(EXTRA_RECEIVER))
                val vpnTransport = hasVpnTransport(context)
                val response = request(targetUrl, expectedMarker)
                receiver.send(
                    response.httpCode,
                    Bundle().apply {
                        putBoolean(RESULT_VPN_TRANSPORT, vpnTransport)
                        putBoolean(RESULT_MARKER_MATCH, response.markerMatch)
                    },
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun hasVpnTransport(context: Context): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    private fun request(targetUrl: String, expectedMarker: String): ProbeResponse = runCatching {
        val connection = URL(targetUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = HTTP_TIMEOUT_MS
        connection.readTimeout = HTTP_TIMEOUT_MS
        connection.instanceFollowRedirects = false
        connection.requestMethod = "GET"
        try {
            val code = connection.responseCode
            val body = if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                ""
            }
            ProbeResponse(code, body.trim() == expectedMarker)
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(ProbeResponse(HTTP_FAILED, false))

    private data class ProbeResponse(
        val httpCode: Int,
        val markerMatch: Boolean,
    )

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_EXPECTED_MARKER = "expected_marker"
        const val EXTRA_RECEIVER = "receiver"
        const val RESULT_VPN_TRANSPORT = "vpn_transport"
        const val RESULT_MARKER_MATCH = "marker_match"
        private const val HTTP_TIMEOUT_MS = 10_000
        private const val HTTP_FAILED = -1
    }
}
