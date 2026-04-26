package ru.ozero.app.selfupdate

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/**
 * Оркестрирует self-update full-flow:
 *   fetch latest release → compare semver → download apk+sig → Ed25519 verify
 *   → PackageInstaller commit → терминальное событие.
 *
 * Flow эмитит [Progress] — UI мапит в [ru.ozero.app.ui.settings.UpdateUiState].
 *
 * Ошибки агрегируются как [Progress.Failed] с указанием [Stage]. Coordinator
 * не бросает исключения наружу — flow всегда завершается терминальным event.
 */
open class UpdateCoordinator(
    private val fetcher: GithubReleaseFetcher,
    private val downloader: ApkDownloader,
    private val verifier: ApkUpdateVerifier,
    private val installer: SilentPackageInstaller,
    private val currentVersion: String,
    private val cacheDir: File,
) {

    sealed class Progress {
        data object Checking : Progress()
        data class Downloading(val percent: Int) : Progress()
        data object Verifying : Progress()
        data object Installing : Progress()
        data object UpToDate : Progress()
        data object NoRelease : Progress()
        data class Submitted(val sessionId: Int) : Progress()
        data class Failed(val stage: Stage, val reason: String) : Progress()

        enum class Stage { FETCH, DOWNLOAD, VERIFY, INSTALL }
    }

    open fun check(): Flow<Progress> = flow {
        Log.i(TAG, "check() current=$currentVersion")
        emit(Progress.Checking)

        val release = runCatching { fetcher.latest() }
            .onFailure { Log.e(TAG, "fetcher.latest() threw", it) }
            .getOrNull()
        if (release == null) {
            Log.w(TAG, "no release info from GitHub API")
            emit(Progress.NoRelease)
            return@flow
        }
        Log.i(TAG, "latest release tag=${release.tag} apk=${release.apkUrl}")

        if (!release.isNewerThan(currentVersion)) {
            Log.i(TAG, "release ${release.tag} not newer than current $currentVersion")
            emit(Progress.UpToDate)
            return@flow
        }

        var apkFile: File? = null
        var sigFile: File? = null
        var downloadFailed: String? = null
        downloader.download(release.apkUrl, release.sigUrl, cacheDir).collect { ev ->
            when (ev) {
                is ApkDownloader.Event.Progress -> emit(Progress.Downloading(ev.percent))
                is ApkDownloader.Event.Success -> {
                    apkFile = ev.apk
                    sigFile = ev.sig
                }
                is ApkDownloader.Event.Failed -> {
                    downloadFailed = ev.reason
                }
            }
        }
        if (downloadFailed != null) {
            Log.e(TAG, "download failed: $downloadFailed")
            emit(Progress.Failed(Progress.Stage.DOWNLOAD, downloadFailed))
            return@flow
        }
        val apk = apkFile
        val sig = sigFile
        if (apk == null || sig == null) {
            Log.e(TAG, "download done but files missing")
            emit(Progress.Failed(Progress.Stage.DOWNLOAD, "files missing"))
            return@flow
        }

        emit(Progress.Verifying)
        val ok = runCatching { verifier.verify(apk, sig) }
            .onFailure { Log.e(TAG, "verifier threw", it) }
            .getOrDefault(false)
        if (!ok) {
            Log.e(TAG, "Ed25519 verify FAILED — apk discarded")
            apk.delete()
            sig.delete()
            emit(Progress.Failed(Progress.Stage.VERIFY, "подпись APK невалидна"))
            return@flow
        }
        Log.i(TAG, "Ed25519 verify OK")

        emit(Progress.Installing)
        when (val res = installer.install(apk)) {
            is SilentPackageInstaller.Result.Submitted -> {
                Log.i(TAG, "PackageInstaller session committed id=${res.sessionId}")
                emit(Progress.Submitted(res.sessionId))
            }
            is SilentPackageInstaller.Result.FileError -> {
                Log.e(TAG, "installer FileError: ${res.reason}")
                emit(Progress.Failed(Progress.Stage.INSTALL, res.reason))
            }
            is SilentPackageInstaller.Result.IoError -> {
                Log.e(TAG, "installer IoError sid=${res.sessionId}: ${res.reason}")
                emit(Progress.Failed(Progress.Stage.INSTALL, res.reason))
            }
        }
    }.flowOn(Dispatchers.IO)

    private companion object {
        const val TAG = "UpdateCoordinator"
    }
}
