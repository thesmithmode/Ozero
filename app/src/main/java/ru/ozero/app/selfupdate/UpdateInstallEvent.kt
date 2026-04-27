package ru.ozero.app.selfupdate

import android.content.Intent

sealed class UpdateInstallEvent {
    data class PendingUserAction(val intent: Intent) : UpdateInstallEvent()

    data class Success(val sessionId: Int) : UpdateInstallEvent()

    data class Failure(
        val sessionId: Int,
        val statusCode: Int,
        val message: String?,
    ) : UpdateInstallEvent()
}
