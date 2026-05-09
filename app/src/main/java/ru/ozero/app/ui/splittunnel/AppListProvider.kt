package ru.ozero.app.ui.splittunnel

import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

interface AppListProvider {
    suspend fun loadApps(): List<InstalledApp>
    suspend fun loadIcon(packageName: String): ImageBitmap?
}

@Singleton
class DefaultAppListProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppListProvider {

    @Volatile private var listCache: List<InstalledApp>? = null
    private val listMutex = Mutex()
    private val iconCache = ConcurrentHashMap<String, ImageBitmap>()
    private val missingIcons = ConcurrentHashMap.newKeySet<String>()

    override suspend fun loadApps(): List<InstalledApp> {
        listCache?.let { return it }
        return listMutex.withLock {
            listCache?.let { return@withLock it }
            val loaded = withContext(Dispatchers.IO) { loadMetadata() }
            listCache = loaded
            loaded
        }
    }

    override suspend fun loadIcon(packageName: String): ImageBitmap? {
        iconCache[packageName]?.let { return it }
        if (packageName in missingIcons) return null
        return withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val drawable = runCatching { pm.getApplicationIcon(packageName) }.getOrNull()
            val bitmap = drawable?.let { drawableToImageBitmap(it) }
            if (bitmap != null) {
                iconCache[packageName] = bitmap
            } else {
                missingIcons.add(packageName)
            }
            bitmap
        }
    }

    private fun loadMetadata(): List<InstalledApp> {
        val pm = context.packageManager
        val ownPackage = context.packageName
        val launchableSet = queryLaunchablePackages(pm)
        val infos = pm.getInstalledApplications(0)
        return infos.asSequence()
            .filter { isUserVisibleApp(it, launchableSet, ownPackage) }
            .map { info ->
                val label = runCatching { info.loadLabel(pm).toString() }.getOrNull().orEmpty()
                    .ifBlank { info.packageName }
                InstalledApp(
                    packageName = info.packageName,
                    label = label,
                    isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    icon = null,
                )
            }
            .sortedWith(compareBy({ it.isSystem }, { it.label.lowercase() }))
            .toList()
    }

    private fun queryLaunchablePackages(pm: PackageManager): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return runCatching {
            pm.queryIntentActivities(intent, 0).mapTo(HashSet()) { it.activityInfo.packageName }
        }.getOrDefault(emptySet())
    }
}

internal fun isUserVisibleApp(
    info: ApplicationInfo,
    launchableSet: Set<String>,
    ownPackage: String,
): Boolean {
    if (info.packageName == ownPackage) return false
    val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
    val isUpdatedSystem = (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    if (!isSystem || isUpdatedSystem) return true
    return info.packageName in launchableSet
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
