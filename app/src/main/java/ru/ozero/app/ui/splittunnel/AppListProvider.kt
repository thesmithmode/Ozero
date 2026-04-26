package ru.ozero.app.ui.splittunnel

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface AppListProvider {
    suspend fun loadApps(): List<InstalledApp>
}

class DefaultAppListProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppListProvider {
    override suspend fun loadApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    label = info.loadLabel(pm).toString(),
                    isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                )
            }
            .sortedWith(compareBy({ it.isSystem }, { it.label.lowercase() }))
    }
}
