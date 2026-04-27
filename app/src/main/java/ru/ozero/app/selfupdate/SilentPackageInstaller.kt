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

open class SilentPackageInstaller(
    private val context: Context,
    private val installer: PackageInstaller = context.packageManager.packageInstaller,
) {

    sealed class Result {
        data class Submitted(val sessionId: Int) : Result()

        data class FileError(val reason: String) : Result()

        data class IoError(val sessionId: Int, val reason: String) : Result()
    }

    open suspend fun install(
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
        } catch (e: SecurityException) {
            Log.e(TAG, "createSession denied by system", e)
            return@withContext Result.IoError(sessionId = -1, reason = e.message ?: "createSession denied")
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
        } catch (e: SecurityException) {
            Log.e(TAG, "session $sessionId security denied, abandoning", e)
            runCatching { installer.abandonSession(sessionId) }
                .onFailure { Log.w(TAG, "abandonSession threw", it) }
            Result.IoError(sessionId = sessionId, reason = e.message ?: "session security")
        }
    }

    private fun copyApkToSession(
        session: PackageInstaller.Session,
        apkFile: File,
        sessionName: String,
    ) {
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
