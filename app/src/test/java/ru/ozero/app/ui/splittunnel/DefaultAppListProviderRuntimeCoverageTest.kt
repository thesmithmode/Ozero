package ru.ozero.app.ui.splittunnel

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowPackageManager
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DefaultAppListProviderRuntimeCoverageTest {

    @Test
    fun `loadApps cache is reused until broadcast invalidates it`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val shadowPm = packageManager()
        shadowPm.installPackage(packageInfo("pkg.one", "One", system = false))
        val provider = DefaultAppListProvider(context)

        val first = provider.loadApps()
        val cached = provider.loadApps()
        shadowPm.installPackage(packageInfo("pkg.two", "Two", system = false))
        provider.invalidateForTest("pkg.two")
        val refreshed = provider.loadApps()

        assertEquals(listOf("pkg.one"), first.map { it.packageName })
        assertEquals(listOf("pkg.one"), cached.map { it.packageName })
        assertTrue(refreshed.any { it.packageName == "pkg.two" })
    }

    @Test
    fun `loadIcon caches missing result until invalidated and then reloads`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val shadowPm = packageManager()
        val provider = DefaultAppListProvider(context)

        assertNull(provider.loadIcon("pkg.missing"))
        shadowPm.installPackage(packageInfo("pkg.missing", "Missing", system = false))
        shadowPm.setApplicationIcon(
            "pkg.missing",
            BitmapDrawable(context.resources, Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)),
        )
        assertNull(provider.loadIcon("pkg.missing"))
        provider.invalidateForTest("pkg.missing")

        assertNotNull(provider.loadIcon("pkg.missing"))
    }

    @Test
    fun `packageChanges emits and invalidates list cache on broadcast`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val shadowPm = packageManager()
        shadowPm.installPackage(packageInfo("pkg.broadcast", "Broadcast", system = false))
        val provider = DefaultAppListProvider(context)
        val collector = launch { provider.packageChanges.collect { } }

        try {
            provider.loadApps()
            shadowPm.installPackage(packageInfo("pkg.added", "Added", system = false))
            context.sendBroadcast(
                Intent(Intent.ACTION_PACKAGE_REPLACED).apply {
                    data = android.net.Uri.parse("package:pkg.broadcast")
                },
            )
            shadowOf(android.os.Looper.getMainLooper()).idle()
            advanceUntilIdle()

            assertTrue(provider.loadApps().any { it.packageName == "pkg.added" })
        } finally {
            collector.cancel()
        }
    }

    private fun DefaultAppListProvider.invalidateForTest(packageName: String?) {
        val method = javaClass.getDeclaredMethod("invalidatePackage", String::class.java)
        method.isAccessible = true
        method.invoke(this, packageName)
    }

    private fun packageInfo(
        packageName: String,
        label: String,
        system: Boolean,
    ) = android.content.pm.PackageInfo().apply {
        this.packageName = packageName
        requestedPermissions = arrayOf(android.Manifest.permission.INTERNET)
        applicationInfo = android.content.pm.ApplicationInfo().apply {
            this.packageName = packageName
            name = packageName
            nonLocalizedLabel = label
            flags = if (system) android.content.pm.ApplicationInfo.FLAG_SYSTEM else 0
        }
    }

    private fun packageManager(): ShadowPackageManager =
        Shadow.extract(RuntimeEnvironment.getApplication().packageManager)
}
