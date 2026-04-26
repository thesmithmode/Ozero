package ru.ozero.engineurnetwork

import android.util.Log

/**
 * Production реализация [UrnetworkDelegate].
 *
 * Оборачивает com.bringyour.ConnectViewController из URnetworkSdk.aar
 * (gomobile bind из github.com/urnetwork/sdk).
 *
 * TODO (E15.1): реализовать после успешного прогона urnetwork-aar.yml CI workflow.
 * AAR даёт классы в пакете com.bringyour:
 *   - Sdk.newBringYourClient(apiUrl, jwtToken) → BringYourClient
 *   - BringYourClient.connectViewController → ConnectViewController
 *   - ConnectViewController.connect(location) / .disconnect()
 *   - ConnectViewController.connectionStatus → String ("CONNECTED" / "CONNECTING" / "DISCONNECTED")
 *
 * Пока AAR не собран — бросает UnsupportedOperationException.
 * UrnetworkEngine.start() вернёт StartResult.Failure с понятным сообщением.
 */
class UrnetworkSdkDelegate : UrnetworkDelegate {

    // TODO: lateinit var sdk: com.bringyour.BringYourClient
    // TODO: lateinit var connectVc: com.bringyour.ConnectViewController

    override fun connect(
        jwtToken: String,
        apiUrl: String,
        region: String?,
        mode: UrnetworkMode,
    ): Boolean {
        // TODO: после появления AAR:
        // sdk = com.bringyour.Sdk.newBringYourClient(apiUrl, jwtToken)
        // connectVc = sdk.connectViewController
        // connectVc.connect(region?.let { com.bringyour.ConnectLocation.fromRegion(it) })
        // return true
        Log.e(TAG, "URnetworkSdk.aar ещё не собран — connect() unavailable")
        return false
    }

    override fun disconnect() {
        // TODO: connectVc.disconnect()
        Log.d(TAG, "disconnect (no-op: AAR не собран)")
    }

    override fun connectionStatus(): UrnetworkConnectionStatus {
        // TODO: return when (connectVc.connectionStatus) {
        //     "CONNECTED" → UrnetworkConnectionStatus.CONNECTED
        //     "CONNECTING" → UrnetworkConnectionStatus.CONNECTING
        //     else → UrnetworkConnectionStatus.DISCONNECTED
        // }
        return UrnetworkConnectionStatus.DISCONNECTED
    }

    override fun sdkVersion(): String {
        // TODO: com.bringyour.Sdk.version() или статическое поле
        return "URnetworkSdk-not-built"
    }

    private companion object {
        const val TAG = "UrnetworkSdkDelegate"
    }
}
