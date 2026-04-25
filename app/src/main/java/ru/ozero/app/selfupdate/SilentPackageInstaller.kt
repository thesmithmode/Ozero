package ru.ozero.app.selfupdate

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Установщик скачанного и верифицированного APK через [PackageInstaller].
 *
 * Поведение:
 * - Создаёт MODE_FULL_INSTALL session, копирует байты APK через openWrite().
 * - Коммитит с IntentSender — system показывает install-confirmation UI
 *   (без UPDATE_PACKAGES_WITHOUT_USER_ACTION на Android 12+ диалог обязателен).
 * - Прерывание session при ошибке записи: abandon() вместо commit, чтобы
 *   PackageInstaller не оставил orphan staging directory на диске.
 *
 * Безопасность:
 *   APK должен быть предварительно проверен [ApkUpdateVerifier] (Ed25519).
 *   Этот класс НЕ верифицирует подпись — он просто устанавливает что дали.
 *   Вызывающий обязан вызвать verify() перед install().
 */
class SilentPackageInstaller(
    private val context: Context,
    private val installer: PackageInstaller = context.packageManager.packageInstaller,
) {

    sealed class Result {
        /** Сессия успешно создана и зафиксирована. Дальше system покажет confirm-dialog. */
        data class Submitted(val sessionId: Int) : Result()

        /** Файл APK не найден или нечитаем. До PackageInstaller дело не дошло. */
        data class FileError(val reason: String) : Result()

        /** PackageInstaller вернул IOException на createSession / openWrite / commit. */
        data class IoError(val sessionId: Int, val reason: String) : Result()
    }

    /**
     * @param apkFile файл APK для установки. Должен существовать и быть читаемым.
     * @param sessionName идентификатор session — попадает в logs PackageInstaller.
     * @param resultIntentAction кастомный action для broadcast, через который
     *  PackageInstaller сообщает финальный статус. Если null — используется DEFAULT_ACTION.
     */
    suspend fun install(
        apkFile: File,
        sessionName: String = DEFAULT_SESSION_NAME,
        resultIntentAction: String = DEFAULT_RESULT_ACTION,
    ): Result = withContext(Dispatchers.IO) {
        if (!apkFile.exists() || !apkFile.canRead()) {
            Log.e(TAG, "apk недоступен: ${apkFile.absolutePath}")
            return@withContext Result.FileError("apk недоступен: ${apkFile.absolutePath}")
        }

        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply {
                setAppPackageName(context.packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
            }

        val sessionId = try {
            installer.createSession(params)
        } catch (e: IOException) {
            Log.e(TAG, "createSession fail", e)
            return@withContext Result.IoError(sessionId = -1, reason = e.message ?: "createSession")
        }

        try {
            installer.openSession(sessionId).use { session ->
                copyApkToSession(session, apkFile, sessionName)
                session.commit(buildResultSender(sessionId, resultIntentAction))
            }
            Log.i(TAG, "session $sessionId committed для ${apkFile.name}")
            Result.Submitted(sessionId)
        } catch (e: IOException) {
            Log.e(TAG, "session $sessionId fail, abandoning", e)
            runCatching { installer.abandonSession(sessionId) }
                .onFailure { Log.w(TAG, "abandonSession threw", it) }
            Result.IoError(sessionId = sessionId, reason = e.message ?: "session io")
        }
    }

    private fun copyApkToSession(
        session: PackageInstaller.Session,
        apkFile: File,
        sessionName: String,
    ) {
        // setStagingProgress принимает только аппроксимацию — точный progress
        // PackageInstaller отдаёт через ProgressListener, нам он не нужен.
        session.openWrite(sessionName, 0, apkFile.length()).use { sink ->
            apkFile.inputStream().use { src ->
                src.copyTo(sink, bufferSize = COPY_BUFFER)
                session.fsync(sink)
            }
        }
    }

    private fun buildResultSender(sessionId: Int, action: String): IntentSender {
        val intent = Intent(action).setPackage(context.packageName)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
        return pending.intentSender
    }

    private companion object {
        const val TAG = "SilentPackageInstaller"
        const val DEFAULT_SESSION_NAME = "ozero-update.apk"
        const val DEFAULT_RESULT_ACTION = "ru.ozero.app.UPDATE_INSTALL_RESULT"
        const val COPY_BUFFER = 64 * 1024
    }
}
