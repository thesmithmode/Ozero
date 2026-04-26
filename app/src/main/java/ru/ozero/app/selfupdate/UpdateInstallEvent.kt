package ru.ozero.app.selfupdate

import android.content.Intent

/**
 * События self-update flow от [UpdateInstallResultReceiver] к UI-слою (Activity).
 *
 * Receiver регистрируется через manifest и поднимается процессом — DI ему недоступен.
 * Связь с Activity идёт через [UpdateInstallEventBus] (singleton SharedFlow).
 */
sealed class UpdateInstallEvent {
    /**
     * Android 12+ требует пользовательского подтверждения. PackageInstaller отдал
     * Intent для системного confirm-dialog'а — Activity должна вызвать
     * startActivityForResult(intent).
     */
    data class PendingUserAction(val intent: Intent) : UpdateInstallEvent()

    /** APK успешно установлен. session_id для логирования/телеметрии. */
    data class Success(val sessionId: Int) : UpdateInstallEvent()

    /** Установка не удалась. message может быть null при отдельных кодах статуса. */
    data class Failure(
        val sessionId: Int,
        val statusCode: Int,
        val message: String?,
    ) : UpdateInstallEvent()
}
