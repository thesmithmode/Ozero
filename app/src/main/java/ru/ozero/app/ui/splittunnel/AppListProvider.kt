package ru.ozero.app.ui.splittunnel

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

interface AppListProvider {
    val packageChanges: Flow<Unit>
    suspend fun loadApps(): List<InstalledApp>
    suspend fun refreshApps(): List<InstalledApp>
    suspend fun loadIcon(packageName: String): ImageBitmap?
}

@Singleton
class DefaultAppListProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppListProvider {

    internal constructor(
        context: Context,
        iconCacheMaxEntries: Int,
    ) : this(context) {
        replaceIconCacheForTest(iconCacheMaxEntries)
    }

    @Volatile private var listCache: List<InstalledApp>? = null
    private val cacheGeneration = AtomicInteger()
    private val listMutex = Mutex()
    private val iconCacheLock = Any()
    private var iconCache = LruCache<String, ImageBitmap>(DEFAULT_ICON_CACHE_MAX_ENTRIES)
    private val missingIcons = ConcurrentHashMap.newKeySet<String>()

    override val packageChanges: Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val packageName = intent?.data?.schemeSpecificPart
                invalidatePackage(packageName)
                trySend(Unit)
            }
        }
        val dataFilter = packageChangeFilter().apply {
            addDataScheme("package")
        }
        val noDataFilter = packageChangeFilter()
        val appContext = context.applicationContext
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            dataFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            noDataFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        awaitClose { runCatching { appContext.unregisterReceiver(receiver) } }
    }

    private fun packageChangeFilter(): IntentFilter =
        IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
        }

    override suspend fun loadApps(): List<InstalledApp> {
        listCache?.let { return it }
        return listMutex.withLock {
            listCache?.let { return@withLock it }
            val generation = cacheGeneration.get()
            val loaded = withContext(Dispatchers.IO) { loadMetadata() }
            if (generation == cacheGeneration.get()) {
                listCache = loaded
            }
            loaded
        }
    }

    override suspend fun refreshApps(): List<InstalledApp> {
        return listMutex.withLock {
            listCache = null
            val generation = cacheGeneration.incrementAndGet()
            val loaded = withContext(Dispatchers.IO) { loadMetadata() }
            if (generation == cacheGeneration.get()) {
                listCache = loaded
            }
            loaded
        }
    }

    override suspend fun loadIcon(packageName: String): ImageBitmap? {
        synchronized(iconCacheLock) { iconCache.get(packageName) }?.let { return it }
        if (packageName in missingIcons) return null
        return withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val drawable = runCatching { pm.getApplicationIcon(packageName) }.getOrNull()
            val bitmap = drawable?.let { drawableToImageBitmap(it) }
            if (bitmap != null) {
                synchronized(iconCacheLock) { iconCache.put(packageName, bitmap) }
                logIconCachePressureIfNeeded()
            } else {
                missingIcons.add(packageName)
            }
            bitmap
        }
    }

    private fun loadMetadata(): List<InstalledApp> {
        val pm = context.packageManager
        val ownPackage = context.packageName
        val infos = mutableMapOf<String, ApplicationInfo>()

        pm.getPackagesHoldingPermissions(arrayOf(Manifest.permission.INTERNET), 0)
            .forEach { pi ->
                pi.applicationInfo?.let { infos[pi.packageName] = it }
            }

        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
        if (launcherApps != null) {
            for (profile in launcherApps.profiles) {
                runCatching {
                    val activities = launcherApps.getActivityList(null, profile)
                    for (activity in activities) {
                        val info = activity.applicationInfo
                        if (!infos.containsKey(info.packageName)) {
                            infos[info.packageName] = info
                        }
                    }
                }
            }
        }

        return infos.values.asSequence()
            .filter { it.packageName != ownPackage }
            .map { info ->
                val label = runCatching { info.loadLabel(pm).toString() }
                    .getOrNull()
                    .orEmpty()
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

    private fun invalidatePackage(packageName: String?) {
        listCache = null
        cacheGeneration.incrementAndGet()
        if (packageName.isNullOrBlank()) return
        synchronized(iconCacheLock) { iconCache.remove(packageName) }
        missingIcons.remove(packageName)
    }

    private fun logIconCachePressureIfNeeded() {
        val size = synchronized(iconCacheLock) { iconCache.size() }
        if (size % ICON_CACHE_LOG_EVERY != 0) return
        Log.i(TAG, "icon cache size=$size heap=${heapSummary()}")
    }

    private fun replaceIconCacheForTest(maxEntries: Int) {
        synchronized(iconCacheLock) {
            iconCache = LruCache(maxEntries.coerceAtLeast(1))
        }
    }
}

internal fun holdsInternetPermission(
    permissions: Array<String>?,
): Boolean = permissions?.any { it == Manifest.permission.INTERNET } == true

private fun drawableToImageBitmap(drawable: Drawable): ImageBitmap? = runCatching {
    if (drawable is BitmapDrawable) {
        return@runCatching drawable.bitmap?.scaledForIconCache()?.asImageBitmap()
    }
    val width = drawable.intrinsicWidth.coerceIn(MIN_ICON_PX, MAX_ICON_PX)
    val height = drawable.intrinsicHeight.coerceIn(MIN_ICON_PX, MAX_ICON_PX)
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    bmp.asImageBitmap()
}.getOrNull()

private fun Bitmap.scaledForIconCache(): Bitmap {
    val width = width.coerceAtLeast(MIN_ICON_PX)
    val height = height.coerceAtLeast(MIN_ICON_PX)
    if (width <= MAX_ICON_PX && height <= MAX_ICON_PX) return this
    val scale = MAX_ICON_PX.toFloat() / maxOf(width, height).toFloat()
    val targetWidth = (width * scale).toInt().coerceAtLeast(MIN_ICON_PX)
    val targetHeight = (height * scale).toInt().coerceAtLeast(MIN_ICON_PX)
    return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
}

private fun heapSummary(): String = runCatching {
    val runtime = Runtime.getRuntime()
    val used = runtime.totalMemory() - runtime.freeMemory()
    "used=${used / BYTES_IN_MIB}MiB max=${runtime.maxMemory() / BYTES_IN_MIB}MiB"
}.getOrDefault("unavailable")

private const val MIN_ICON_PX = 1
private const val MAX_ICON_PX = 96
private const val DEFAULT_ICON_CACHE_MAX_ENTRIES = 128
private const val ICON_CACHE_LOG_EVERY = 32
private const val BYTES_IN_MIB = 1024L * 1024L
private const val TAG = "AppListProvider"
