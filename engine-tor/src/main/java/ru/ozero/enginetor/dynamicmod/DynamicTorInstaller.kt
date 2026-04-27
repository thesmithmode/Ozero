package ru.ozero.enginetor.dynamicmod

fun interface DynamicTorInstaller {
    suspend fun ensureInstalled(): InstallResult
}

sealed class InstallResult {
    data object AlreadyInstalled : InstallResult()
    data class Installing(val percent: Int) : InstallResult()
    data object Installed : InstallResult()
    data class Failed(val reason: String) : InstallResult()
}
