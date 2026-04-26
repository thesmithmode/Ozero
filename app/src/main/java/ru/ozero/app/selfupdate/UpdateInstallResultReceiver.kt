package ru.ozero.app.selfupdate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log

/**
 * Принимает result-broadcast от [PackageInstaller] и маршрутизирует в
 * [UpdateInstallEventBus]. PendingIntent на этот receiver формирует
 * [SilentPackageInstaller] при commit() сессии.
 *
 * Регистрация в AndroidManifest.xml (exported=false): broadcast приходит с
 * Intent.setPackage(this), внешним приложениям доступ не нужен.
 */
class UpdateInstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) {
            Log.w(TAG, "ignoring foreign action=${intent.action}")
            return
        }
        val statusCode = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE,
        )
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        Log.i(TAG, "session=$sessionId status=$statusCode message=$message")

        val event = when (statusCode) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = intent.confirmIntent()
                if (confirm == null) {
                    Log.e(TAG, "session=$sessionId PENDING_USER_ACTION без EXTRA_INTENT")
                    UpdateInstallEvent.Failure(
                        sessionId = sessionId,
                        statusCode = statusCode,
                        message = "missing EXTRA_INTENT",
                    )
                } else {
                    UpdateInstallEvent.PendingUserAction(confirm)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> UpdateInstallEvent.Success(sessionId)
            else -> UpdateInstallEvent.Failure(
                sessionId = sessionId,
                statusCode = statusCode,
                message = message,
            )
        }
        UpdateInstallEventBus.emit(event)
    }

    private fun Intent.confirmIntent(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_INTENT)
        }

    companion object {
        const val ACTION = "ru.ozero.app.UPDATE_INSTALL_RESULT"
        private const val TAG = "UpdateInstallReceiver"
    }
}
