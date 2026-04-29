package ru.ozero.app.selfupdate

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

open class UpdateCoordinator(
    private val fetcher: GithubReleaseFetcher,
    private val downloader: ApkDownloader,
    private val verifier: ApkUpdateVerifier,
    private val installer: SilentPackageInstaller,
    private val currentVersion: String,
    private val currentVersionCode: Long,
    private val cacheDir: File,
) {

    private val checkMutex = Mutex()

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
        checkMutex.withLock { runCheck(this) }
    }.flowOn(Dispatchers.IO)

    private suspend fun runCheck(collector: kotlinx.coroutines.flow.FlowCollector<Progress>) {
        Log.i(TAG, "check() current=$currentVersion vc=$currentVersionCode")
        collector.emit(Progress.Checking)

        val release = runCatching { fetcher.latest() }
            .onFailure { Log.e(TAG, "fetcher.latest() threw", it) }
            .getOrNull()
        if (release == null) {
            Log.w(TAG, "no release info from GitHub API")
            collector.emit(Progress.NoRelease)
            return
        }
        Log.i(TAG, "latest release tag=${release.tag} apk=${release.apkUrl}")

        if (!release.apkUrl.startsWith("https://") || !release.sigUrl.startsWith("https://")) {
            Log.e(TAG, "non-HTTPS URL в release info — REJECT")
            collector.emit(Progress.Failed(Progress.Stage.FETCH, "non-HTTPS URL в release"))
            return
        }

        if (!release.isNewerThan(currentVersion)) {
            Log.i(TAG, "release ${release.tag} not newer than current $currentVersion")
            collector.emit(Progress.UpToDate)
            return
        }

        if (release.versionCode > 0 && release.versionCode <= currentVersionCode) {
            Log.w(TAG, "release versionCode=${release.versionCode} <= current=$currentVersionCode → REJECT")
            collector.emit(Progress.UpToDate)
            return
        }

        var apkFile: File? = null
        var sigFile: File? = null
        var downloadFailed: String? = null
        downloader.download(release.apkUrl, release.sigUrl, cacheDir).collect { ev ->
            when (ev) {
                is ApkDownloader.Event.Progress -> collector.emit(Progress.Downloading(ev.percent))
                is ApkDownloader.Event.Success -> {
                    apkFile = ev.apk
                    sigFile = ev.sig
                }
                is ApkDownloader.Event.Failed -> {
                    downloadFailed = ev.reason
                }
            }
        }
        val df = downloadFailed
        if (df != null) {
            Log.e(TAG, "download failed: $df")
            collector.emit(Progress.Failed(Progress.Stage.DOWNLOAD, df))
            return
        }
        val apk = apkFile
        val sig = sigFile
        if (apk == null || sig == null) {
            Log.e(TAG, "download done but files missing")
            collector.emit(Progress.Failed(Progress.Stage.DOWNLOAD, "files missing"))
            return
        }

        collector.emit(Progress.Verifying)
        val ok = runCatching { verifier.verify(apk, sig) }
            .onFailure { Log.e(TAG, "verifier threw", it) }
            .getOrDefault(false)
        if (!ok) {
            Log.e(TAG, "Ed25519 verify FAILED — apk discarded")
            apk.delete()
            sig.delete()
            collector.emit(Progress.Failed(Progress.Stage.VERIFY, "подпись APK невалидна"))
            return
        }
        Log.i(TAG, "Ed25519 verify OK")

        collector.emit(Progress.Installing)
        try {
            when (val res = installer.install(apk)) {
                is SilentPackageInstaller.Result.Submitted -> {
                    Log.i(TAG, "PackageInstaller session committed id=${res.sessionId}")
                    collector.emit(Progress.Submitted(res.sessionId))
                }
                is SilentPackageInstaller.Result.FileError -> {
                    Log.e(TAG, "installer FileError: ${res.reason}")
                    collector.emit(Progress.Failed(Progress.Stage.INSTALL, res.reason))
                }
                is SilentPackageInstaller.Result.IoError -> {
                    Log.e(TAG, "installer IoError sid=${res.sessionId}: ${res.reason}")
                    collector.emit(Progress.Failed(Progress.Stage.INSTALL, res.reason))
                }
                is SilentPackageInstaller.Result.PermissionDenied -> {
                    Log.e(TAG, "installer PermissionDenied: ${res.reason}")
                    collector.emit(Progress.Failed(Progress.Stage.INSTALL, res.reason))
                }
            }
        } finally {
            apk.delete()
            sig.delete()
        }
    }

    private companion object {
        const val TAG = "UpdateCoordinator"
    }
}
