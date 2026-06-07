package ru.ozero.app.ui.splittunnel

import android.Manifest
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowPackageManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DefaultAppListProviderRuntimeTest {

    @Before
    fun setUp() {
        packageManager().removePackage("pkg.system")
        packageManager().removePackage("pkg.alpha")
        packageManager().removePackage("pkg.beta")
        packageManager().removePackage("pkg.nointernet")
        packageManager().removePackage("pkg.one")
        packageManager().removePackage("pkg.two")
        packageManager().removePackage("pkg.icon")
        packageManager().removePackage("pkg.missing")
        packageManager().removePackage("pkg.added")
    }

    @Test
    fun `loadApps returns INTERNET packages excluding own package sorted user first`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val shadowPm = packageManager()
        shadowPm.installPackage(packageInfo(context.packageName, "Ozero", system = false))
        shadowPm.installPackage(packageInfo("pkg.system", "System App", system = true))
        shadowPm.installPackage(packageInfo("pkg.alpha", "Alpha", system = false))
        shadowPm.installPackage(packageInfo("pkg.beta", "Beta", system = false))
        shadowPm.installPackage(packageInfo("pkg.nointernet", "No Internet", system = false, internet = false))

        val apps = DefaultAppListProvider(context).loadApps()

        assertEquals(listOf("pkg.alpha", "pkg.beta", "pkg.system"), apps.map { it.packageName })
        assertFalse(apps.any { it.packageName == context.packageName })
        assertFalse(apps.any { it.packageName == "pkg.nointernet" })
        assertTrue(apps.last().isSystem)
    }

    @Test
    fun `loadApps uses cached metadata until refreshApps`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val shadowPm = packageManager()
        shadowPm.installPackage(packageInfo("pkg.one", "One", system = false))
        val provider = DefaultAppListProvider(context)

        val first = provider.loadApps()
        shadowPm.installPackage(packageInfo("pkg.two", "Two", system = false))
        val cached = provider.loadApps()
        val refreshed = provider.refreshApps()

        assertEquals(listOf("pkg.one"), first.map { it.packageName })
        assertEquals(listOf("pkg.one"), cached.map { it.packageName })
        assertEquals(listOf("pkg.one", "pkg.two"), refreshed.map { it.packageName })
    }

    @Test
    fun `loadIcon returns and caches package icon`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val shadowPm = packageManager()
        shadowPm.installPackage(packageInfo("pkg.icon", "Icon", system = false))
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        shadowPm.setApplicationIcon("pkg.icon", BitmapDrawable(context.resources, bitmap))
        val provider = DefaultAppListProvider(context)

        val first = provider.loadIcon("pkg.icon")
        shadowPm.setApplicationIcon(
            "pkg.icon",
            BitmapDrawable(context.resources, Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888)),
        )
        val cached = provider.loadIcon("pkg.icon")

        assertNotNull(first)
        assertTrue(first === cached)
    }

    @Test
    fun `loadIcon returns null and remembers missing package`() = runTest {
        val provider = DefaultAppListProvider(RuntimeEnvironment.getApplication())

        assertNull(provider.loadIcon("pkg.missing"))
        assertNull(provider.loadIcon("pkg.missing"))
    }

    @Test
    fun `packageChanges invalidates cached app list and icon cache on broadcast`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val shadowPm = packageManager()
        shadowPm.installPackage(packageInfo("pkg.broadcast", "Broadcast App", system = false))
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        shadowPm.setApplicationIcon("pkg.broadcast", BitmapDrawable(context.resources, bitmap))
        val provider = DefaultAppListProvider(context)
        val collector = launch { provider.packageChanges.collect { } }

        try {
            val firstApps = provider.loadApps()
            val firstIcon = provider.loadIcon("pkg.broadcast")
            assertEquals(listOf("pkg.broadcast"), firstApps.map { it.packageName })
            assertNotNull(firstIcon)

            shadowPm.installPackage(packageInfo("pkg.added", "Added App", system = false))
            shadowPm.setApplicationIcon(
                "pkg.broadcast",
                BitmapDrawable(context.resources, Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888)),
            )
            context.sendBroadcast(
                Intent(Intent.ACTION_PACKAGE_CHANGED).apply {
                    data = android.net.Uri.parse("package:pkg.broadcast")
                },
            )
            shadowOf(Looper.getMainLooper()).idle()
            advanceUntilIdle()

            assertTrue(provider.loadApps().any { it.packageName == "pkg.added" })
            assertTrue(provider.loadIcon("pkg.broadcast") !== firstIcon)
        } finally {
            collector.cancel()
        }
    }

    @Test
    fun `holdsInternetPermission handles null empty and matching permissions`() {
        assertFalse(holdsInternetPermission(null))
        assertFalse(holdsInternetPermission(emptyArray()))
        assertFalse(holdsInternetPermission(arrayOf(Manifest.permission.ACCESS_NETWORK_STATE)))
        assertTrue(holdsInternetPermission(arrayOf(Manifest.permission.INTERNET)))
    }

    private fun packageInfo(
        packageName: String,
        label: String,
        system: Boolean,
        internet: Boolean = true,
    ) = PackageInfo().apply {
        this.packageName = packageName
        requestedPermissions = if (internet) arrayOf(Manifest.permission.INTERNET) else emptyArray()
        applicationInfo = ApplicationInfo().apply {
            this.packageName = packageName
            name = packageName
            nonLocalizedLabel = label
            flags = if (system) ApplicationInfo.FLAG_SYSTEM else 0
        }
    }

    private fun packageManager(): ShadowPackageManager =
        Shadow.extract(RuntimeEnvironment.getApplication().packageManager)
}
