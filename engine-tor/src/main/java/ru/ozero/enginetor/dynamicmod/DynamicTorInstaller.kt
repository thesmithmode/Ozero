package ru.ozero.enginetor.dynamicmod

/**
 * Контракт установки on-demand модуля `:dynamic_tor` (~50 МБ: tor binary +
 * obfs4proxy + snowflake-client + conjure-client).
 *
 * Реализация (DynamicTorInstallerPlayCore) использует PlayCore SplitInstallManager:
 * - запросить установку (если ещё нет)
 * - подписаться на progress
 * - после `INSTALLED` бинари доступны в `nativeLibraryDir` основного app context
 *
 * Stub-реализация для тестов и устройств без Google Play (раздаём APK напрямую) —
 * считаем, что бинари уже в APK (устаревший fallback на размер).
 */
fun interface DynamicTorInstaller {
    suspend fun ensureInstalled(): InstallResult
}

sealed class InstallResult {
    data object AlreadyInstalled : InstallResult()
    data class Installing(val percent: Int) : InstallResult()
    data object Installed : InstallResult()
    data class Failed(val reason: String) : InstallResult()
}
