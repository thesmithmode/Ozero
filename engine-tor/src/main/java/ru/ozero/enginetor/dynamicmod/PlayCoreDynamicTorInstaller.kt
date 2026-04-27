package ru.ozero.enginetor.dynamicmod

import android.util.Log
import kotlinx.coroutines.flow.fold
import java.io.File

class PlayCoreDynamicTorInstaller(
    private val client: SplitInstallClient,
    private val verifier: TorBinaryVerifier,
    private val nativeLibDirProvider: () -> File,
    private val currentAbi: () -> String,
    private val moduleName: String = DEFAULT_MODULE_NAME,
) : DynamicTorInstaller {

    override suspend fun ensureInstalled(): InstallResult {
        if (moduleName in client.installedModules) {
            Log.i(TAG, "module=$moduleName already installed — verify checksums")
            return verifyAndMap(installedResult = InstallResult.AlreadyInstalled)
        }
        Log.i(TAG, "module=$moduleName request install")
        val raw = client.requestInstall(moduleName).fold(
            initial = InstallResult.Failed(reason = "no terminal emit") as InstallResult,
        ) { acc, value ->
                        if (value is InstallResult.Installing) acc else value
        }
        return when (raw) {
            is InstallResult.Installed -> verifyAndMap(installedResult = InstallResult.Installed)
            is InstallResult.AlreadyInstalled -> verifyAndMap(installedResult = raw)
            is InstallResult.Failed, is InstallResult.Installing -> raw
        }
    }

    private suspend fun verifyAndMap(installedResult: InstallResult): InstallResult {
        val abi = currentAbi()
        val dir = nativeLibDirProvider()
        return when (val v = verifier.verify(abi, dir)) {
            VerifyResult.Ok -> installedResult
            is VerifyResult.Missing -> {
                Log.e(TAG, "missing binary=${v.fileName} → deferredUninstall")
                client.deferredUninstall(moduleName)
                InstallResult.Failed(reason = "missing binary: ${v.fileName}")
            }
            is VerifyResult.Corrupted -> {
                Log.e(
                    TAG,
                    "checksum mismatch file=${v.fileName} expected=${v.expected} actual=${v.actual}",
                )
                client.deferredUninstall(moduleName)
                InstallResult.Failed(
                    reason = "checksum mismatch: ${v.fileName} expected=${v.expected.take(8)}… " +
                        "actual=${v.actual.take(8)}…",
                )
            }
            is VerifyResult.UnknownAbi -> {
                Log.e(TAG, "unknown abi=${v.abi} — нет checksums, REJECT")
                client.deferredUninstall(moduleName)
                InstallResult.Failed(reason = "unknown abi: ${v.abi} — нет эталонных checksums")
            }
        }
    }

    private companion object {
        const val TAG = "PlayCoreInstaller"
        const val DEFAULT_MODULE_NAME = "dynamic_tor"
    }
}
