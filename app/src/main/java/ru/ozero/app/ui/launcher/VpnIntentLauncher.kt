package ru.ozero.app.ui.launcher

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import ru.ozero.app.logging.BootFileLogger
import ru.ozero.app.ui.MainViewModel
import ru.ozero.commonvpn.OzeroVpnService
import ru.ozero.commonvpn.TunnelState

class VpnIntentLauncher(
    private val activity: ComponentActivity,
    private val viewModel: MainViewModel,
    private val vpnPermissionLauncher: ActivityResultLauncher<Intent>,
    private val notificationPermissionLauncher: ActivityResultLauncher<String>,
    private val onStarted: () -> Unit = {},
) {
    fun onConnectClick() {
        val current = viewModel.state.value
        runCatching { BootFileLogger.info(TAG, "onConnectClick state=${current::class.simpleName}") }
        when (current) {
            is TunnelState.Connected -> {
                viewModel.onConnectClick()
                stop()
            }
            is TunnelState.Idle, is TunnelState.Failed -> requestVpnAndStart()
            else -> Unit
        }
    }

    fun start() {
        runCatching { BootFileLogger.info(TAG, "startForegroundService(OzeroVpnService, ACTION_START)") }
        ContextCompat.startForegroundService(
            activity,
            Intent(activity, OzeroVpnService::class.java).apply {
                action = OzeroVpnService.ACTION_START
            },
        )
        onStarted()
    }

    fun stop() {
        ContextCompat.startForegroundService(
            activity,
            Intent(activity, OzeroVpnService::class.java).apply {
                action = OzeroVpnService.ACTION_STOP
            },
        )
    }

    fun onVpnPermissionResult(resultCode: Int) {
        runCatching { BootFileLogger.info(TAG, "vpnPermissionLauncher result code=$resultCode") }
        if (resultCode == Activity.RESULT_OK) {
            viewModel.onVpnPermissionGranted()
            start()
        } else {
            viewModel.onVpnPermissionDenied()
        }
    }

    fun onNotificationPermissionResult() {
        runCatching { BootFileLogger.info(TAG, "notificationPermissionLauncher result") }
        proceedToVpnRequest()
    }

    private fun requestVpnAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                activity,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                runCatching { BootFileLogger.info(TAG, "POST_NOTIFICATIONS not granted → request") }
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        proceedToVpnRequest()
    }

    private fun proceedToVpnRequest() {
        viewModel.onConnectClick()
        val vpnIntent = VpnService.prepare(activity)
        if (vpnIntent != null) {
            runCatching { BootFileLogger.info(TAG, "VpnService.prepare → permission dialog") }
            vpnPermissionLauncher.launch(vpnIntent)
        } else {
            runCatching { BootFileLogger.info(TAG, "VpnService.prepare null → startVpnService") }
            viewModel.onVpnPermissionGranted()
            start()
        }
    }

    private companion object {
        const val TAG = "VpnIntentLauncher"
    }
}
