package ru.ozero.engineurnetwork

import android.util.Log

class UrnetworkSdkDelegate : UrnetworkDelegate {

    override fun connect(
        jwtToken: String,
        apiUrl: String,
        region: String?,
        mode: UrnetworkMode,
    ): Boolean {
        Log.e(TAG, "URnetworkSdk.aar ещё не собран — connect() unavailable")
        return false
    }

    override fun disconnect() {
        Log.d(TAG, "disconnect (no-op: AAR не собран)")
    }

    override fun connectionStatus(): UrnetworkConnectionStatus {
        return UrnetworkConnectionStatus.DISCONNECTED
    }

    override fun sdkVersion(): String {
        return "URnetworkSdk-not-built"
    }

    private companion object {
        const val TAG = "UrnetworkSdkDelegate"
    }
}
