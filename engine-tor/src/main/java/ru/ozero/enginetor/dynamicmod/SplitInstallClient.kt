package ru.ozero.enginetor.dynamicmod

import kotlinx.coroutines.flow.Flow

/**
 * Тестируемая абстракция над PlayCore [com.google.android.play.core.splitinstall.SplitInstallManager].
 *
 * SplitInstallManager — final класс, mock-friendly через Mockito-inline тяжёл и
 * хрупок. Эта обёртка имеет минимальную поверхность которую тривиально подменить
 * на FakeSplitInstallClient в unit-тестах [PlayCoreDynamicTorInstallerTest].
 */
interface SplitInstallClient {
    /** Имена уже установленных модулей (snapshot на момент чтения). */
    val installedModules: Set<String>

    /**
     * Запросить установку модуля. Flow эмитит прогресс ([InstallResult.Installing])
     * и завершается терминальным состоянием ([Installed] / [Failed] / [AlreadyInstalled]).
     *
     * Cancellation (отмена корутины) → отмена session_id через PlayCore.
     */
    fun requestInstall(moduleName: String): Flow<InstallResult>

    /**
     * Запросить deferred-uninstall модуля. PlayCore удалит split-pack при следующем
     * подходящем моменте. Используется RT.5.3 при детекте повреждённого/подменённого
     * dynamic_tor: верификатор SHA-256 не сошёлся → выкидываем модуль, чтобы
     * следующий запуск перекачал его с CDN.
     */
    suspend fun deferredUninstall(moduleName: String)
}
