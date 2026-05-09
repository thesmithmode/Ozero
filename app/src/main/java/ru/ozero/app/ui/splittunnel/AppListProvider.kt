package ru.ozero.app.ui.splittunnel

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
        val ownPackage = context.packageName
        val infos = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        infos.asSequence()
            .filter { isUserVisibleApp(pm, it, ownPackage) }
            .map { info -> toInstalledApp(pm, info) }
            .sortedWith(compareBy({ it.isSystem }, { it.label.lowercase() }))
            .toList()
    }

    private fun toInstalledApp(pm: PackageManager, info: ApplicationInfo): InstalledApp {
        val label = runCatching { info.loadLabel(pm).toString() }.getOrNull().orEmpty()
            .ifBlank { info.packageName }
        val drawable = runCatching { info.loadIcon(pm) }.getOrNull()
        val icon = drawable?.let { drawableToImageBitmap(it) }
        return InstalledApp(
            packageName = info.packageName,
            label = label,
            isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            icon = icon,
        )
    }
}

internal fun isUserVisibleApp(pm: PackageManager, info: ApplicationInfo, ownPackage: String): Boolean {
    if (info.packageName == ownPackage) return false
    val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
    val isUpdatedSystem = (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    if (!isSystem || isUpdatedSystem) return true
    return runCatching { pm.getLaunchIntentForPackage(info.packageName) }.getOrNull() != null
}

private fun drawableToImageBitmap(drawable: Drawable): ImageBitmap? = runCatching {
    if (drawable is BitmapDrawable) {
        return@runCatching drawable.bitmap?.asImageBitmap()
    }
    val width = drawable.intrinsicWidth.coerceAtLeast(MIN_ICON_PX)
    val height = drawable.intrinsicHeight.coerceAtLeast(MIN_ICON_PX)
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    bmp.asImageBitmap()
}.getOrNull()

private const val MIN_ICON_PX = 1
