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
import kotlinx.coroutines.tasks.await
import java.util.concurrent.atomic.AtomicInteger

class PlayCoreSplitInstallClient(
    private val manager: SplitInstallManager,
) : SplitInstallClient {

    constructor(context: Context) : this(SplitInstallManagerFactory.create(context))

    override val installedModules: Set<String>
        get() = manager.installedModules

    override fun requestInstall(moduleName: String): Flow<InstallResult> = callbackFlow {
        val request = SplitInstallRequest.newBuilder().addModule(moduleName).build()
        val sessionId = AtomicInteger(0)
        val listener = SplitInstallStateUpdatedListener { state: SplitInstallSessionState ->
            val knownId = sessionId.get()
            if (knownId != 0 && state.sessionId() != knownId) return@SplitInstallStateUpdatedListener
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
                    trySend(InstallResult.Failed(reason = "user confirmation required"))
                    close()
                }

                else -> Log.w(TAG, "unknown status=${state.status()}")
            }
        }
        manager.registerListener(listener)
        runCatching { manager.startInstall(request) }
            .onSuccess { task ->
                task
                    .addOnSuccessListener { id ->
                        sessionId.set(id)
                        Log.i(TAG, "install request module=$moduleName session=$id")
                    }
                    .addOnFailureListener { e ->
                        val reason = (e as? com.google.android.play.core.splitinstall.SplitInstallException)
                            ?.let { "code=${it.errorCode}" }
                            ?: e.message.orEmpty()
                        trySend(InstallResult.Failed(reason = reason.ifEmpty { "startInstall failed" }))
                        close(e)
                    }
            }
            .onFailure { e ->
                val reason = (e as? com.google.android.play.core.splitinstall.SplitInstallException)
                    ?.let { "code=${it.errorCode}" }
                    ?: e.message.orEmpty()
                trySend(InstallResult.Failed(reason = reason.ifEmpty { "startInstall crashed" }))
                close(e)
            }
        awaitClose {
            manager.unregisterListener(listener)
            val id = sessionId.get()
            if (id != 0) {
                runCatching { manager.cancelInstall(id) }
                    .onFailure { Log.w(TAG, "cancelInstall threw", it) }
            }
        }
    }

    override suspend fun deferredUninstall(moduleName: String) {
        Log.i(TAG, "deferredUninstall module=$moduleName")
        runCatching { manager.deferredUninstall(listOf(moduleName)).await() }
            .onFailure { Log.w(TAG, "deferredUninstall threw", it) }
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

        @Suppress("unused")
        val ERROR_CODE_REF = SplitInstallErrorCode::class.java
    }
}
