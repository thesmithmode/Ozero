package ru.ozero.enginetor.dynamicmod

import kotlinx.coroutines.flow.Flow

interface SplitInstallClient {
        val installedModules: Set<String>

        fun requestInstall(moduleName: String): Flow<InstallResult>

        suspend fun deferredUninstall(moduleName: String)
}
