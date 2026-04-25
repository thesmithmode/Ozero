package ru.ozero.enginetor.dynamicmod

import android.util.Log
import kotlinx.coroutines.flow.fold

/**
 * Production [DynamicTorInstaller] для устройств с Google Play.
 *
 * Поведение:
 * 1. Если модуль уже установлен — мгновенно [InstallResult.AlreadyInstalled].
 * 2. Иначе — запросить установку через [SplitInstallClient.requestInstall],
 *    подписаться на Flow и вернуть терминальный результат (последний emit).
 *
 * Терминальные состояния: [Installed] / [Failed] / [AlreadyInstalled].
 * Промежуточные [Installing] игнорируются — Engine не показывает прогресс,
 * UI-слой подписывается на Flow напрямую если нужен progress bar.
 */
class PlayCoreDynamicTorInstaller(
    private val client: SplitInstallClient,
    private val moduleName: String = DEFAULT_MODULE_NAME,
) : DynamicTorInstaller {

    override suspend fun ensureInstalled(): InstallResult {
        if (moduleName in client.installedModules) {
            Log.i(TAG, "module=$moduleName already installed")
            return InstallResult.AlreadyInstalled
        }
        Log.i(TAG, "module=$moduleName request install")
        return client.requestInstall(moduleName).fold(
            initial = InstallResult.Failed(reason = "no terminal emit") as InstallResult,
        ) { acc, value ->
            // Прогресс-эмиты не интересуют, нужен только последний терминальный.
            // fold перезаписывает acc на каждом emit, последний и будет результатом.
            if (value is InstallResult.Installing) acc else value
        }
    }

    private companion object {
        const val TAG = "PlayCoreInstaller"
        const val DEFAULT_MODULE_NAME = "dynamic_tor"
    }
}
