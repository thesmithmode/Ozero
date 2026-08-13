package ru.ozero.app.soak

import android.app.Service
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.IBinder
import android.os.ResultReceiver
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class SoakExternalProbeService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val targetUrl = requireNotNull(intent?.getStringExtra(EXTRA_URL))

        @Suppress("DEPRECATION")
        val receiver = requireNotNull(intent.getParcelableExtra<ResultReceiver>(EXTRA_RECEIVER))
        thread(name = "soak-external-http") {
            val vpnTransport = hasVpnTransport()
            val response = request(targetUrl)
            receiver.send(
                response.httpCode,
                Bundle().apply {
                    putBoolean(RESULT_VPN_TRANSPORT, vpnTransport)
                    putBoolean(RESULT_MARKER_MATCH, response.markerMatch)
                },
            )
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun hasVpnTransport(): Boolean {
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    private fun request(targetUrl: String): ProbeResponse = runCatching {
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
            ProbeResponse(code, body.trim() == EXPECTED_MARKER)
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
        const val EXTRA_RECEIVER = "receiver"
        const val RESULT_VPN_TRANSPORT = "vpn_transport"
        const val RESULT_MARKER_MATCH = "marker_match"
        private const val EXPECTED_MARKER = "ozero-singbox-routed"
        private const val HTTP_TIMEOUT_MS = 10_000
        private const val HTTP_FAILED = -1
    }
}
