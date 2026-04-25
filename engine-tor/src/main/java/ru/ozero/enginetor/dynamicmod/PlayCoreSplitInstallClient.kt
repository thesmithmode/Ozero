package ru.ozero.enginetor.dynamicmod

import android.content.Context
import android.util.Log
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallErrorCode
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Production-обёртка над PlayCore [SplitInstallManager].
 *
 * Маппит [SplitInstallSessionStatus] → [InstallResult]:
 * - DOWNLOADING / INSTALLING / PENDING / DOWNLOADED → [InstallResult.Installing]
 * - INSTALLED → [InstallResult.Installed] (закрывает Flow)
 * - FAILED / CANCELED / REQUIRES_USER_CONFIRMATION → [InstallResult.Failed] (закрывает Flow)
 *
 * Cancel корутины → cancelInstall(sessionId) — PlayCore не освобождает скачанный
 * payload до cancel, без него прервать загрузку нельзя.
 */
class PlayCoreSplitInstallClient(
    private val manager: SplitInstallManager,
) : SplitInstallClient {

    constructor(context: Context) : this(SplitInstallManagerFactory.create(context))

    override val installedModules: Set<String>
        get() = manager.installedModules

    override fun requestInstall(moduleName: String): Flow<InstallResult> = callbackFlow {
        val request = SplitInstallRequest.newBuilder().addModule(moduleName).build()
        var sessionId = 0
        val listener = SplitInstallStateUpdatedListener { state: SplitInstallSessionState ->
            if (state.sessionId() != sessionId) return@SplitInstallStateUpdatedListener
            when (state.status()) {
                SplitInstallSessionStatus.PENDING,
                SplitInstallSessionStatus.DOWNLOADING,
                SplitInstallSessionStatus.DOWNLOADED,
                SplitInstallSessionStatus.INSTALLING,
                -> trySend(InstallResult.Installing(percent = computePercent(state)))

                SplitInstallSessionStatus.INSTALLED -> {
                    trySend(InstallResult.Installed)
                    close()
                }

                SplitInstallSessionStatus.FAILED -> {
                    trySend(InstallResult.Failed(reason = "code=${state.errorCode()}"))
                    close()
                }

                SplitInstallSessionStatus.CANCELED -> {
                    trySend(InstallResult.Failed(reason = "canceled"))
                    close()
                }

                SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> {
                    // RT.5 не показывает UI-confirm: модуль ~200 МБ, требуется пользовательское
                    // согласие на large download через системный диалог. Доставка этого диалога
                    // — задача UI-слоя (Activity.startIntentSender) в RT.4. Пока считаем как Failed.
                    trySend(InstallResult.Failed(reason = "user confirmation required"))
                    close()
                }

                else -> Log.w(TAG, "unknown status=${state.status()}")
            }
        }
        manager.registerListener(listener)
        manager.startInstall(request)
            .addOnSuccessListener { id ->
                sessionId = id
                Log.i(TAG, "install request module=$moduleName session=$id")
            }
            .addOnFailureListener { e ->
                val reason = (e as? com.google.android.play.core.splitinstall.SplitInstallException)
                    ?.let { "code=${it.errorCode}" }
                    ?: e.message.orEmpty()
                trySend(InstallResult.Failed(reason = reason.ifEmpty { "startInstall failed" }))
                close(e)
            }
        awaitClose {
            manager.unregisterListener(listener)
            if (sessionId != 0) {
                runCatching { manager.cancelInstall(sessionId) }
                    .onFailure { Log.w(TAG, "cancelInstall threw", it) }
            }
        }
    }

    private fun computePercent(state: SplitInstallSessionState): Int {
        val total = state.totalBytesToDownload()
        if (total <= 0L) return 0
        val downloaded = state.bytesDownloaded().coerceAtLeast(0L)
        return ((downloaded * PERCENT_BASE) / total).toInt().coerceIn(0, PERCENT_BASE)
    }

    private companion object {
        const val TAG = "PlayCoreSplitInstall"
        const val PERCENT_BASE = 100

        // SplitInstallErrorCode задействован неявно через state.errorCode() — keep import
        // живым чтобы рефакторинг не выкинул его при чистке (нужен для отладки кодов).
        @Suppress("unused")
        val ERROR_CODE_REF = SplitInstallErrorCode::class.java
    }
}
